package uz.imsoft.mijoz.printer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.shared.B64
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 2-filial chek printeri: kompyuterdan (1C -> forwarder.py) Firebase navbatiga tushgan cheklarni USB printerga
 * eskisidan yangisiga, birma-bir chiqaradi.
 *
 * Dublikatdan himoya: chek printerga yuborilishidan OLDIN uning kaliti "inflight" sifatida diskka yoziladi;
 * muvaffaqiyatdan keyin "printed" ro'yxatiga qo'shilib, bulutdan o'chiriladi. Jarayon o'rtada o'lsa —
 * chek o'zi QAYTA chiqarilmaydi, ekranda "noma'lum" deb ko'rsatiladi va foydalanuvchi o'zi tanlaydi.
 * Yangi chek kelganini Firebase "stream" (SSE) darhol bildiradi; zaxira sifatida har 60 soniyada tekshiriladi.
 */
class PrintService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var askedPermission = false
    private var lastNotified = ""

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == UsbManager.ACTION_USB_DEVICE_DETACHED) askedPermission = false
            if (i.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) askedPermission = false
            PrinterHub.nudge()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground(getString(R.string.pr_notif_waiting))
        val f = IntentFilter().apply {
            addAction(UsbPrinter.ACTION_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "imsoft:printer").apply { setReferenceCounted(false); acquire() }
        PrinterHub.update { it.copy(running = true, printedTotal = Locator.prefs.printedTotal, lastPrintAt = Locator.prefs.lastPrintAt) }
        PrinterHub.log(getString(R.string.pr_log_started))
        scope.launch { mainLoop() }
        scope.launch { streamLoop() }
        scope.launch { while (isActive) { delay(5 * 60_000L); writeStatus() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Locator.prefs.printerOn = false
            stopSelf()
            return START_NOT_STICKY
        }
        PrinterHub.nudge()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(usbReceiver) }
        runCatching { wakeLock?.release() }
        PrinterHub.update { it.copy(running = false) }
        PrinterHub.log(getString(R.string.pr_log_stopped))
        // Holatni oxirgi marta yozish (tarmoq bo'lsa)
        Thread { runCatching { writeStatusBlocking(stopped = true) } }.start()
        super.onDestroy()
    }

    // ---------------- asosiy sikl ----------------

    private suspend fun mainLoop() {
        while (scope.isActive) {
            while (true) {
                val cmd = PrinterHub.commands.tryReceive().getOrNull() ?: break
                runCatching { handle(cmd) }.onFailure { PrinterHub.log("! " + (it.message ?: it.javaClass.simpleName)) }
            }
            runCatching { drain() }.onFailure { e ->
                PrinterHub.update { it.copy(online = false, lastError = e.message ?: e.javaClass.simpleName) }
            }
            withTimeoutOrNull(60_000L) { PrinterHub.wake.receive() }
        }
    }

    private fun db(): Pair<CloudDb, String>? {
        val link = Locator.prefs.link ?: return null
        if (link.queueKey.isEmpty()) return null
        return CloudDb(link.dbUrl) to link.queueKey
    }

    private fun drain() {
        // v5.3: printer holati ulanish kodi bo'lmasa ham ko'rinadi (avval "topilmadi" deb turar edi)
        val ready = UsbPrinter.ready(this)
        PrinterHub.update { it.copy(ready = ready) }
        if (ready == UsbPrinter.Ready.NO_PERMISSION && !askedPermission) {
            askedPermission = true
            UsbPrinter.requestPermission(this)
        }
        val (db, qk) = db() ?: run { PrinterHub.update { it.copy(lastError = getString(R.string.pr_err_no_link)) }; return }
        val prefs = Locator.prefs
        val keys = db.keys("q/$qk")
        val unknown = prefs.inflight
        if (unknown.isNotEmpty() && unknown !in keys) prefs.inflight = ""
        PrinterHub.update { it.copy(online = true, queue = keys.size, unknownKey = prefs.inflight, lastError = "") }
        updateNotification(keys.size, ready)
        if (ready != UsbPrinter.Ready.OK || keys.isEmpty()) { writeStatusBlocking(); return }
        val printed = prefs.printed().toHashSet()
        var left = keys.size
        for (key in keys) {
            if (key in printed) { runCatching { db.delete("q/$qk/$key") }; runCatching { db.delete("p/$qk/jl/$key") }; left--; continue }
            if (key == prefs.inflight) continue
            @Suppress("UNCHECKED_CAST")
            val item = (db.get("q/$qk/$key") as? Map<String, Any?>)?.let { JObj(it) } ?: continue
            val t = item.long("t")
            val days = prefs.skipOlderDays
            if (days > 0 && t > 0 && System.currentTimeMillis() - t > days * 86_400_000L) {
                runCatching { db.delete("q/$qk/$key") }
                runCatching { db.delete("p/$qk/jl/$key") }
                PrinterHub.log(getString(R.string.pr_log_skipped_old))
                left--
                continue
            }
            val bytes = runCatching { B64.decode(item.str("b")) }.getOrNull()
            if (bytes == null || bytes.isEmpty()) { runCatching { db.delete("q/$qk/$key") }; left--; continue }
            if (!printOne(db, qk, key, bytes)) break
            left--
            PrinterHub.update { it.copy(queue = left) }
            Thread.sleep(300)
        }
        updateNotification(left, ready)
        writeStatusBlocking()
    }

    /** @return false — printer xatosi (navbat to'xtaydi, keyinroq qayta). */
    private fun printOne(db: CloudDb, qk: String, key: String, bytes: ByteArray): Boolean {
        val prefs = Locator.prefs
        prefs.inflight = key
        try {
            UsbPrinter.send(this, bytes)
        } catch (e: UsbPrinter.SendException) {
            if (!e.maybePrinted) prefs.inflight = ""
            PrinterHub.update { it.copy(lastError = e.message.orEmpty(), unknownKey = prefs.inflight) }
            PrinterHub.log("! " + e.message)
            return false
        }
        prefs.addPrinted(key)
        prefs.inflight = ""
        prefs.printedTotal = prefs.printedTotal + 1
        prefs.lastPrintAt = System.currentTimeMillis()
        PrinterHub.update { it.copy(printedTotal = prefs.printedTotal, lastPrintAt = prefs.lastPrintAt, unknownKey = "") }
        PrinterHub.log(getString(R.string.pr_log_printed, bytes.size))
        runCatching { db.delete("q/$qk/$key") }
        runCatching { db.delete("p/$qk/jl/$key") }
        return true
    }

    private fun handle(cmd: PrinterHub.Command) {
        val prefs = Locator.prefs
        when (cmd) {
            PrinterHub.Command.TestPage -> {
                val lines = listOf("IMSOFT Mijoz", "Sinov cheki / Test", java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US).format(java.util.Date()), "OK")
                try {
                    UsbPrinter.send(this, EscPos.testPage(lines))
                    PrinterHub.log(getString(R.string.pr_log_test_ok))
                } catch (e: UsbPrinter.SendException) {
                    PrinterHub.log("! " + e.message)
                    PrinterHub.update { it.copy(lastError = e.message.orEmpty()) }
                }
            }
            PrinterHub.Command.DropUnknown -> {
                val key = prefs.inflight
                if (key.isEmpty()) return
                prefs.addPrinted(key)
                prefs.inflight = ""
                db()?.let { (db, qk) -> runCatching { db.delete("q/$qk/$key") } }
                PrinterHub.update { it.copy(unknownKey = "") }
                PrinterHub.log(getString(R.string.pr_log_dropped))
            }
            PrinterHub.Command.ReprintUnknown -> {
                val key = prefs.inflight
                if (key.isEmpty()) return
                val (db, qk) = db() ?: return
                @Suppress("UNCHECKED_CAST")
                val item = (db.get("q/$qk/$key") as? Map<String, Any?>)?.let { JObj(it) }
                if (item == null) { prefs.inflight = ""; PrinterHub.update { it.copy(unknownKey = "") }; return }
                printOne(db, qk, key, B64.decode(item.str("b")))
            }
        }
    }

    // ---------------- "yangi chek bor" signali (Firebase SSE) ----------------

    private suspend fun streamLoop() {
        var backoff = 2_000L
        while (scope.isActive) {
            val (db, qk) = db() ?: run { delay(30_000L); null } ?: continue
            try {
                val c = URL(db.url("p/$qk/sig")).openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "text/event-stream")
                c.connectTimeout = 15_000
                c.readTimeout = 90_000 // Firebase har ~30 s "keep-alive" yuboradi
                c.instanceFollowRedirects = true
                try {
                    if (c.responseCode != 200) throw java.io.IOException("SSE HTTP ${c.responseCode}")
                    backoff = 2_000L
                    BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                        while (scope.isActive) {
                            val line = r.readLine() ?: break
                            if (line.startsWith("event:")) {
                                val ev = line.substringAfter(':').trim()
                                if (ev == "put" || ev == "patch") PrinterHub.nudge()
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
                // Internet yo'q — keyinroq. Zaxira: asosiy sikl baribir har 60 s tekshiradi.
            }
            delay(backoff)
            backoff = minOf(backoff * 2, 60_000L)
        }
    }

    // ---------------- holat (1-ilova ko'radi) ----------------

    private fun writeStatus() { scope.launch { runCatching { writeStatusBlocking() } } }

    private fun writeStatusBlocking(stopped: Boolean = false) {
        val (db, qk) = db() ?: return
        val st = PrinterHub.state.value
        val text = when {
            stopped -> getString(R.string.pr_st_stopped)
            st.ready == UsbPrinter.Ready.OK -> getString(R.string.pr_ready_ok)
            st.ready == UsbPrinter.Ready.NO_PERMISSION -> getString(R.string.pr_ready_perm)
            else -> getString(R.string.pr_ready_none)
        }
        runCatching {
            db.put(
                "p/$qk/status",
                linkedMapOf(
                    "at" to CloudDb.SERVER_TIME, "ready" to (!stopped && st.ready == UsbPrinter.Ready.OK), "text" to text,
                    "queue" to st.queue, "printed" to st.printedTotal, "unknown" to st.unknownKey.isNotEmpty(),
                ),
            )
        }
    }

    // ---------------- bildirishnoma ----------------

    private fun startAsForeground(text: String) {
        val n = notification(text)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun updateNotification(queue: Int, ready: UsbPrinter.Ready) {
        val text = when (ready) {
            UsbPrinter.Ready.OK -> if (queue > 0) getString(R.string.pr_notif_printing, queue) else getString(R.string.pr_notif_waiting)
            UsbPrinter.Ready.NO_PERMISSION -> getString(R.string.pr_ready_perm)
            else -> if (queue > 0) getString(R.string.pr_notif_queue_no_printer, queue) else getString(R.string.pr_notif_waiting_no_printer)
        }
        if (text == lastNotified) return
        lastNotified = text
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text)) }
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.pr_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_PRINTER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_print)
            .setContentTitle(getString(R.string.pr_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "printer"
        private const val NOTIF_ID = 7001
        const val ACTION_STOP = "uz.imsoft.mijoz.STOP_PRINTER"

        fun start(ctx: Context) {
            Locator.prefs.printerOn = true
            ContextCompat.startForegroundService(ctx, Intent(ctx, PrintService::class.java))
        }

        fun stop(ctx: Context) {
            Locator.prefs.printerOn = false
            ctx.stopService(Intent(ctx, PrintService::class.java))
        }
    }
}

/** Telefon qayta yoqilganda (yoki ilova yangilanganda) servis o'zi ishga tushadi. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = Locator.prefs(context)
        if (p.printerSection && p.printerOn && p.autostart) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PrintService::class.java)) }
        }
    }
}
