package uz.imsoft.mijoz.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.LinkCode
import uz.imsoft.shared.LoginRecord
import uz.imsoft.shared.Phone
import uz.imsoft.shared.ShopCode

/**
 * 2-ilovaga kirish: do'kon kodi + telefon + parol (bir marta so'raladi).
 * Parol shu telefonda Android Keystore bilan shifrlangan holda turadi — 1-ilova kalitni almashtirsa (masalan, boshqa
 * telefon o'chirilganda) ilova o'zi jimgina qayta kiradi. Telefon o'chirilgan bo'lsa — kirish bekor qilinadi.
 */
class LoginRepository(private val context: Context, private val prefs: MijozPrefs) {
    sealed class Failure(msg: String) : Exception(msg) {
        class BadShopCode : Failure("bad_shop")
        class BadPhone : Failure("bad_phone")
        class NoSuchPhone : Failure("no_phone")
        class WrongPassword : Failure("wrong_password")
    }

    enum class Relogin { SAME, CHANGED, REVOKED, OFFLINE, NONE }

    private val mutex = Mutex()
    /** Oxirgi tekshiruvda kalit o'zgarmagan edi — tez-tez qayta tekshirmaymiz (PBKDF2 sekin, zaryad tejaladi). */
    @Volatile private var sameAt = 0L
    private val secure: SharedPreferences? by lazy { create() }

    private fun create(): SharedPreferences? = try {
        build()
    } catch (e: Exception) {
        Log.w("Login", "secure prefs qayta yaratiladi", e)
        runCatching { context.deleteSharedPreferences(FILE) }
        runCatching { build() }.getOrNull()
    }

    private fun build(): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Telefon raqami bilan kirilganmi (aks holda — eski "ulash kodi" bilan yoki umuman ulanmagan). */
    val hasCredentials: Boolean get() = prefs.loginPhone.isNotEmpty() && !secure?.getString(K_PW, null).isNullOrEmpty()

    suspend fun login(shopCode: String, phone: String, password: String): LoginRecord.Grant = mutex.withLock {
        val url = ShopCode.toUrl(shopCode) ?: throw Failure.BadShopCode()
        if (!Phone.isValid(phone)) throw Failure.BadPhone()
        val p = Phone.normalize(phone)
        val g = fetch(url, p, password)
        save(url, p, password, g)
        g
    }

    private suspend fun fetch(url: String, phone: String, password: String): LoginRecord.Grant {
        val rec = withContext(Dispatchers.IO) { CloudDb(url).get("a/" + LoginRecord.key(url, phone)) } ?: throw Failure.NoSuchPhone()
        return withContext(Dispatchers.Default) {
            try {
                LoginRecord.decrypt(rec, password)
            } catch (e: LoginRecord.WrongPassword) {
                throw Failure.WrongPassword()
            }
        }
    }

    private fun save(url: String, phone: String, password: String, g: LoginRecord.Grant) {
        secure?.edit()?.putString(K_PW, password)?.apply()
        prefs.loginPhone = phone
        prefs.memberName = g.memberName
        prefs.loginClientId = g.clientId
        val old = prefs.link
        if (old == null || old.dbUrl != url || old.readKey != g.readKey || old.queueKey != g.queueKey || old.clientName != g.clientName) {
            prefs.setLink(LinkCode(url, g.readKey, g.queueKey, g.clientName))
        }
    }

    /**
     * Saqlangan telefon/parol bilan jimgina qayta kirish (ma'lumot topilmaganda yoki ruxsat berilmaganda chaqiriladi).
     * [Relogin.CHANGED] — yangi kalit olindi, so'rovni qaytarish mumkin.
     */
    suspend fun relogin(force: Boolean = false): Relogin = mutex.withLock {
        val link = prefs.link ?: return@withLock Relogin.NONE
        val pw = secure?.getString(K_PW, null).orEmpty()
        val phone = prefs.loginPhone
        if (phone.isEmpty() || pw.isEmpty()) return@withLock Relogin.NONE
        if (!force && System.currentTimeMillis() - sameAt < 10 * 60_000L) return@withLock Relogin.SAME
        val g = try {
            fetch(link.dbUrl, phone, pw)
        } catch (e: Failure.NoSuchPhone) {
            revoke(); return@withLock Relogin.REVOKED
        } catch (e: Failure.WrongPassword) {
            // 1-ilovada parol almashtirilgan — qayta kirish kerak.
            revoke(); return@withLock Relogin.REVOKED
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock Relogin.OFFLINE
        }
        val changed = g.readKey != link.readKey || g.queueKey != link.queueKey || g.clientName != link.clientName
        save(link.dbUrl, phone, pw, g)
        sameAt = if (changed) 0L else System.currentTimeMillis()
        if (changed) Relogin.CHANGED else Relogin.SAME
    }

    private fun revoke() {
        prefs.setLink(null)
        prefs.revokedAt = System.currentTimeMillis()
        secure?.edit()?.clear()?.apply()
    }

    fun logout() {
        secure?.edit()?.clear()?.apply()
        prefs.loginPhone = ""
        prefs.memberName = ""
        prefs.loginClientId = ""
    }

    private companion object {
        const val FILE = "mijoz_secure"
        const val K_PW = "pw"
    }
}
