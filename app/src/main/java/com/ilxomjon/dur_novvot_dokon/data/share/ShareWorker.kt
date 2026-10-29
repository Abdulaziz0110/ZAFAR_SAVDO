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

/** Har 15 daqiqada (internet bo'lsa): 2-ilova so'roviga javob va yangi ma'lumotni yuborish. */
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

        fun schedule(context: Context) {
            runCatching {
                val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<ShareWorker>(15, TimeUnit.MINUTES).setConstraints(net).build(),
                )
            }
        }

        /** Bir martalik (masalan katalog yangilangandan keyin). */
        fun kick(context: Context) {
            runCatching {
                val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    NOW, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ShareWorker>().setConstraints(net).setInitialDelay(5, TimeUnit.SECONDS).build(),
                )
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(PERIODIC) }
        }
    }
}
