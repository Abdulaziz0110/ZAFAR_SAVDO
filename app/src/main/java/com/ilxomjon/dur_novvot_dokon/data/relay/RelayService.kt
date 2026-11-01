package com.ilxomjon.dur_novvot_dokon.data.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Admin: agentlar ko'prigi fonda (bildirishnoma bilan). Agent yangi narsa yozsa (g/{kalit}/sig) Firebase oqimi orqali
 * darhol bilib oladi; har 60 soniyada ham tekshiradi. Faqat Admin ilovada e'lon qilingan (admin flavor manifesti).
 */
class RelayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var lock: PowerManager.WakeLock? = null
    private var lastText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.ensureInit(applicationContext)
        startAsForeground(getString(R.string.rl_notif_starting))
        lock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "imsoft:relay").apply { setReferenceCounted(false); acquire() }
        scope.launch { mainLoop() }
        scope.launch { streamLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AppRole.isAdmin || !ServiceLocator.shareStore.relayOn) {
            stopSelf()
            return START_NOT_STICKY
        }
        wake.trySend(Unit)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { lock?.release() }
        super.onDestroy()
    }

    private suspend fun mainLoop() {
        val host = ServiceLocator.relayHost
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        var tickAt = 0L
        while (scope.isActive) {
            if (!ServiceLocator.shareStore.relayOn) { stopSelf(); return }
            val st = ServiceLocator.shareStore
            if (st.isCloudReady && st.agentKey.isEmpty()) st.agentKey = uz.imsoft.shared.CloudDb.newKey()
            runCatching { host.pump(version = version) }
            // v3.1: kompyuterdan kelganlar, 2-ilova so'rovlari (qarzni yangilash) va yuborish — har daqiqada
            if (System.currentTimeMillis() - tickAt >= 55_000L) {
                tickAt = System.currentTimeMillis()
                runCatching { ServiceLocator.share.tick() }
            }
            val s = host.stats
            val time = if (s.at > 0) SimpleDateFormat("HH:mm", Locale.US).format(Date(s.at)) else "—"
            update(if (s.error.isNotEmpty()) getString(R.string.rl_notif_error, time) else getString(R.string.rl_notif_ok, time, s.written))
            withTimeoutOrNull(60_000L) { wake.receive() }
        }
    }

    /** Firebase oqimi (SSE): agent signal yozsa — darhol ishlash. */
    private suspend fun streamLoop() {
        var backoff = 2_000L
        while (scope.isActive) {
            val st = ServiceLocator.shareStore
            val key = st.agentKey
            if (!st.isCloudReady || key.isEmpty()) { delay(30_000L); continue }
            try {
                val db = uz.imsoft.shared.CloudDb(st.dbUrl, st.secret)
                val c = URL(db.url("g/$key/sig")).openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "text/event-stream")
                c.connectTimeout = 15_000
                c.readTimeout = 90_000
                try {
                    if (c.responseCode != 200) throw java.io.IOException("SSE HTTP ${c.responseCode}")
                    backoff = 2_000L
                    BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                        while (scope.isActive) {
                            val line = r.readLine() ?: break
                            if (line.startsWith("event:")) {
                                val ev = line.substringAfter(':').trim()
                                if (ev == "put" || ev == "patch") wake.trySend(Unit)
                                if (ev == "cancel" || ev == "auth_revoked") break
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // internet yo'q — keyinroq; asosiy sikl baribir har 60 s ishlaydi
            }
            delay(backoff)
            backoff = minOf(backoff * 2, 60_000L)
        }
    }

    private fun startAsForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(text), type)
        lastText = text
    }

    private fun update(text: String) {
        if (text == lastText) return
        lastText = text
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text)) }
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.rl_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(getString(R.string.rl_title)).setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open).build()
    }

    companion object {
        private const val CHANNEL = "relay"
        private const val NOTIF_ID = 7301
        private const val WORK = "relay_periodic"

        /** Ko'prikni yoqish (faqat Admin, ilova ochiq paytida — fon cheklovlari). */
        fun start(ctx: Context) {
            if (!AppRole.isAdmin) return
            ServiceLocator.shareStore.relayOn = true
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, RelayService::class.java)) }
            schedule(ctx)
        }

        fun stop(ctx: Context) {
            ServiceLocator.shareStore.relayOn = false
            ctx.stopService(Intent(ctx, RelayService::class.java))
            runCatching { WorkManager.getInstance(ctx).cancelUniqueWork(WORK) }
        }

        /** Ilova ochilganda / telefon yonganda: yoqilgan (standart) va Firebase sozlangan bo'lsa ishlab tursin. */
        fun ensureRunning(ctx: Context) {
            val st = ServiceLocator.shareStore
            if (AppRole.isAdmin && st.relayOn && st.isCloudReady) start(ctx)
        }

        /** Zaxira: xizmat to'xtatilgan bo'lsa ham 15 daqiqada bir navbat ishlanadi. */
        fun schedule(ctx: Context) {
            runCatching {
                val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    WORK, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<RelayWorker>(15, TimeUnit.MINUTES).setConstraints(net).build(),
                )
            }
        }
    }
}

/** Fon zaxirasi: 15 daqiqada bir [RelayHost.pump]. */
class RelayWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.ensureInit(applicationContext)
        if (!AppRole.isAdmin || !ServiceLocator.shareStore.relayOn) return Result.success()
        runCatching { ServiceLocator.relayHost.pump() }
        return Result.success()
    }
}

/** Telefon qayta yoqilganda ko'prikni tiklash (Admin). */
class RelayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ServiceLocator.ensureInit(context.applicationContext)
        RelayService.ensureRunning(context.applicationContext)
    }
}
