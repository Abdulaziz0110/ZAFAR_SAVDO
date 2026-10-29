package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 2-ilova bilan ulashish sozlamalari. Firebase "secret" Android Keystore bilan shifrlangan holda saqlanadi
 * va ekranga to'liq chiqarilmaydi. Mavjud sozlamalarga tegmaydi (alohida fayl).
 */
class ShareStore(private val context: Context) {
    private val prefs: SharedPreferences = create()

    private fun create(): SharedPreferences = try {
        build()
    } catch (e: Exception) {
        Log.w("ShareStore", "qayta yaratilmoqda", e)
        context.deleteSharedPreferences(FILE)
        build()
    }

    private fun build(): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun s(k: String) = prefs.getString(k, "").orEmpty()
    private fun put(k: String, v: String) = prefs.edit().putString(k, v).apply()

    var dbUrl: String get() = s("db_url"); set(v) = put("db_url", v)
    var secret: String get() = s("secret"); set(v) = put("secret", v)
    var clientId: String get() = s("client_id"); set(v) = put("client_id", v)
    var clientName: String get() = s("client_name"); set(v) = put("client_name", v)
    var readKey: String get() = s("read_key"); set(v) = put("read_key", v)
    var queueKey: String get() = s("queue_key"); set(v) = put("queue_key", v)

    /** Yangi ma'lumot (import, narx/qarz yangilanishi) bo'lsa o'zi yuborsin. */
    var autoPublish: Boolean get() = prefs.getBoolean("auto", true); set(v) = prefs.edit().putBoolean("auto", v).apply()
    /** 2-ilova "Yangilash" bosganda (15 daqiqagacha) narx/qarzni serverdan yangilab yuborsin. */
    var answerRequests: Boolean get() = prefs.getBoolean("answer", true); set(v) = prefs.edit().putBoolean("answer", v).apply()
    var rulesOk: Boolean get() = prefs.getBoolean("rules_ok", false); set(v) = prefs.edit().putBoolean("rules_ok", v).apply()
    var dirty: Boolean get() = prefs.getBoolean("dirty", false); set(v) = prefs.edit().putBoolean("dirty", v).apply()
    var lastPublishAt: Long get() = prefs.getLong("last_pub", 0L); set(v) = prefs.edit().putLong("last_pub", v).apply()
    var lastPublishError: String get() = s("last_err"); set(v) = put("last_err", v)
    var lastRequestServed: Long get() = prefs.getLong("last_req", 0L); set(v) = prefs.edit().putLong("last_req", v).apply()

    val isConfigured: Boolean get() = dbUrl.isNotBlank() && secret.isNotBlank() && clientId.isNotBlank() && readKey.isNotBlank()

    private companion object { const val FILE = "share_secure" }
}
