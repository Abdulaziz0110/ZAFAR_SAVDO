package uz.imsoft.shared.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.imsoft.shared.sched.RateQuote
import java.net.HttpURLConnection
import java.net.URL

/**
 * v5.0: dollar kursini internetdan olish. Manbalar ketma-ket so'raladi, javob bergani ko'rsatiladi.
 * Bank kursi — bozorga yaqin (sotish narxi), Markaziy bank kursi — rasmiy. Kurs 1C'ga faqat Admin
 * tasdiqlasa yoziladi (1C baribir asosiy manba).
 */
class RateOnline(private val context: Context) {
    data class Source(val title: String, val url: String, val market: Boolean)

    private val prefs = context.getSharedPreferences("rate_online", Context.MODE_PRIVATE)

    /** Oxirgi muvaffaqiyatli o'qish vaqti (0 — hali o'qilmagan). */
    val checkedAt: Long get() = prefs.getLong("at", 0L)

    /** Oxirgi o'qilgan kurslar (tarmoqsiz ham ko'rsatish uchun). */
    fun cached(): List<RateQuote.Quote> = SOURCES.mapNotNull { s ->
        val sell = prefs.getFloat("s_" + s.title, 0f).toDouble()
        val buy = prefs.getFloat("b_" + s.title, 0f).toDouble()
        if (sell <= 0 && buy <= 0) null else RateQuote.Quote(s.title, buy, sell, prefs.getString("d_" + s.title, "").orEmpty())
    }

    /** Tarmoqdan o'qiydi. Hech biri javob bermasa — oxirgi saqlangani. */
    suspend fun load(): List<RateQuote.Quote> {
        val got = withContext(Dispatchers.IO) {
            SOURCES.mapNotNull { s -> fetch(s.url)?.let { RateQuote.parse(s.title, it) } }
        }
        if (got.isEmpty()) return cached()
        val ed = prefs.edit().putLong("at", System.currentTimeMillis())
        for (q in got) ed.putFloat("s_" + q.source, q.sell.toFloat()).putFloat("b_" + q.source, q.buy.toFloat()).putString("d_" + q.source, q.date)
        ed.apply()
        return got
    }

    private fun fetch(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 15_000
        c.useCaches = false
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Birinchisi — bozorga yaqin bank kursi, ikkinchisi — rasmiy kurs (zaxira). */
        val SOURCES = listOf(
            Source("Bank kursi", "https://nbu.uz/uz/exchange-rates/json/", market = true),
            Source("Markaziy bank", "https://cbu.uz/uz/arkhiv-kursov-valyut/json/USD/", market = false),
        )
    }
}
