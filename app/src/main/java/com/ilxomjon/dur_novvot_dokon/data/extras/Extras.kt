package com.ilxomjon.dur_novvot_dokon.data.extras

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.ui.MainActivity
import java.io.File
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import uz.imsoft.shared.B64

/** v2.4 "Qulayliklar": fon ishlari, bildirishnomalar, ekran o'lchami, PIN. Har biri Sozlamalarda o'chiriladi. */
object Extras {
    const val CHANNEL = "imsoft_main"
    private const val BACKUP_WORK = "auto_backup"
    private const val REMINDER_WORK = "dayend_reminder"
    private const val NOTIF_REMINDER = 4101
    private const val NOTIF_QUEUE = 4102

    fun onAppStart(context: Context) {
        val p = ServiceLocator.appPrefs
        scheduleBackup(context, p.fAutoBackup)
        scheduleReminder(context, p.reminderTime)
    }

    // ---------------- ekran o'lchami va shrift ----------------

    /** Activity konteksti: zichlik va shrift foydalanuvchi tanlagan ko'paytuvchi bilan. */
    fun wrap(base: Context, prefs: AppPrefs): Context {
        val scale = prefs.uiScale.coerceIn(0.75f, 1.3f)
        val font = prefs.fontScale.coerceIn(0.8f, 1.4f)
        if (scale == 1.0f && font == 1.0f) return base
        val cfg = Configuration(base.resources.configuration)
        if (scale != 1.0f) {
            val sysDpi = cfg.densityDpi.takeIf { it > 0 } ?: base.resources.displayMetrics.densityDpi
            val newDpi = (sysDpi * scale).toInt().coerceAtLeast(120)
            val k = sysDpi.toFloat() / newDpi
            cfg.densityDpi = newDpi
            // Ekran o'lchami dp'da ham mos o'zgaradi (dialog/bottom sheet kengligi to'g'ri hisoblansin)
            if (cfg.screenWidthDp > 0) cfg.screenWidthDp = (cfg.screenWidthDp * k).toInt()
            if (cfg.screenHeightDp > 0) cfg.screenHeightDp = (cfg.screenHeightDp * k).toInt()
            if (cfg.smallestScreenWidthDp > 0) cfg.smallestScreenWidthDp = (cfg.smallestScreenWidthDp * k).toInt()
        }
        cfg.fontScale = cfg.fontScale * font
        return base.createConfigurationContext(cfg)
    }

    // ---------------- bildirishnomalar ----------------

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_history)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, n) }
    }

    /** OutboxWorker chaqiradi: yuborilmagan buyurtma 30 daqiqadan ko'p tursa (soatda bir martadan ko'p emas). */
    fun checkQueueAlert(context: Context) {
        val p = ServiceLocator.appPrefs
        if (!p.fQueueAlert) return
        val oldest = runCatching { ServiceLocator.historyDb.oldestUnsentAt() }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        if (now - oldest < 30 * 60_000L || now - p.lastQueueAlertAt < 60 * 60_000L) return
        p.lastQueueAlertAt = now
        val n = ServiceLocator.outbox.counts.value.unsent
        notify(context, NOTIF_QUEUE, context.getString(R.string.notif_queue_title), context.getString(R.string.notif_queue_text, n, ((now - oldest) / 60_000L).toInt()))
    }

    // ---------------- avto-zaxira ----------------

    fun backupDir(context: Context) = File(context.filesDir, "auto_backups").apply { mkdirs() }

    fun backups(context: Context): List<File> =
        backupDir(context).listFiles { f -> f.name.endsWith(".zip") }?.sortedByDescending { it.name }.orEmpty()

    fun scheduleBackup(context: Context, on: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            if (on) wm.enqueueUniquePeriodicWork(BACKUP_WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).build())
            else wm.cancelUniqueWork(BACKUP_WORK)
        }
    }

    suspend fun backupNow(context: Context): File {
        val name = "dokon_auto_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".zip"
        val f = File(backupDir(context), name)
        ServiceLocator.backup.export(Uri.fromFile(f), null)
        backups(context).drop(7).forEach { it.delete() }
        return f
    }

    // ---------------- kun yakuni eslatmasi ----------------

    fun scheduleReminder(context: Context, time: String, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            val hm = parseTime(time)
            if (hm == null) { wm.cancelUniqueWork(REMINDER_WORK); return }
            val now = Calendar.getInstance()
            val at = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hm.first); set(Calendar.MINUTE, hm.second); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            wm.enqueueUniqueWork(
                REMINDER_WORK, policy,
                OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(at.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS).build(),
            )
        }
    }

    fun parseTime(t: String): Pair<Int, Int>? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(t.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h to mi else null
    }

    suspend fun remind(context: Context) {
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val orders = runCatching { ServiceLocator.history.orders().filter { it.sentAt >= start } }.getOrDefault(emptyList())
        val payments = runCatching { ServiceLocator.history.payments().filter { it.sentAt >= start } }.getOrDefault(emptyList())
        val unsent = ServiceLocator.outbox.counts.value.unsent
        val total = orders.filter { it.mode.code == "1" && it.currency.code == "0" }.sumOf { it.total - it.discount }
        val text = context.getString(R.string.notif_dayend_text, orders.size, Money.display(total), payments.size, unsent)
        notify(context, NOTIF_REMINDER, context.getString(R.string.notif_dayend_title), text)
    }

    // ---------------- PIN ----------------

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(pin.toCharArray(), salt, 20_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    fun setPin(prefs: AppPrefs, pin: String?) {
        if (pin.isNullOrEmpty()) { prefs.pinHash = ""; prefs.pinSalt = ""; return }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.pinSalt = B64.encode(salt)
        prefs.pinHash = B64.encode(pbkdf2(pin, salt))
    }

    fun checkPin(prefs: AppPrefs, pin: String): Boolean {
        if (prefs.pinHash.isEmpty()) return true
        val salt = runCatching { B64.decode(prefs.pinSalt) }.getOrNull() ?: return false
        val a = pbkdf2(pin, salt)
        val b = runCatching { B64.decode(prefs.pinHash) }.getOrNull() ?: return false
        return java.security.MessageDigest.isEqual(a, b)
    }
}

class AutoBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.ensureInit(applicationContext)
        if (!ServiceLocator.appPrefs.fAutoBackup) return Result.success()
        return try {
            Extras.backupNow(applicationContext)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}

class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.ensureInit(applicationContext)
        val time = ServiceLocator.appPrefs.reminderTime
        if (Extras.parseTime(time) != null) {
            runCatching { Extras.remind(applicationContext) }
            // Ertangi kun. Ishlab turgan o'zimizni bekor qilmaslik uchun APPEND_OR_REPLACE.
            Extras.scheduleReminder(applicationContext, time, androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
