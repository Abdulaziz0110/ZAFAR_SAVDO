package uz.imsoft.shared

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Base64 (java.util.Base64 Android 8 dan oldin yo'q, 1-ilova Android 6 da ham ishlaydi). */
object B64 {
    private const val STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val DEC = IntArray(128) { -1 }.also { t ->
        for (i in STD.indices) t[STD[i].code] = i
        t['-'.code] = 62; t['_'.code] = 63
    }

    fun encode(data: ByteArray, urlSafe: Boolean = false, pad: Boolean = true): String {
        val a = if (urlSafe) URL else STD
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8) or (data[i + 2].toInt() and 0xFF)
            sb.append(a[n ushr 18 and 63]).append(a[n ushr 12 and 63]).append(a[n ushr 6 and 63]).append(a[n and 63])
            i += 3
        }
        val rem = data.size - i
        if (rem == 1) {
            val n = data[i].toInt() and 0xFF shl 16
            sb.append(a[n ushr 18 and 63]).append(a[n ushr 12 and 63])
            if (pad) sb.append("==")
        } else if (rem == 2) {
            val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8)
            sb.append(a[n ushr 18 and 63]).append(a[n ushr 12 and 63]).append(a[n ushr 6 and 63])
            if (pad) sb.append('=')
        }
        return sb.toString()
    }

    /** Ikkala alifboni ham, bo'shliq/yangi qatorlarni ham qabul qiladi. Noto'g'ri belgi bo'lsa IllegalArgumentException. */
    fun decode(text: String): ByteArray {
        val out = ByteArrayOutputStream(text.length * 3 / 4)
        var buf = 0
        var bits = 0
        for (c in text) {
            if (c == '=' ) break
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue
            val v = if (c.code < 128) DEC[c.code] else -1
            require(v >= 0) { "Base64 emas: '$c'" }
            buf = (buf shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write(buf ushr bits and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size / 4 + 64)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    fun gunzip(data: ByteArray, maxBytes: Int = 64 * 1024 * 1024): ByteArray {
        val bos = ByteArrayOutputStream(data.size * 4)
        GZIPInputStream(ByteArrayInputStream(data)).use { inp ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                bos.write(buf, 0, n)
                require(bos.size() <= maxBytes) { "Ma'lumot juda katta" }
            }
        }
        return bos.toByteArray()
    }
}
