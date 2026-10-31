package uz.imsoft.mijoz.printer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build

/**
 * USB (OTG) orqali ulangan ESC/POS chek printeri (Xprinter XP-Q80A: vid=1fc9 pid=2016, interfeys sinfi 7).
 * ChekPrinter loyihasida sinalgan yo'l: sinf 7 (printer) ustuvor, bo'lmasa sinf 255 (vendor) + bulk OUT.
 */
object UsbPrinter {
    const val ACTION_PERMISSION = "uz.imsoft.mijoz.USB_PERMISSION"
    private const val CHUNK = 16 * 1024
    private const val TIMEOUT_MS = 5_000

    enum class Ready { OK, NO_DEVICE, NO_PERMISSION, NO_USB_HOST }

    class Found(val device: UsbDevice, val iface: UsbInterface, val out: UsbEndpoint)

    private fun manager(ctx: Context): UsbManager? = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager

    fun find(ctx: Context): Found? {
        val um = manager(ctx) ?: return null
        val devices = runCatching { um.deviceList.values.toList() }.getOrDefault(emptyList())
        for (cls in intArrayOf(UsbConstants.USB_CLASS_PRINTER, UsbConstants.USB_CLASS_VENDOR_SPEC)) {
            for (d in devices) {
                for (i in 0 until d.interfaceCount) {
                    val itf = d.getInterface(i)
                    if (itf.interfaceClass != cls) continue
                    for (e in 0 until itf.endpointCount) {
                        val ep = itf.getEndpoint(e)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) return Found(d, itf, ep)
                    }
                }
            }
        }
        return null
    }

    fun ready(ctx: Context): Ready {
        if (!ctx.packageManager.hasSystemFeature("android.hardware.usb.host")) return Ready.NO_USB_HOST
        val f = find(ctx) ?: return Ready.NO_DEVICE
        return if (manager(ctx)?.hasPermission(f.device) == true) Ready.OK else Ready.NO_PERMISSION
    }

    fun requestPermission(ctx: Context) {
        val f = find(ctx) ?: return
        val intent = Intent(ACTION_PERMISSION).setPackage(ctx.packageName)
        // Natija qo'shimcha maydondan o'qilmaydi (keyin hasPermission tekshiriladi), shuning uchun o'zgarmas PendingIntent yetarli
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        manager(ctx)?.requestPermission(f.device, PendingIntent.getBroadcast(ctx, 0, intent, flags or PendingIntent.FLAG_UPDATE_CURRENT))
    }

    /** Barcha USB qurilmalar (tashxis uchun). */
    fun describeAll(ctx: Context): String {
        val um = manager(ctx) ?: return "USB yo'q"
        val list = um.deviceList.values
        if (list.isEmpty()) return "—"
        return list.joinToString("\n") { d ->
            val classes = (0 until d.interfaceCount).joinToString(",") { d.getInterface(it).interfaceClass.toString() }
            "%s vid=%04x pid=%04x sinf=%s ruxsat=%s".format(d.deviceName, d.vendorId, d.productId, classes, if (um.hasPermission(d)) "bor" else "yo'q")
        }
    }

    class SendException(message: String, val maybePrinted: Boolean) : Exception(message)

    /**
     * Baytlarni printerga yozadi. Xato bo'lsa [SendException]: [SendException.maybePrinted] = true — bir qismi yetib bordi
     * (chek yarim chiqqan bo'lishi mumkin).
     */
    fun send(ctx: Context, data: ByteArray) {
        val um = manager(ctx) ?: throw SendException("USB yo'q", false)
        val f = find(ctx) ?: throw SendException("Printer topilmadi", false)
        if (!um.hasPermission(f.device)) throw SendException("USB ruxsati yo'q", false)
        val conn = um.openDevice(f.device) ?: throw SendException("Printerni ochib bo'lmadi", false)
        var sent = 0
        try {
            if (!conn.claimInterface(f.iface, true)) throw SendException("Interfeys band", false)
            while (sent < data.size) {
                val n = minOf(CHUNK, data.size - sent)
                val r = conn.bulkTransfer(f.out, data, sent, n, TIMEOUT_MS)
                if (r <= 0) throw SendException("Printer javob bermadi ($sent/${data.size} bayt)", sent > 0)
                sent += r
            }
        } finally {
            runCatching { conn.releaseInterface(f.iface) }
            runCatching { conn.close() }
        }
    }
}

/** ESC/POS yordamchilari (sinov cheki). Haqiqiy cheklar kompyuterdan tayyor bayt bo'lib keladi va o'zgartirilmaydi. */
object EscPos {
    private val INIT = byteArrayOf(0x1B, 0x40)
    private val CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    fun testPage(lines: List<String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(INIT)
        out.write(byteArrayOf(0x1B, 0x61, 0x01)) // markaz
        for (l in lines) {
            out.write(l.map { if (it.code in 32..126) it.code.toByte() else '?'.code.toByte() }.toByteArray())
            out.write(0x0A)
        }
        out.write(byteArrayOf(0x1B, 0x61, 0x00))
        out.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A))
        out.write(CUT)
        return out.toByteArray()
    }
}
