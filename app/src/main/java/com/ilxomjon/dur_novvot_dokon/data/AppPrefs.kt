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
}
