package uz.imsoft.mijoz.data

import android.content.Context
import android.content.SharedPreferences
import uz.imsoft.shared.LinkCode

/** 2-ilova sozlamalari. Ulanish kodida maxfiy kalit yo'q — oddiy SharedPreferences yetarli. */
class MijozPrefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("mijoz_prefs", Context.MODE_PRIVATE)

    private fun s(k: String, d: String = "") = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun b(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    // ---------------- ulanish ----------------
    val link: LinkCode?
        get() {
            val u = s("db_url"); val r = s("read_key")
            if (u.isEmpty() || r.isEmpty()) return null
            return LinkCode(u, r, s("queue_key"), s("client_name"))
        }

    fun setLink(c: LinkCode?) {
        p.edit().putString("db_url", c?.dbUrl.orEmpty()).putString("read_key", c?.readKey.orEmpty())
            .putString("queue_key", c?.queueKey.orEmpty()).putString("client_name", c?.clientName.orEmpty())
            .putLong("meta_at", 0L).apply()
    }

    /** Oxirgi yuklangan ma'lumotning server vaqti (Firebase). */
    var metaAt: Long get() = p.getLong("meta_at", 0L); set(v) = p.edit().putLong("meta_at", v).apply()
    var fetchedAt: Long get() = p.getLong("fetched_at", 0L); set(v) = p.edit().putLong("fetched_at", v).apply()
    var requestAt: Long get() = p.getLong("request_at", 0L); set(v) = p.edit().putLong("request_at", v).apply()

    // ---------------- ko'rinish ----------------
    var language: String get() = s("language", "lat"); set(v) = put("language", v)
    var theme: String get() = s("theme", "system"); set(v) = put("theme", v)
    var uiScale: Float
        get() {
            // v2.5: eski "Ixcham" (0.92) standart edi — ro'yxatlar mayda ko'rinardi. Bir marta "Odatiy" ga o'tkaziladi.
            if (!p.getBoolean("scale_v25", false)) {
                val old = p.getFloat("ui_scale", 1.0f)
                val e = p.edit().putBoolean("scale_v25", true)
                if (old == 0.92f) e.putFloat("ui_scale", 1.0f) else if (old == 0.85f) e.putFloat("ui_scale", 0.9f)
                e.apply()
            }
            return p.getFloat("ui_scale", 1.0f)
        }
        set(v) = p.edit().putFloat("ui_scale", v).apply()
    var fontScale: Float get() = p.getFloat("font_scale", 1.0f); set(v) = p.edit().putFloat("font_scale", v).apply()
    /** Narxlar: "cash" (Naqd) / "wholesale" (Optom). */
    var priceType: String get() = s("price_type", "wholesale"); set(v) = put("price_type", v)
    /** "som" / "usd". */
    var currency: String get() = s("currency", "som"); set(v) = put("currency", v)
    var fuzzy: Boolean get() = b("fuzzy", true); set(v) = putB("fuzzy", v)
    /** Ilova ochilganda o'zi yangilasin. */
    var autoRefresh: Boolean get() = b("auto_refresh", true); set(v) = putB("auto_refresh", v)
    /** Fonda avtomatik yangilash oralig'i, daqiqa (0 — o'chiq). v2.5 */
    var autoMinutes: Int get() = p.getInt("auto_minutes", 60); set(v) = p.edit().putInt("auto_minutes", v).apply()
    /** Yangi ma'lumot kelganda bildirishnoma. */
    var notifyNew: Boolean get() = b("notify_new", true); set(v) = putB("notify_new", v)
    /** Yuborilgan buyurtmalar id lari (oxirgi 50) — holatini ko'rsatish uchun. */
    var myOrders: String get() = s("my_orders"); set(v) = put("my_orders", v)

    // ---------------- chek printeri ----------------
    /** "Printer" bo'limi ko'rinsin (2-filial telefoni). */
    var printerSection: Boolean get() = b("printer_section", false); set(v) = putB("printer_section", v)
    /** Servis yoqilgan (foydalanuvchi "Serverni yoqish" bosgan). */
    var printerOn: Boolean get() = b("printer_on", false); set(v) = putB("printer_on", v)
    var autostart: Boolean get() = b("autostart", true); set(v) = putB("autostart", v)
    /** Shuncha kundan eski cheklar o'zi chop etilmaydi (0 — hammasi chiqadi). */
    var skipOlderDays: Int get() = p.getInt("skip_older", 0); set(v) = p.edit().putInt("skip_older", v).apply()

    /** Chop etilgan chek kalitlari (dublikatdan himoya), oxirgi 300 ta. */
    fun printed(): List<String> = s("printed").split('|').filter { it.isNotEmpty() }
    fun addPrinted(key: String) {
        val l = (printed() + key).takeLast(300)
        p.edit().putString("printed", l.joinToString("|")).commit()
    }
    /** Hozir printerga yuborilayotgan chek (jarayon shu yerda o'lsa — natija noma'lum). */
    var inflight: String
        get() = s("inflight")
        set(v) { p.edit().putString("inflight", v).commit() }
    var printedTotal: Int get() = p.getInt("printed_total", 0); set(v) = p.edit().putInt("printed_total", v).apply()
    var lastPrintAt: Long get() = p.getLong("last_print", 0L); set(v) = p.edit().putLong("last_print", v).apply()
}
