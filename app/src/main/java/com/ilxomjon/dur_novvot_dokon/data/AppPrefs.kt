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
        get() = UiTheme.fromCode(prefs.getString("theme", null))
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
}

