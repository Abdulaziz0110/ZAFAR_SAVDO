package uz.imsoft.shared.label

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Yorliq printeri buyruqlari (TSPL) — Xprinter XP-365B va shunga o'xshash yorliq printerlari tushunadi.
 * Rasm bitmap bo'lib yuboriladi: kirill/lotin, shrift va drayver sozlamasiga bog'liq emas.
 * TSPL BITMAP (rejim 0) da bit 1 — oq, bit 0 — qora (chop etiladi).
 */
object Tspl {
    /**
     * [black] — w×h, true = qora nuqta. [wMm]/[hMm] — yorliq o'lchami, [gapMm] — yorliqlar orasidagi bo'shliq.
     * [rotate] — 180° burish (DIRECTION 0/1).
     */
    fun build(
        black: BooleanArray, w: Int, h: Int, copies: Int,
        wMm: Double, hMm: Double, gapMm: Double = 2.0, rotate: Boolean = false, density: Int = 8, speed: Int = 4,
    ): ByteArray {
        require(black.size == w * h) { "tspl: size" }
        val wb = (w + 7) / 8
        val data = ByteArray(wb * h) { 0xFF.toByte() }
        for (y in 0 until h) {
            val row = y * w
            val out = y * wb
            for (x in 0 until w) {
                if (black[row + x]) {
                    val i = out + (x shr 3)
                    data[i] = (data[i].toInt() and (0x80 ushr (x and 7)).inv()).toByte()
                }
            }
        }
        val head = "SIZE ${n(wMm)} mm,${n(hMm)} mm\r\nGAP ${n(gapMm)} mm,0 mm\r\nDIRECTION ${if (rotate) 0 else 1}\r\n" +
            "REFERENCE 0,0\r\nDENSITY ${density.coerceIn(0, 15)}\r\nSPEED ${speed.coerceIn(1, 6)}\r\nCLS\r\n"
        val o = ByteArrayOutputStream(head.length + data.size + 64)
        o.write(head.toByteArray(Charsets.US_ASCII))
        o.write("BITMAP 0,0,$wb,$h,0,".toByteArray(Charsets.US_ASCII))
        o.write(data)
        o.write("\r\nPRINT 1,${copies.coerceIn(1, 999)}\r\n".toByteArray(Charsets.US_ASCII))
        return o.toByteArray()
    }

    private fun n(v: Double): String {
        val r = Math.round(v * 10) / 10.0
        return if (r == Math.rint(r)) r.toLong().toString() else r.toString()
    }
}

/**
 * LPR/LPD (RFC 1179) — Windows'ning o'z "LPD Print Service" xizmatiga to'g'ridan-to'g'ri chop etish.
 * Kompyuterda qo'shimcha dastur kerak emas: printer "общий доступ" bilan ulashiladi, navbat nomi = ulashish nomi.
 * Ma'lumot 'l' (xom, o'zgartirilmaydi) turida yuboriladi — TSPL printerga aynan yetadi.
 */
object Lpr {
    const val PORT = 515

    class LprError(val reason: String) : Exception(reason)

    private fun ack(inp: InputStream, step: String) {
        val b = inp.read()
        if (b != 0) throw LprError(if (b < 0) "closed:$step" else "refused:$step")
    }

    /** Chop etish. [job] — 0..999. @throws LprError navbat yo'q yoki rad etilsa. */
    fun print(
        host: String, queue: String, data: ByteArray, job: Int, title: String = "IMSOFT yorliq",
        clientHost: String = "android", user: String = "savdo", port: Int = PORT, connectMs: Int = 3000, readMs: Int = 60_000,
    ): Unit = Socket().use { s ->
        s.connect(InetSocketAddress(host, port), connectMs)
        s.soTimeout = readMs
        val out: OutputStream = s.getOutputStream().buffered()
        val inp = s.getInputStream()
        val q = clean(queue)
        val h = clean(clientHost).take(31).ifEmpty { "android" }
        val num = "%03d".format(job.coerceIn(0, 999))
        val df = "dfA$num$h"
        val cf = "cfA$num$h"
        cmd(out, "\u0002$q\n")
        ack(inp, "queue")
        val control = "H$h\nP${clean(user).ifEmpty { "savdo" }}\nJ${clean(title)}\nN${clean(title)}\nl$df\nU$df\n".toByteArray(Charsets.US_ASCII)
        cmd(out, "\u0002${control.size} $cf\n")
        ack(inp, "control")
        out.write(control); out.write(0); out.flush()
        ack(inp, "control_data")
        cmd(out, "\u0003${data.size} $df\n")
        ack(inp, "data")
        out.write(data); out.write(0); out.flush()
        ack(inp, "data_end")
    }

    /** Navbat holati (qisqa). Windows LPD navbat nomi to'g'ri bo'lsa matn qaytaradi. */
    fun queueState(host: String, queue: String, port: Int = PORT, connectMs: Int = 3000, readMs: Int = 8000): String = Socket().use { s ->
        s.connect(InetSocketAddress(host, port), connectMs)
        s.soTimeout = readMs
        val out = s.getOutputStream()
        out.write("\u0003${clean(queue)}\n".toByteArray(Charsets.US_ASCII))
        out.flush()
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(2048)
        try {
            while (buf.size() < 16_384) {
                val n = s.getInputStream().read(tmp)
                if (n < 0) break
                buf.write(tmp, 0, n)
            }
        } catch (e: java.net.SocketTimeoutException) {
            // javob tugadi
        }
        String(buf.toByteArray(), Charsets.ISO_8859_1).trim()
    }

    /** LPR buyruqlarida faqat oddiy ASCII (bo'shliq va boshqaruv belgilarisiz). */
    fun clean(s: String): String = s.trim().filter { it.code in 33..126 }

    private fun cmd(out: OutputStream, s: String) {
        out.write(s.toByteArray(Charsets.US_ASCII))
        out.flush()
    }
}
