package com.ilxomjon.dur_novvot_dokon.data.repo

import android.content.Context
import android.net.Uri
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.SecureStore
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.local.LocalDb
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Ma'lumotni zaxiralash/tiklash (yangi imzo bilan o'rnatishda ma'lumot yo'qolmasligi uchun).
 * Zaxiraga SQL Server PAROLI kiritilmaydi — tiklagandan keyin qayta kiritiladi.
 */
class BackupRepository(
    private val context: Context,
    private val localDb: LocalDb,
    private val historyDb: HistoryDb,
    private val secure: SecureStore,
    private val prefs: AppPrefs,
    private val catalog: CatalogRepository,
    private val draft: DraftRepository,
) {
    private val userPrefs get() = context.getSharedPreferences("user", Context.MODE_PRIVATE)
    private val draftKeys = listOf("tan_dokon_id", "tan_region_id", "tan_dokon_nomi", "savdo_turi", "valyuta_turi", "vazvrat")

    suspend fun export(target: Uri) {
        draft.flush()
        exportNow(target)
    }

    private suspend fun exportNow(target: Uri) = withContext(Dispatchers.IO) {
        localDb.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        val out = context.contentResolver.openOutputStream(target) ?: throw IllegalStateException("Fayl ochilmadi")
        ZipOutputStream(out.buffered()).use { zip ->
            addFile(zip, LocalDb.NAME, context.getDatabasePath(LocalDb.NAME))
            addFile(zip, HistoryDb.NAME, context.getDatabasePath(HistoryDb.NAME))
            val cfg = secure.serverConfig
            val json = JSONObject()
                .put("host", cfg.host).put("db", cfg.database).put("login", cfg.login).put("remote", cfg.remoteHost)
                .put("language", prefs.language.code).put("theme", prefs.theme.code)
            val draftJson = JSONObject()
            for (k in draftKeys) draftJson.put(k, userPrefs.getString(k, ""))
            json.put("draft", draftJson)
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    suspend fun import(source: Uri) {
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(source) ?: throw IllegalStateException("Fayl ochilmadi")
            val tmp = File(context.cacheDir, "import")
            tmp.deleteRecursively(); tmp.mkdirs()
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    if (name == LocalDb.NAME || name == HistoryDb.NAME || name == "settings.json") {
                        File(tmp, name).outputStream().use { zip.copyTo(it) }
                    }
                    e = zip.nextEntry
                }
            }
            val main = File(tmp, LocalDb.NAME)
            if (!main.exists()) throw IllegalStateException("Bu zaxira fayli emas")
            localDb.close(); historyDb.close()
            replaceDb(main, context.getDatabasePath(LocalDb.NAME))
            File(tmp, HistoryDb.NAME).takeIf { it.exists() }?.let { replaceDb(it, context.getDatabasePath(HistoryDb.NAME)) }
            File(tmp, "settings.json").takeIf { it.exists() }?.let { applySettings(JSONObject(it.readText())) }
            tmp.deleteRecursively()
        }
        catalog.load()
        draft.load()
    }

    private fun applySettings(json: JSONObject) {
        val cur = secure.serverConfig
        secure.serverConfig = ServerConfig(
            host = json.optString("host", cur.host), database = json.optString("db", cur.database),
            login = json.optString("login", cur.login), password = cur.password, remoteHost = json.optString("remote", cur.remoteHost),
        )
        val d = json.optJSONObject("draft")
        if (d != null) {
            val e = userPrefs.edit()
            for (k in draftKeys) e.putString(k, d.optString(k, ""))
            e.apply()
        }
    }

    private fun replaceDb(from: File, to: File) {
        File(to.path + "-journal").delete(); File(to.path + "-wal").delete(); File(to.path + "-shm").delete()
        to.parentFile?.mkdirs()
        from.copyTo(to, overwrite = true)
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
