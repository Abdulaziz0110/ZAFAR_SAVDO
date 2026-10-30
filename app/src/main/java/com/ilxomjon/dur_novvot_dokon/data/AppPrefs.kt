package com.ilxomjon.dur_novvot_dokon.data

import android.content.Context
import android.content.SharedPreferences

enum class UiLanguage(val tag: String, val code: String) {
    LATIN("uz-Latn", "lat"), CYRILLIC("uz-Cyrl", "cyr");

    companion object { fun fromCode(c: String?) = entries.firstOrNull { it.code == c } ?: LATIN }
}

enum class UiTheme(val code: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object { fun fromCode(c: String?) = entries.firstOrNull { it.code == c } ?: SYSTEM }
}

/** Oddiy (sir bo'lmagan) sozlamalar. */
class AppPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var language: UiLanguage
        get() = UiLanguage.fromCode(prefs.getString("language", null))
        set(v) = prefs.edit().putString("language", v.code).apply()

    var theme: UiTheme
        // v2.6: standart — qorong'i mavzu (egasi tanlovi)
        get() = prefs.getString("theme", null)?.let { UiTheme.fromCode(it) } ?: UiTheme.DARK
        set(v) = prefs.edit().putString("theme", v.code).apply()

    var lastSyncAt: Long
        get() = prefs.getLong("last_sync", 0L)
        set(v) = prefs.edit().putLong("last_sync", v).apply()

    /** Tez tanlash uchun ko'pi bilan 2 ta do'kon (id). */
    var favoriteStoreIds: List<String>
        get() = prefs.getString("fav_stores", "").orEmpty().split('|').filter { it.isNotEmpty() }.take(2)
        set(v) = prefs.edit().putString("fav_stores", v.take(2).joinToString("|")).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen", false)
        set(v) = prefs.edit().putBoolean("keep_screen", v).apply()

    var printer: String
        get() = prefs.getString("printer", "1") ?: "1"
        set(v) = prefs.edit().putString("printer", v).apply()

    // ---------------- yangi funksiyalar (har biri Sozlamalarda o'chiriladi) ----------------
    private fun flag(key: String, def: Boolean = true) = prefs.getBoolean(key, def)
    private fun setFlag(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    /** Do'kon tanlanganda xulosa kartasi. */
    var fStoreCard: Boolean get() = flag("f_store_card"); set(v) = setFlag("f_store_card", v)
    /** Savdo ekranidagi aqlli asosiy tugma ("Odatdagini qo'sh" / "Yakunla" / "To'lov"). */
    var fSmartButton: Boolean get() = flag("f_smart_button"); set(v) = setFlag("f_smart_button", v)
    /** Taklif qatorida izoh ("Odatda ~7 kunda ~10"). */
    var fExplainSuggest: Boolean get() = flag("f_explain_suggest"); set(v) = setFlag("f_explain_suggest", v)
    /** Savatcha tekshiruvchisi (faqat ogohlantirish). */
    var fCartChecker: Boolean get() = flag("f_cart_checker"); set(v) = setFlag("f_cart_checker", v)
    /** Ko'p savatcha ("kutishga qo'yish"). */
    var fMultiCart: Boolean get() = flag("f_multi_cart"); set(v) = setFlag("f_multi_cart", v)
    /** Takroriy to'lovdan ogohlantirish. */
    var fDuplicatePaymentGuard: Boolean get() = flag("f_dup_payment"); set(v) = setFlag("f_dup_payment", v)
    /** KPI o'lchagich (faqat qurilmada). Dasturchi uchun — odatda o'chiq. */
    var fKpi: Boolean get() = flag("f_kpi", false); set(v) = setFlag("f_kpi", v)

    // ---------------- v2.4: Qulayliklar (Sozlamalar ▸ Qulayliklar) ----------------
    private fun str(key: String, def: String) = prefs.getString(key, def) ?: def
    private fun setStr(key: String, v: String) = prefs.edit().putString(key, v).apply()

    /** Ekran o'lchami (zichlik ko'paytuvchisi): 0.85 / 0.92 / 1.0 / 1.1. Odatiy — ixcham (0.92). */
    var uiScale: Float
        get() {
            // v2.5: eski standart "Ixcham" (0.92) ro'yxatlarni mayda qilardi — bir marta "100%" ga o'tkaziladi.
            if (!prefs.getBoolean("scale_v25", false)) {
                val old = prefs.getFloat("ui_scale", 1.0f)
                val e = prefs.edit().putBoolean("scale_v25", true)
                if (old == 0.92f) e.putFloat("ui_scale", 1.0f) else if (old == 0.85f) e.putFloat("ui_scale", 0.9f)
                e.apply()
            }
            return prefs.getFloat("ui_scale", 1.0f)
        }
        set(v) = prefs.edit().putFloat("ui_scale", v).apply()
    /** Shrift ko'paytuvchisi (telefon sozlamasiga qo'shimcha): 0.9 / 1.0 / 1.15. */
    var fontScale: Float get() = prefs.getFloat("font_scale", 0.9f); set(v) = prefs.edit().putFloat("font_scale", v).apply()
    /** Imlo xatolariga chidamli qidiruv. */
    var fFuzzySearch: Boolean get() = flag("f_fuzzy"); set(v) = setFlag("f_fuzzy", v)
    /** Klaviatura tartibi adashganda ham topish (ЙЦУКЕН/QWERTY). */
    var fLayoutFix: Boolean get() = flag("f_layout_fix"); set(v) = setFlag("f_layout_fix", v)
    /** Qidiruvsiz ro'yxat tartibi: "server" / "name" / "popular". */
    var productSort: String get() = str("product_sort", "server"); set(v) = setStr("product_sort", v)
    /** "+" bosilganda: "plus" — darhol qo'shish, "ask" — miqdor oynasi. */
    var addMode: String get() = str("add_mode", "plus"); set(v) = setStr("add_mode", v)
    var fHaptics: Boolean get() = flag("f_haptics"); set(v) = setFlag("f_haptics", v)
    /** Savdo ekranida "Kelgan narx" almashtirgichi ko'rinsin. */
    var fCostSwitch: Boolean get() = flag("f_cost_switch"); set(v) = setFlag("f_cost_switch", v)
    /** Ilova ochilganda ma'lumot shuncha soatdan eski bo'lsa o'zi yangilansin (0 — o'chiq). */
    var autoSyncHours: Int get() = prefs.getInt("auto_sync_h", 0); set(v) = prefs.edit().putInt("auto_sync_h", v).apply()
    /** Kunlik avtomatik zaxira nusxa (telefon ichida, oxirgi 7 ta). */
    var fAutoBackup: Boolean get() = flag("f_auto_backup", false); set(v) = setFlag("f_auto_backup", v)
    /** Kun yakuni eslatmasi vaqti "HH:mm" ("" — o'chiq). */
    var reminderTime: String get() = str("reminder", ""); set(v) = setStr("reminder", v)
    /** Yuborilmagan buyurtma 30 daqiqadan ko'p tursa bildirishnoma. */
    var fQueueAlert: Boolean get() = flag("f_queue_alert", false); set(v) = setFlag("f_queue_alert", v)
    var lastQueueAlertAt: Long get() = prefs.getLong("queue_alert_at", 0L); set(v) = prefs.edit().putLong("queue_alert_at", v).apply()
    /** PIN (PBKDF2 xeshi va tuzi, base64). Bo'sh — qulf o'chiq. */
    var pinHash: String get() = str("pin_hash", ""); set(v) = setStr("pin_hash", v)
    var pinSalt: String get() = str("pin_salt", ""); set(v) = setStr("pin_salt", v)
    /** Ilova fonda shuncha daqiqa tursa PIN so'raladi (0 — har safar). */
    var lockAfterMin: Int get() = prefs.getInt("lock_after", 1); set(v) = prefs.edit().putInt("lock_after", v).apply()
}
