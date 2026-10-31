package uz.imsoft.shared.android

import android.content.Context
import android.graphics.Typeface

/**
 * Chek va yorliq uchun shriftlar: Liberation Sans (Arial bilan bir xil o'lchamli, OFL) — ilova assets'ida.
 * Yuklanmasa tizim shrifti ishlatiladi.
 */
object PrintFonts {
    @Volatile var regular: Typeface? = null
    @Volatile var bold: Typeface? = null

    fun init(ctx: Context) {
        val a = ctx.applicationContext.assets
        regular = runCatching { Typeface.createFromAsset(a, "fonts/LiberationSans-Regular.ttf") }.getOrNull()
        bold = runCatching { Typeface.createFromAsset(a, "fonts/LiberationSans-Bold.ttf") }.getOrNull()
        LabelBitmap.font = bold
    }
}

/** Chek logosi rasmini qayta-qayta o'qimaslik uchun (oxirgisi xotirada). */
object LogoCache {
    private var key = 0
    private var size = -1
    private var bmp: android.graphics.Bitmap? = null

    @Synchronized
    fun get(bytes: ByteArray?): android.graphics.Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        val h = bytes.contentHashCode()
        if (h == key && bytes.size == size) return bmp
        bmp = ReceiptBitmap.decodeLogo(bytes)
        key = h
        size = bytes.size
        return bmp
    }

    fun fromBase64(s: String?): android.graphics.Bitmap? =
        if (s.isNullOrEmpty()) null else get(runCatching { uz.imsoft.shared.B64.decode(s) }.getOrNull())
}
