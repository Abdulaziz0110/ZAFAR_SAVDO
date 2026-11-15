package com.ilxomjon.dur_novvot_dokon.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v5.3: mahsulot rasmlari — faqat TELEFONNING O'ZIDA (kompyuterga ham, 1C'ga ham ketmaydi).
 *
 * Har mahsulotga bitta rasm: files/pimg/<kod>.jpg, eng ko'pi 900 px (joy tejash uchun). Ro'yxatlar uchun kichik
 * nusxa xotirada saqlanadi. Mahsulot kodi fayl nomiga xavfsiz ko'rinishda o'tkaziladi.
 */
class ProductImages(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "pimg")
    private val resolver = context.applicationContext.contentResolver
    private val thumbs = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Rasmi bor mahsulotlar (kodlari). O'zgarganda ro'yxatlar qayta chiziladi. */
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids

    @Volatile private var loaded = false

    fun file(productId: String): File = File(dir, safeName(productId) + ".jpg")

    fun has(productId: String): Boolean {
        if (!loaded) scan()
        return productId in _ids.value
    }

    @Synchronized
    private fun scan() {
        if (loaded) return
        val names = dir.list()?.filter { it.endsWith(".jpg") }?.map { unsafeName(it.removeSuffix(".jpg")) }?.toSet().orEmpty()
        _ids.value = names
        loaded = true
    }

    /** Kichik rasm (ro'yxat uchun). Xotirada bo'lmasa fayldan o'qiydi — fon oqimida chaqiring yoki [thumbCached]. */
    fun thumb(productId: String): Bitmap? {
        if (!has(productId)) return null
        thumbs.get(productId)?.let { return it }
        val b = decode(file(productId), THUMB_PX) ?: return null
        thumbs.put(productId, b)
        return b
    }

    /** Faqat xotiradagisi (asosiy oqimda xavfsiz). */
    fun thumbCached(productId: String): Bitmap? = thumbs.get(productId)

    /** To'liq rasm (mahsulot oynasi uchun). */
    suspend fun full(productId: String): Bitmap? = withContext(Dispatchers.IO) { if (has(productId)) decode(file(productId), FULL_PX) else null }

    /** Galereya yoki kameradan kelgan rasmni kichraytirib saqlaydi. @return saqlandimi. */
    suspend fun save(productId: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val bmp = runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val sample = sampleFor(opts.outWidth, opts.outHeight, FULL_PX)
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, o2) }
        }.getOrNull() ?: return@withContext false
        val rotated = rotate(bmp, orientation(source))
        val scaled = scaleDown(rotated, FULL_PX)
        dir.mkdirs()
        val tmp = File(dir, safeName(productId) + ".tmp")
        val ok = runCatching { tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) } }.getOrDefault(false)
        if (!ok || !tmp.renameTo(file(productId))) { tmp.delete(); return@withContext false }
        thumbs.remove(productId)
        scan()
        _ids.value = _ids.value + productId
        true
    }

    suspend fun remove(productId: String) = withContext(Dispatchers.IO) {
        file(productId).delete()
        thumbs.remove(productId)
        scan()
        _ids.value = _ids.value - productId
    }

    private fun orientation(source: Uri): Int = runCatching {
        resolver.openInputStream(source)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } ?: 1
    }.getOrDefault(1)

    private fun rotate(b: Bitmap, exif: Int): Bitmap {
        val deg = when (exif) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return b
        }
        val m = android.graphics.Matrix().apply { postRotate(deg) }
        return runCatching { Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true) }.getOrDefault(b)
    }

    private fun decode(f: File, max: Int): Bitmap? = runCatching {
        if (!f.isFile) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, opts)
        val o2 = BitmapFactory.Options().apply { inSampleSize = sampleFor(opts.outWidth, opts.outHeight, max) }
        BitmapFactory.decodeFile(f.path, o2)?.let { scaleDown(it, max) }
    }.getOrNull()

    private fun scaleDown(b: Bitmap, max: Int): Bitmap {
        val big = maxOf(b.width, b.height)
        if (big <= max) return b
        val k = max.toFloat() / big
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    companion object {
        const val FULL_PX = 900
        const val THUMB_PX = 160

        /** 2 ning darajasi: o'qilgan rasm [max] dan kichik bo'lib qolmasin. */
        fun sampleFor(w: Int, h: Int, max: Int): Int {
            var s = 1
            val big = maxOf(w, h)
            while (big / (s * 2) >= max) s *= 2
            return s
        }

        /** Mahsulot kodi -> fayl nomi: harf-raqam o'z holicha, qolgani "_xx" (o'n oltilik). Qaytarsa bo'ladi. */
        fun safeName(id: String): String {
            val sb = StringBuilder()
            for (b in id.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt().toChar()
                if (b >= 0 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-')) sb.append(c)
                else sb.append('_').append("%02x".format(b.toInt() and 0xFF))
            }
            return sb.toString().ifEmpty { "_" }
        }

        fun unsafeName(name: String): String {
            if (name == "_") return ""
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < name.length) {
                val c = name[i]
                if (c == '_' && i + 2 < name.length) {
                    val v = name.substring(i + 1, i + 3).toIntOrNull(16)
                    if (v != null) { out.write(v); i += 3; continue }
                }
                out.write(c.code)
                i++
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }
}
