package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * Fon ishi (internet bo'lsa, batareya kam bo'lmasa): 2-ilova so'roviga javob, yangi ma'lumotni yuborish, buyurtmalarni tekshirish.
 * Oralig'i Sozlamalar ▸ Mijoz ilovasi ▸ "Avtomatik yangilash" (15 daq / 30 daq / 1 soat / 2 soat / o'chiq). Har ishga tushishda
 * bir nechta kichik so'rov — batareyaga sezilarli ta'sir qilmaydi.
 */
class ShareWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.ensureInit(applicationContext)
        return try {
            ServiceLocator.share.tick()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "share_periodic"
        private const val NOW = "share_now"

        /** Davriy ish. [minutes] = 0 — o'chiriladi (ilova ochilganda va import qilinganda baribir yuboriladi). */
        fun schedule(context: Context, minutes: Int = ServiceLocator.shareStore.syncMinutes) {
            runCatching {
                val wm = WorkManager.getInstance(context)
                if (minutes <= 0) { wm.cancelUniqueWork(PERIODIC); return }
                val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
                wm.enqueueUniquePeriodicWork(
                    PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<ShareWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES).setConstraints(c).build(),
                )
            }
        }

        /** Bir martalik: import, katalog yangilanishi yoki ilova ochilganda. Internet bo'lmasa — paydo bo'lganda. */
        fun kick(context: Context, delaySeconds: Long = 3) {
            runCatching {
                val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    NOW, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ShareWorker>().setConstraints(net).setInitialDelay(delaySeconds, TimeUnit.SECONDS).build(),
                )
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(PERIODIC) }
        }
    }
}
