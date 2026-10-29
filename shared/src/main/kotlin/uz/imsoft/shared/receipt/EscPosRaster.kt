package uz.imsoft.shared.receipt

import java.io.ByteArrayOutputStream

/**
 * ESC/POS rastr rasm (GS v 0). Chek matni rasm bo'lib chiqariladi — shuning uchun kirill, lotin, o'/g' harflari
 * printerning kod sahifasiga bog'liq emas (Xprinter XP-Q80A: 80 mm = 576 nuqta).
 */
object EscPosRaster {
    private val INIT = byteArrayOf(0x1B, 0x40)
    private val CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    /** Bir buyruqdagi qator soni (ba'zi printerlar katta rasmni bitta buyruqda qabul qilmaydi). */
    private const val BAND = 240

    /** [black] — width*height, true = qora nuqta. */
    fun encode(width: Int, height: Int, black: BooleanArray, cut: Boolean = true, feedLines: Int = 4): ByteArray {
        require(black.size >= width * height)
        val bytesPerRow = (width + 7) / 8
        val out = ByteArrayOutputStream(bytesPerRow * height + 64)
        out.write(INIT)
        out.write(CENTER)
        var y0 = 0
        while (y0 < height) {
            val h = minOf(BAND, height - y0)
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, (bytesPerRow and 0xFF).toByte(), (bytesPerRow shr 8 and 0xFF).toByte(), (h and 0xFF).toByte(), (h shr 8 and 0xFF).toByte()))
            for (y in y0 until y0 + h) {
                val row = y * width
                for (bx in 0 until bytesPerRow) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val x = bx * 8 + bit
                        if (x < width && black[row + x]) b = b or (0x80 shr bit)
                    }
                    out.write(b)
                }
            }
            y0 += h
        }
        out.write(LEFT)
        repeat(feedLines) { out.write(0x0A) }
        if (cut) out.write(CUT)
        return out.toByteArray()
    }
}
