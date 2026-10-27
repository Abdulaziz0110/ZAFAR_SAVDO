package com.ilxomjon.dur_novvot_dokon.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig

/** SQL Server login/paroli Android Keystore bilan shifrlangan holda saqlanadi. */
class SecureStore(private val context: Context) {
    private val prefs: SharedPreferences = create()

    private fun create(): SharedPreferences {
        return try {
            build()
        } catch (e: Exception) {
            // Keystore buzilgan bo'lsa (masalan zaxiradan tiklangan) — fayl qayta yaratiladi.
            Log.w(TAG, "Secure prefs qayta yaratilmoqda", e)
            context.deleteSharedPreferences(FILE)
            build()
        }
    }

    private fun build(): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Foydalanuvchi serverni kamida bir marta saqlaganmi (aks holda oldindan belgilangan qiymatlar ishlaydi). */
    val hasStored: Boolean get() = prefs.contains("host")

    /** Saqlanmagan/bo'sh maydonlar uchun oldindan belgilangan qiymat ([ServerConfig.DEFAULT]) ishlatiladi. */
    var serverConfig: ServerConfig
        get() {
            val d = ServerConfig.DEFAULT
            fun s(key: String, def: String) = prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: def
            return ServerConfig(
                host = s("host", d.host), database = s("db", d.database), login = s("login", d.login),
                password = prefs.getString("password", null)?.takeIf { it.isNotEmpty() } ?: d.password,
                remoteHost = prefs.getString("remote", "").orEmpty(),
            )
        }
        set(v) {
            prefs.edit().putString("host", v.host.trim()).putString("db", v.database.trim())
                .putString("login", v.login.trim()).putString("password", v.password)
                .putString("remote", v.remoteHost.trim()).apply()
        }

    /** Foydalanuvchi o'zi saqlagan parol ("" — ilovaga o'rnatilgani ishlatiladi). Ekranga CHIQARILMAYDI. */
    fun storedPassword(): String = prefs.getString("password", null).orEmpty()

    /** Parol saqlanganmi (o'zi kiritgan yoki ilovaga o'rnatilgan). Faqat "bor/yo'q" ko'rsatish uchun. */
    val hasPassword: Boolean get() = serverConfig.password.isNotEmpty()

    private companion object {
        const val FILE = "secure_server"
        const val TAG = "SecureStore"
    }
}
