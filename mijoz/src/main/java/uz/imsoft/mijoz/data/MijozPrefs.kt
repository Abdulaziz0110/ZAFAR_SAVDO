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

    // ---------------- kirish (v2.6: telefon + parol) ----------------
    /** Kirilgan telefon (998...). Bo'sh — "ulash kodi" bilan yoki ulanmagan. Parol alohida, shifrlangan joyda. */
    var loginPhone: String get() = s("login_phone"); set(v) = put("login_phone", v)
    /** 1-ilovada shu telefonga yozilgan ism (buyurtmada "kim" bo'lib boradi). */
    var memberName: String get() = s("member_name"); set(v) = put("member_name", v)
    var loginClientId: String get() = s("login_client"); set(v) = put("login_client", v)
    /** Oxirgi kiritilgan do'kon kodi (keyingi safar tayyor tursin). */
    var lastShopCode: String get() = s("last_shop"); set(v) = put("last_shop", v)
    /** Kirish bekor qilingan vaqt (1-ilovada telefon o'chirildi yoki parol almashdi). */
    var revokedAt: Long get() = p.getLong("revoked_at", 0L); set(v) = p.edit().putLong("revoked_at", v).apply()
    /** Kirish oynasini "keyinroq" deb yopgan (faqat shu ochilishda). */
    var skipLogin: Boolean = false

    /** Oxirgi yuklangan ma'lumotning server vaqti (Firebase). */
    var metaAt: Long get() = p.getLong("meta_at", 0L); set(v) = p.edit().putLong("meta_at", v).apply()
    var fetchedAt: Long get() = p.getLong("fetched_at", 0L); set(v) = p.edit().putLong("fetched_at", v).apply()
    var requestAt: Long get() = p.getLong("request_at", 0L); set(v) = p.edit().putLong("request_at", v).apply()

    // ---------------- ko'rinish ----------------
    var language: String get() = s("language", "lat"); set(v) = put("language", v)
    var theme: String get() = s("theme", "dark"); set(v) = put("theme", v)
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
    var fontScale: Float get() = p.getFloat("font_scale", 0.9f); set(v) = p.edit().putFloat("font_scale", v).apply()
    /** Narxlar: "cash" (Naqd) / "wholesale" (Optom). */
    var priceType: String get() = s("price_type", "wholesale"); set(v) = put("price_type", v)

    /** v3.1: 1..4-narx ("cash" = 3, "wholesale" = 4, "1"/"2"). */
    var priceLevel: Int
        get() = when (priceType) { "cash" -> 3; "1" -> 1; "2" -> 2; else -> 4 }
        set(v) { priceType = when (v) { 3 -> "cash"; 1 -> "1"; 2 -> "2"; else -> "wholesale" } }
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
    /** v4.0: kalitsiz ulanishda printer bir marta o'zi yoqilgan (keyin foydalanuvchi tanlovi saqlanadi). */
    var devPrinterSet: Boolean get() = b("dev_printer_set", false); set(v) = putB("dev_printer_set", v)
    /** Servis yoqilgan (foydalanuvchi "Serverni yoqish" bosgan). */
    var printerOn: Boolean get() = b("printer_on", false); set(v) = putB("printer_on", v)
    /** 1-ilova savdolari chekini qabul qilish (bulutdagi sozlamaning nusxasi). */
    var saleReceipts: Boolean get() = b("sale_receipts", false); set(v) = putB("sale_receipts", v)
    var autostart: Boolean get() = b("autostart", true); set(v) = putB("autostart", v)
    /** Qo'shimcha sozlamalar: ilova qaysi bo'limdan ochilsin ("home", "prices", "report"). */
    var startTab: String get() = s("start_tab", "home"); set(v) = put("start_tab", v)
    /** Qo'shimcha sozlamalar: ilova ochiq turganda ekran o'chmasin. */
    var keepScreenOn: Boolean get() = b("keep_screen_on", false); set(v) = putB("keep_screen_on", v)
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

    // ---------------- v3.2 ----------------
    /** Ish vaqti: avtomatik yangilash shu oraliqda tez; tugashidan 1 daqiqa oldin oxirgi to'liq yuklab olish. */
    var workHours: uz.imsoft.shared.sched.WorkHours
        get() = uz.imsoft.shared.sched.WorkHours.decode(p.getString("work_hours", null))
        set(v) = p.edit().putString("work_hours", v.normalized().encode()).apply()
    /** Ilova ochiq turganda ish vaqtida yangilash oralig'i (daqiqa). */
    var liveMinutes: Int get() = p.getInt("live_min", 2); set(v) = p.edit().putInt("live_min", v.coerceIn(1, 60)).apply()
    var lastFinalAt: Long get() = p.getLong("final_at", 0L); set(v) = p.edit().putLong("final_at", v).apply()
    /** Kunlik zaxira. */
    var dailyBackup: Boolean get() = b("daily_backup", true); set(v) = putB("daily_backup", v)
    var lastBackupAt: Long get() = p.getLong("backup_at", 0L); set(v) = p.edit().putLong("backup_at", v).apply()
    var lastBackupError: String get() = s("backup_err"); set(v) = put("backup_err", v)
    /** Chek qo'shimchalari (sarlavha — do'kon nomi — o'zgarmaydi): sarlavha ostidagi qatorlar, chek osti, rahmat matni. */
    var receiptSub: String get() = s("rc_sub"); set(v) = put("rc_sub", v)
    var receiptFooter: String get() = s("rc_footer"); set(v) = put("rc_footer", v)
    var receiptThanks: String get() = s("rc_thanks"); set(v) = put("rc_thanks", v)
    /** Shu telefondan yuborilgan to'lov va tuzatish so'rovlari (id lar). */
    var myPays: String get() = s("my_pays"); set(v) = put("my_pays", v)
    var myEdits: String get() = s("my_edits"); set(v) = put("my_edits", v)
    /** Bosh sahifada "Kassa" (o'z savdosi). */
    var posOn: Boolean get() = b("pos_on", true); set(v) = putB("pos_on", v)
}
