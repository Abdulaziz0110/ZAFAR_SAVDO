package uz.imsoft.mijoz.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uz.imsoft.mijoz.Locator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * v3.2 Mijoz: ish vaqti bo'yicha avtomatik yangilash (ochiq turganda har N daqiqada), ish tugashidan 1 daqiqa oldin
 * oxirgi to'liq yuklab olish va kunlik zaxira (1C tarixi nusxasi + Kassa bazasi; ulanish kalitlari KIRMAYDI).
 */
object MijozAuto {
    private const val REQ_FINAL = 7401
    private const val FINAL = "mijoz_final"
    private const val DAILY = "mijoz_kunlik_zaxira.zip"
    private val FILES = listOf("snapshot.blob")

    fun apply(ctx: Context) {
        val app = ctx.applicationContext
        val p = Locator.prefs(app)
        RefreshWorker.schedule(app, p.autoMinutes)
        scheduleFinal(app)
    }

    fun scheduleFinal(ctx: Context) {
        val p = Locator.prefs(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(ctx, REQ_FINAL, Intent(ctx, MijozFinalReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val wh = p.workHours
        if (!p.autoRefresh || !wh.on || p.link == null) { am.cancel(pi); return }
        val at = wh.nextFinal(System.currentTimeMillis())
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Ochiq ilovada yangilash oralig'i (ms). */
    fun liveIntervalMs(): Long {
        val p = Locator.prefs
        if (!p.autoRefresh) return Long.MAX_VALUE
        return p.workHours.interval(System.currentTimeMillis(), p.liveMinutes * 60_000L, 15 * 60_000L)
    }

    fun kickFinal(ctx: Context) {
        runCatching {
            val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(FINAL, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<MijozFinalWorker>().setConstraints(net).build())
        }
    }

    fun dailyFile(ctx: Context): File = File(ctx.getExternalFilesDir("zaxira") ?: File(ctx.filesDir, "zaxira").apply { mkdirs() }, DAILY)

    suspend fun backupIfDue(ctx: Context, force: Boolean = false) {
        val p = Locator.prefs(ctx)
        if (!p.dailyBackup && !force) return
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = p.lastBackupAt
        if (!force && p.lastBackupAt > 0 && cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR) == today) return
        try {
            withContext(Dispatchers.IO) {
                Locator.pos.checkpoint()
                val f = dailyFile(ctx)
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".tmp")
                ZipOutputStream(FileOutputStream(tmp)).use { z ->
                    for (n in FILES) File(ctx.filesDir, n).takeIf { it.isFile }?.let { add(z, n, it) }
                    ctx.getDatabasePath(PosDb.NAME).takeIf { it.isFile }?.let { add(z, "db/" + PosDb.NAME, it) }
                }
                if (!tmp.renameTo(f)) { f.delete(); check(tmp.renameTo(f)) { "rename" } }
            }
            p.lastBackupAt = System.currentTimeMillis()
            p.lastBackupError = ""
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            p.lastBackupError = (e.message ?: e.javaClass.simpleName).take(200)
        }
    }

    private fun add(z: ZipOutputStream, name: String, f: File) {
        z.putNextEntry(ZipEntry(name))
        FileInputStream(f).use { it.copyTo(z) }
        z.closeEntry()
    }

    /** Kunlik zaxiradan tiklash. Faqat ma'lum fayl nomlari (yo'l bilan hujum bo'lmasin). @return tiklangan fayllar soni. */
    suspend fun restore(ctx: Context): Int = withContext(Dispatchers.IO) {
        val f = dailyFile(ctx)
        require(f.isFile) { "no_backup" }
        var n = 0
        Locator.pos.close()
        ZipInputStream(FileInputStream(f)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val target = when (e.name) {
                    in FILES -> File(ctx.filesDir, e.name)
                    "db/" + PosDb.NAME -> ctx.getDatabasePath(PosDb.NAME)
                    else -> null
                }
                if (target != null && !e.isDirectory) {
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, target.name + ".restore")
                    FileOutputStream(tmp).use { z.copyTo(it) }
                    if (target.name == PosDb.NAME) {
                        File(target.path + "-wal").delete()
                        File(target.path + "-shm").delete()
                    }
                    if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
                    n++
                }
                z.closeEntry()
            }
        }
        Locator.pos.load()
        Locator.data.loadLocal()
        n
    }
}

class MijozFinalWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Locator.prefs(applicationContext)
        runCatching { Locator.data.loadLocal() }
        runCatching { Locator.data.refresh(askFresh = true) }
        delay(45_000L)
        runCatching { Locator.data.refresh(askFresh = false) }
        Locator.prefs.lastFinalAt = System.currentTimeMillis()
        MijozAuto.backupIfDue(applicationContext)
        MijozAuto.scheduleFinal(applicationContext)
        return Result.success()
    }
}

class MijozFinalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Locator.prefs(context)
        MijozAuto.kickFinal(context.applicationContext)
        MijozAuto.scheduleFinal(context.applicationContext)
    }
}

class MijozBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED && a != "android.intent.action.TIME_SET" && a != Intent.ACTION_TIMEZONE_CHANGED) return
        Locator.prefs(context)
        MijozAuto.apply(context.applicationContext)
    }
}
