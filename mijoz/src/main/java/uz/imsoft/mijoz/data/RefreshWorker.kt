package uz.imsoft.mijoz.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.shared.report.Stamp
import java.util.concurrent.TimeUnit

/**
 * Fonda avtomatik yangilash (har 1 yoki 2 soatda; internet bor va batareya kam bo'lmaganda). Bitta kichik so'rov
 * (sarlavha) — ma'lumot o'zgarmagan bo'lsa katta fayl yuklanmaydi, shuning uchun batareya va trafik deyarli sarflanmaydi.
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Locator.prefs(applicationContext)
        return try {
            Locator.data.loadLocal()
            val r = Locator.data.refresh(askFresh = false)
            if (r == RefreshResult.UPDATED && Locator.prefs.notifyNew) notifyNew(applicationContext)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        private const val NAME = "auto_refresh"
        private const val CHANNEL = "mijoz_updates"

        fun schedule(ctx: Context, minutes: Int) {
            runCatching {
                val wm = WorkManager.getInstance(ctx)
                if (minutes <= 0 || Locator.prefs(ctx).link == null) { wm.cancelUniqueWork(NAME); return }
                val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
                wm.enqueueUniquePeriodicWork(
                    NAME, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<RefreshWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES).setConstraints(c).build(),
                )
            }
        }

        private fun notifyNew(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_updates), NotificationManager.IMPORTANCE_DEFAULT))
            }
            val snap = Locator.data.state.value.snapshot
            val text = if (snap != null && snap.coveredTo > 0) ctx.getString(R.string.notif_new_text, Stamp.formatDate(snap.coveredTo)) else ctx.getString(R.string.home_updated)
            val pi = PendingIntent.getActivity(
                ctx, 7, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_sync).setContentTitle(ctx.getString(R.string.notif_new_title))
                .setContentText(text).setContentIntent(pi).setAutoCancel(true).build()
            runCatching { nm.notify(7001, n) }
        }
    }
}
