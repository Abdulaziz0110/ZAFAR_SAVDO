package com.ilxomjon.dur_novvot_dokon.data.repo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.DbSafety
import com.ilxomjon.dur_novvot_dokon.data.SecureStore
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.local.LocalDb
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import com.ilxomjon.dur_novvot_dokon.domain.outbox.CheckReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Zaxira faylidagi ma'lumot (tiklashdan OLDIN ko'rsatiladi). */
data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val historyVersion: Int,
    val products: Int,
    val stores: Int,
    val orders: Int,
    val payments: Int,
    /** Hali serverga yozilmagan navbat qatorlari: tiklangandan keyin AVTOMATIK YUBORILMAYDI (tekshirish kerak). */
    val unsent: Int,
    val encrypted: Boolean,
    /** Zaxira bu ilovadan yangiroq versiyada qilingan — tiklab bo'lmaydi. */
    val tooNew: Boolean,
)

class BackupException(val kind: Kind, message: String? = null, cause: Throwable? = null) : Exception(message ?: kind.name, cause) {
    enum class Kind { NOT_BACKUP, WRONG_PASSWORD, TOO_NEW, CORRUPT, DISK_FULL, IO }
}

/**
 * Ma'lumotni zaxiralash/tiklash.
 *  - Zaxiraga SQL Server PAROLI kiritilmaydi.
 *  - Ixtiyoriy parol bilan shifrlash (AES-256-GCM, PBKDF2). Parol hech qayerda saqlanmaydi — unutilsa, ochib bo'lmaydi.
 *  - Tiklashdan oldin: ko'rib chiqish (preview), versiya va yaxlitlik tekshiruvi, joriy bazalar nusxasi (xato bo'lsa qaytariladi).
 *  - Tiklangan navbatdagi yuborilmagan qatorlar "Tekshirish kerak" holatiga o'tadi va o'zi yuborilmaydi (takroriy buyurtma xavfi).
 */
private const val BARCODES = "barcodes.json"

class BackupRepository(
    private val context: Context,
    private val localDb: LocalDb,
    private val historyDb: HistoryDb,
    private val secure: SecureStore,
    private val prefs: AppPrefs,
    private val catalog: CatalogRepository,
    private val draft: DraftRepository,
    private val outbox: OutboxRepository,
) {
    private val userPrefs get() = context.getSharedPreferences("user", Context.MODE_PRIVATE)
    private val draftKeys = listOf("tan_dokon_id", "tan_region_id", "tan_dokon_nomi", "savdo_turi", "valyuta_turi", "vazvrat")

    suspend fun export(target: Uri, password: CharArray?) {
        draft.flush()
        outbox.paused {
            withContext(Dispatchers.IO) {
                try {
                    localDb.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    historyDb.checkpoint()
                    val zip = buildZip()
                    val bytes = if (password != null && password.isNotEmpty()) Crypto.encrypt(zip, password) else zip
                    val out = context.contentResolver.openOutputStream(target) ?: throw BackupException(BackupException.Kind.IO, "Fayl ochilmadi")
                    out.use { it.write(bytes); it.flush() }
                } catch (e: BackupException) {
                    throw e
                } catch (e: IOException) {
                    throw BackupException(if (isDiskFull(e)) BackupException.Kind.DISK_FULL else BackupException.Kind.IO, e.message, e)
                }
            }
        }
    }

    private fun buildZip(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            addFile(zip, LocalDb.NAME, context.getDatabasePath(LocalDb.NAME))
            addFile(zip, HistoryDb.NAME, context.getDatabasePath(HistoryDb.NAME))
            // v2.6: telefonda yozilgan shtrix kodlar (bo'lsa) — telefon almashsa ham yo'qolmasin
            File(context.filesDir, BARCODES).takeIf { it.exists() }?.let { addFile(zip, BARCODES, it) }
            val cfg = secure.serverConfig
            val json = JSONObject()
                .put("host", cfg.host).put("db", cfg.database).put("login", cfg.login).put("remote", cfg.remoteHost)
                .put("language", prefs.language.code).put("theme", prefs.theme.code)
            val draftJson = JSONObject()
            for (k in draftKeys) draftJson.put(k, userPrefs.getString(k, ""))
            json.put("draft", draftJson)
            putText(zip, "settings.json", json.toString())
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            putText(zip, "manifest.json", JSONObject().put("format", 2).put("created_at", System.currentTimeMillis()).put("app_version", version)
                .put("history_db_version", HistoryDb.VERSION).put("local_db_version", LocalDb.VERSION).toString())
        }
        return bos.toByteArray()
    }

    /** Zaxiradagi shtrix kodlar (eskiroq zaxirada bo'lmaydi — unda hech narsa o'zgarmaydi). */
    private fun restoreBarcodes(src: File) {
        runCatching {
            val dst = File(context.filesDir, BARCODES)
            val tmp = File(context.filesDir, "$BARCODES.tmp")
            src.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst) }
            com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.appScope.launch { com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.barcodes.reloadFromDisk() }
        }
    }

    private fun putText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Faylni o'qib, nima tiklanishini ko'rsatadi. Hech narsa o'zgartirmaydi. */
    suspend fun preview(source: Uri, password: CharArray?): BackupPreview = withContext(Dispatchers.IO) {
        val (dir, encrypted) = unpack(source, password)
        try {
            val manifest = File(dir, "manifest.json").takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            val hist = File(dir, HistoryDb.NAME)
            val hv = if (hist.exists()) DbSafety.userVersion(hist) else 0
            val main = File(dir, LocalDb.NAME)
            BackupPreview(
                createdAt = manifest?.optLong("created_at") ?: 0L,
                appVersion = manifest?.optString("app_version").orEmpty(),
                historyVersion = hv,
                products = count(main, "SELECT COUNT(*) FROM tovar"),
                stores = count(main, "SELECT COUNT(*) FROM dokon"),
                orders = count(hist, "SELECT COUNT(*) FROM sent_order"),
                payments = count(hist, "SELECT COUNT(*) FROM sent_payment"),
                unsent = if (hv >= 3) count(hist, "SELECT COUNT(*) FROM outbox WHERE state NOT IN ('SENT','SENT_MANUAL')") else count(hist, "SELECT COUNT(*) FROM outbox"),
                encrypted = encrypted,
                tooNew = hv > HistoryDb.VERSION,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    /** @return tekshirish kerak bo'lgan (avtomatik yuborilmaydigan) qatorlar soni */
    suspend fun import(source: Uri, password: CharArray?): Int {
        draft.flush()
        val needsCheck = outbox.paused {
            withContext(Dispatchers.IO) {
                val (dir, _) = unpack(source, password)
                try {
                    val main = File(dir, LocalDb.NAME)
                    val hist = File(dir, HistoryDb.NAME).takeIf { it.exists() }
                    if (!DbSafety.integrityOk(main) || (hist != null && !DbSafety.integrityOk(hist))) throw BackupException(BackupException.Kind.CORRUPT)
                    if (hist != null && DbSafety.userVersion(hist) > HistoryDb.VERSION) throw BackupException(BackupException.Kind.TOO_NEW)

                    // Joriy holat nusxasi: biror qadam xato bo'lsa, shu qaytariladi.
                    val mainPath = context.getDatabasePath(LocalDb.NAME)
                    val histPath = context.getDatabasePath(HistoryDb.NAME)
                    localDb.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    historyDb.checkpoint()
                    val keepMain = DbSafety.copyDb(context, mainPath, "${LocalDb.NAME}.pre-restore")
                    val keepHist = DbSafety.copyDb(context, histPath, "${HistoryDb.NAME}.pre-restore")
                    localDb.close(); historyDb.close()
                    try {
                        DbSafety.restoreCopy(main, mainPath)
                        if (hist != null) DbSafety.restoreCopy(hist, histPath)
                        // Ochilishi va yangi versiyaga o'tishi shu yerda tekshiriladi.
                        localDb.readableDatabase
                        val n = historyDb.markAllUnfinishedNeedsCheck(CheckReason.RESTORED)
                        File(dir, "settings.json").takeIf { it.exists() }?.let { applySettings(JSONObject(it.readText())) }
                        File(dir, BARCODES).takeIf { it.exists() }?.let { restoreBarcodes(it) }
                        n
                    } catch (t: Throwable) {
                        runCatching { localDb.close(); historyDb.close() }
                        keepMain?.let { DbSafety.restoreCopy(it, mainPath) }
                        keepHist?.let { DbSafety.restoreCopy(it, histPath) }
                        throw if (t is IOException && isDiskFull(t)) BackupException(BackupException.Kind.DISK_FULL, t.message, t) else t
                    }
                } finally {
                    dir.deleteRecursively()
                }
            }
        }
        catalog.load()
        draft.load()
        outbox.refreshCount()
        return needsCheck
    }

    private fun unpack(source: Uri, password: CharArray?): Pair<File, Boolean> {
        val raw = try {
            context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: throw BackupException(BackupException.Kind.IO, "Fayl ochilmadi")
        } catch (e: IOException) {
            throw BackupException(BackupException.Kind.IO, e.message, e)
        }
        val encrypted = Crypto.isEncrypted(raw)
        val zipBytes = if (encrypted) {
            if (password == null || password.isEmpty()) throw BackupException(BackupException.Kind.WRONG_PASSWORD)
            Crypto.decrypt(raw, password)
        } else raw
        val tmp = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        tmp.deleteRecursively(); tmp.mkdirs()
        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    if (name == LocalDb.NAME || name == HistoryDb.NAME || name == "settings.json" || name == "manifest.json" || name == BARCODES) {
                        File(tmp, name).outputStream().use { zip.copyTo(it) }
                    }
                    e = zip.nextEntry
                }
            }
        } catch (e: IOException) {
            tmp.deleteRecursively()
            throw BackupException(if (isDiskFull(e)) BackupException.Kind.DISK_FULL else BackupException.Kind.NOT_BACKUP, e.message, e)
        }
        if (!File(tmp, LocalDb.NAME).exists()) { tmp.deleteRecursively(); throw BackupException(BackupException.Kind.NOT_BACKUP) }
        return tmp to encrypted
    }

    private fun count(file: File, sql: String): Int {
        if (!file.exists()) return 0
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db -> db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 } }
        }.getOrDefault(0)
    }

    private fun applySettings(json: JSONObject) {
        val cur = secure.serverConfig
        secure.serverConfig = ServerConfig(
            host = json.optString("host", cur.host), database = json.optString("db", cur.database),
            login = json.optString("login", cur.login), password = secure.storedPassword(), remoteHost = json.optString("remote", cur.remoteHost),
        )
        val d = json.optJSONObject("draft")
        if (d != null) {
            val e = userPrefs.edit()
            for (k in draftKeys) e.putString(k, d.optString(k, ""))
            e.apply()
        }
    }

    private fun isDiskFull(e: IOException) = e.message.orEmpty().contains("ENOSPC") || e.message.orEmpty().contains("No space", true)

    /** Zaxira faylini parol bilan shifrlash. Format: "IMSBK1" + salt(16) + iv(12) + AES-256-GCM. */
    object Crypto {
        private val MAGIC = "IMSBK1".toByteArray(Charsets.US_ASCII)
        private const val ITER = 120_000

        fun isEncrypted(b: ByteArray) = b.size > MAGIC.size + 28 && b.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

        private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(password, salt, ITER, 256)
            val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            return SecretKeySpec(k, "AES")
        }

        fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
            val rnd = SecureRandom()
            val salt = ByteArray(16).also { rnd.nextBytes(it) }
            val iv = ByteArray(12).also { rnd.nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            return MAGIC + salt + iv + c.doFinal(plain)
        }

        fun decrypt(data: ByteArray, password: CharArray): ByteArray {
            val p = MAGIC.size
            val salt = data.copyOfRange(p, p + 16)
            val iv = data.copyOfRange(p + 16, p + 28)
            return try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
                c.doFinal(data, p + 28, data.size - p - 28)
            } catch (e: Exception) {
                throw BackupException(BackupException.Kind.WRONG_PASSWORD, cause = e)
            }
        }
    }
}
