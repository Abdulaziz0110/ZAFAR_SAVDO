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
    /**
     * v5.0.3: dollar kursi — mahalliy (internetdagi bozor kursi yoki qo'lda yozilgani). 1C'dagi kursga qaralmaydi.
     * 0 bo'lsa kurs hali ma'lum emas.
     */
    var localRate: Double
        get() = prefs.getFloat("local_rate", 0f).toDouble()
        set(v) = prefs.edit().putFloat("local_rate", v.toFloat()).putLong("local_rate_at", System.currentTimeMillis()).apply()

    /** Kurs oxirgi marta qachon yangilangani (ms, 0 — hech qachon). */
    val localRateAt: Long get() = prefs.getLong("local_rate_at", 0L)

    /** Kurs qo'lda yozilgan bo'lsa — internetdan o'zi almashtirilmaydi. */
    var localRateManual: Boolean get() = prefs.getBoolean("local_rate_manual", false); set(v) = setFlag("local_rate_manual", v)

    /** v5.0.3: statistikada ajratilgan mijoz (nomi) va rejim: "all" / "only" / "without". */
    var statsClient: String get() = str("stats_client", ""); set(v) = setStr("stats_client", v)
    var statsWho: String get() = str("stats_who", "all"); set(v) = setStr("stats_who", v)

    /** v5.2 (A7): savdo paytida mahsulot ma'lumoti o'zi ochilib tursinmi. */
    var bioAuto: Boolean get() = prefs.getBoolean("bio_auto", false); set(v) = setFlag("bio_auto", v)

    /** v5.1.1: bildirishnoma ruxsati bir marta so'ralganmi (Android 13+). */
    var notifAsked: Boolean get() = prefs.getBoolean("notif_asked", false); set(v) = setFlag("notif_asked", v)

    /** v5.0.4: vidjetdagi 6 ta tugma amallari (kalitlar, tartib bilan). */
    var widgetSlots: List<String>
        get() = str("widget_slots", "").split("|").filter { it.isNotEmpty() }
            .ifEmpty { com.ilxomjon.dur_novvot_dokon.ui.widget.QuickWidget.DEFAULTS }
        set(v) = setStr("widget_slots", v.joinToString("|"))

    /** v5.0.3: bosh sahifadagi 3- va 4-tugma (foydalanuvchi tanlaydi). */
    var homeSlot3: String get() = str("home_slot3", "return"); set(v) = setStr("home_slot3", v)
    var homeSlot4: String get() = str("home_slot4", "report"); set(v) = setStr("home_slot4", v)
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

    // ---- v3.2 ----
    /** Ish vaqti: avtomatik yangilash shu oraliqda tez; tugashidan 1 daqiqa oldin oxirgi to'liq yuklab olish. */
    var workHours: uz.imsoft.shared.sched.WorkHours
        get() = uz.imsoft.shared.sched.WorkHours.decode(prefs.getString("work_hours", null))
        set(v) = prefs.edit().putString("work_hours", v.normalized().encode()).apply()
    /** Avtomatik yangilash (umuman). */
    var autoUpdate: Boolean get() = flag("auto_update", true); set(v) = setFlag("auto_update", v)
    /** Ish vaqtida yangilash oralig'i (daqiqa). */
    var updateMinutes: Int get() = prefs.getInt("update_min", 5); set(v) = prefs.edit().putInt("update_min", v.coerceIn(1, 60)).apply()
    var lastFinalSyncAt: Long get() = prefs.getLong("final_sync_at", 0L); set(v) = prefs.edit().putLong("final_sync_at", v).apply()
    /** Kunlik zaxira (avtomatik, eskisini almashtiradi). */
    var dailyBackup: Boolean get() = flag("daily_backup", true); set(v) = setFlag("daily_backup", v)
    var lastDailyBackupAt: Long get() = prefs.getLong("daily_backup_at", 0L); set(v) = prefs.edit().putLong("daily_backup_at", v).apply()
    var lastDailyBackupError: String get() = str("daily_backup_err", ""); set(v) = setStr("daily_backup_err", v)
}
