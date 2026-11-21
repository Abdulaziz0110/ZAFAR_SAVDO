package com.ilxomjon.dur_novvot_dokon.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * v5.3.8: rasm orqali kirim — rasmni o'qish (Anthropic Claude, rasm o'qiydigan model) va o'rganilganlarni saqlash.
 * Kalit (API key) Admin telefonida shifrlangan holda ([com.ilxomjon.dur_novvot_dokon.data.SecureStore.aiKey]).
 */
class ReceiptAi(private val app: Context, private val key: () -> String, private val model: () -> String) {
    private val memFile = File(app.filesDir, "receipt_memory.json")
    val dir: File get() = File(app.cacheDir, "receipt").apply { mkdirs() }

    /** Galereya yoki kameradan — EXIF bo'yicha tik, [max] pikselgacha kichraytirilgan va [dir] ga saqlangan nusxa. */
    suspend fun load(source: Uri, max: Int = 2200): File? = withContext(Dispatchers.IO) {
        val r = app.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { r.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) } }
        if (opts.outWidth <= 0) return@withContext null
        var s = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (s * 2) >= max) s *= 2
        val bmp = runCatching { r.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } }
            .getOrNull() ?: return@withContext null
        val deg = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) runCatching {
            r.openInputStream(source)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0) else 0
        val up = scale(rotate(bmp, deg), max)
        val f = File(dir, "p" + System.currentTimeMillis() + ".jpg")
        val ok = runCatching { f.outputStream().use { up.compress(Bitmap.CompressFormat.JPEG, 88, it) } }.getOrDefault(false)
        if (ok) f else null
    }

    /** Rasmni o'qish. Internet va kalit kerak. */
    suspend fun read(photo: File): ReceiptScan.Result = withContext(Dispatchers.IO) {
        val k = key().trim()
        if (k.isEmpty()) throw IllegalStateException(NO_KEY)
        val bmp = BitmapFactory.decodeFile(photo.path) ?: throw IllegalStateException("rasm o'qilmadi")
        val small = scale(bmp, 1568)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        val body = ReceiptScan.request(b64, model()).toByteArray(Charsets.UTF_8)
        val c = URL(ReceiptScan.API_URL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 180_000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("x-api-key", k)
            c.setRequestProperty("anthropic-version", ReceiptScan.API_VERSION)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { ReceiptScan.replyText(text) }.exceptionOrNull()?.message ?: text.take(200)
                throw IllegalStateException("HTTP $code: $msg")
            }
            ReceiptScan.parse(ReceiptScan.replyText(text))
        } finally {
            c.disconnect()
        }
    }

    fun memory(): ReceiptMatch.Memory = runCatching { ReceiptMatch.read(memFile.readText()) }.getOrElse { ReceiptMatch.Memory() }

    fun save(mem: ReceiptMatch.Memory) {
        runCatching {
            val tmp = File(memFile.path + ".tmp")
            tmp.writeText(ReceiptMatch.write(mem))
            tmp.renameTo(memFile)
        }
    }

    /** Qator kesimi: tik qilingan rasmdan [y0]..[y1] (ulush) oralig'i, ozgina zaxira bilan. */
    fun crop(photo: File, rotateDeg: Int, y0: Double, y1: Double): Bitmap? = runCatching {
        val bmp = rotate(BitmapFactory.decodeFile(photo.path) ?: return null, rotateDeg)
        val pad = 0.025
        val top = ((y0 - pad).coerceIn(0.0, 1.0) * bmp.height).toInt()
        val bottom = ((y1 + pad).coerceIn(0.0, 1.0) * bmp.height).toInt().coerceAtLeast(top + 8).coerceAtMost(bmp.height)
        Bitmap.createBitmap(bmp, 0, top, bmp.width, (bottom - top).coerceAtLeast(1))
    }.getOrNull()

    fun upright(photo: File, rotateDeg: Int): Bitmap? = runCatching { rotate(BitmapFactory.decodeFile(photo.path) ?: return null, rotateDeg) }.getOrNull()

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        if (deg % 360 == 0) return b
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return runCatching { Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true) }.getOrDefault(b)
    }

    private fun scale(b: Bitmap, max: Int): Bitmap {
        val big = maxOf(b.width, b.height)
        if (big <= max) return b
        val k = max.toFloat() / big
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    companion object {
        const val NO_KEY = "no_key"
    }
}
