package com.ilxomjon.dur_novvot_dokon.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * v3.2 avtomatik yangilash (Agent va Admin):
 *  * ilova ochiq — ish vaqtida har N daqiqada (Sozlamalar ▸ Avtomatik yangilash), tashqarida — 15 daqiqada;
 *  * ilova yopiq — WorkManager 15 daqiqada (Android ruxsat etgan eng kichik oraliq);
 *  * ish tugashidan 1 daqiqa oldin — budilnik: hammasi to'liq yuklab olinadi (keyin internetsiz ham ko'rinadi);
 *  * kunlik zaxira — kuniga bir marta, eskisini almashtiradi.
 * Admin'da agentlar ko'prigi (fon xizmati) o'z tezligida ishlaydi; kompyuterga ish vaqti bulut orqali yuboriladi.
 */
object AutoUpdate {
    private const val PERIODIC = "auto_update"
    private const val FINAL_NOW = "auto_final"
    private const val REQ_FINAL = 7301

    /** Sozlama o'zgarganda va ilova ochilganda: fon ishi va oxirgi yuklash budilnigi. */
    fun apply(context: Context) {
        val app = context.applicationContext
        val prefs = ServiceLocator.appPrefs
        runCatching {
            val wm = WorkManager.getInstance(app)
            if (!prefs.autoUpdate && !prefs.dailyBackup) wm.cancelUniqueWork(PERIODIC)
            else {
                val c = Constraints.Builder().setRequiresBatteryNotLow(true).build()
                wm.enqueueUniquePeriodicWork(
                    PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<AutoUpdateWorker>(15, TimeUnit.MINUTES).setConstraints(c).build(),
                )
            }
        }
        scheduleFinal(app)
    }

    /** Ish tugashidan 1 daqiqa oldingi budilnik. Aniq budilnikka ruxsat bo'lmasa — taxminiy (bir necha daqiqa farq). */
    fun scheduleFinal(context: Context) {
        val prefs = ServiceLocator.appPrefs
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            context, REQ_FINAL, Intent(context, FinalSyncReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val wh = prefs.workHours
        if (!prefs.autoUpdate || !wh.on) { am.cancel(pi); return }
        val at = wh.nextFinal(System.currentTimeMillis())
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun intervalMs(): Long {
        val prefs = ServiceLocator.appPrefs
        if (!prefs.autoUpdate) return Long.MAX_VALUE
        return prefs.workHours.interval(System.currentTimeMillis(), prefs.updateMinutes * 60_000L, 15 * 60_000L)
    }

    /** Bitta yangilash: katalog (narx, qarz, mijozlar), Admin'da — 2-ilova va kompyuter ishlari. Xato tashqariga chiqmaydi. */
    suspend fun refresh(full: Boolean) {
        val loc = ServiceLocator
        if (loc.catalog.state.value.agent != null) runCatching { loc.catalog.sync() }
        if (AppRole.isAdmin || loc.shareStore.isConfigured) runCatching { loc.share.tick() }
        if (full) {
            runCatching { loc.outbox.drainNow() }
            ServiceLocator.appPrefs.lastFinalSyncAt = System.currentTimeMillis()
        }
    }

    /** Kunlik zaxira (bugun olinmagan bo'lsa). */
    suspend fun dailyBackupIfDue() {
        val prefs = ServiceLocator.appPrefs
        if (!prefs.dailyBackup) return
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = prefs.lastDailyBackupAt
        val last = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        if (prefs.lastDailyBackupAt > 0 && last == today) return
        try {
            ServiceLocator.backup.exportDaily()
            prefs.lastDailyBackupAt = System.currentTimeMillis()
            prefs.lastDailyBackupError = ""
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            prefs.lastDailyBackupError = (e.message ?: e.javaClass.simpleName).take(200)
        }
    }

    fun kickFinal(context: Context) {
        runCatching {
            val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                FINAL_NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FinalSyncWorker>().setConstraints(net).build(),
            )
        }
    }
}

class AutoUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.ensureInit(applicationContext)
        AutoUpdate.dailyBackupIfDue()
        val prefs = ServiceLocator.appPrefs
        if (prefs.autoUpdate && prefs.workHours.isWorkTime(System.currentTimeMillis())) AutoUpdate.refresh(full = false)
        // Budilnik o'chib qolgan bo'lsa (telefon qayta yoqilgan) — qayta qo'yiladi
        AutoUpdate.scheduleFinal(applicationContext)
        return Result.success()
    }
}

class FinalSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.ensureInit(applicationContext)
        AutoUpdate.refresh(full = true)
        AutoUpdate.dailyBackupIfDue()
        AutoUpdate.scheduleFinal(applicationContext)
        return Result.success()
    }
}

/** Ish tugashidan 1 daqiqa oldin. */
class FinalSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ServiceLocator.ensureInit(context.applicationContext)
        AutoUpdate.kickFinal(context.applicationContext)
        AutoUpdate.scheduleFinal(context.applicationContext)
    }
}

/** Telefon qayta yoqilganda budilnik va fon ishi tiklanadi. */
class AutoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED && a != "android.intent.action.TIME_SET" &&
            a != Intent.ACTION_TIMEZONE_CHANGED) return
        ServiceLocator.ensureInit(context.applicationContext)
        AutoUpdate.apply(context.applicationContext)
    }
}
