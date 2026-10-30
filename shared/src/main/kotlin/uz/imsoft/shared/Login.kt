package uz.imsoft.shared

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * "Do'kon kodi" — Firebase bazasining qisqa nomi (2-ilovaga kirishda bir marta yoziladi):
 *   "dokon-1a2b3"                -> https://dokon-1a2b3-default-rtdb.firebaseio.com
 *   "dokon-1a2b3@europe-west1"   -> https://dokon-1a2b3-default-rtdb.europe-west1.firebasedatabase.app
 * To'liq manzil ham qabul qilinadi.
 */
object ShopCode {
    private val US = Regex("^https://([a-z0-9-]+)-default-rtdb\\.firebaseio\\.com$")
    private val REGION = Regex("^https://([a-z0-9-]+)-default-rtdb\\.([a-z0-9-]+)\\.firebasedatabase\\.app$")

    fun fromUrl(url: String): String {
        val u = CloudDb.normalizeUrl(url)
        US.matchEntire(u)?.let { return it.groupValues[1] }
        REGION.matchEntire(u)?.let { return it.groupValues[1] + "@" + it.groupValues[2] }
        return u.removePrefix("https://")
    }

    fun toUrl(code0: String): String? {
        val code = code0.trim().lowercase().removeSuffix("/")
        if (code.isEmpty()) return null
        if (code.contains('.')) return CloudDb.normalizeUrl(code).takeIf { CloudDb.looksLikeFirebase(it) }
        val parts = code.split('@')
        val project = parts[0].removeSuffix("-default-rtdb")
        if (!Regex("^[a-z0-9][a-z0-9-]{3,62}$").matches(project)) return null
        return if (parts.size == 2 && Regex("^[a-z0-9-]{3,30}$").matches(parts[1])) "https://$project-default-rtdb.${parts[1]}.firebasedatabase.app"
        else if (parts.size == 1) "https://$project-default-rtdb.firebaseio.com" else null
    }
}

/** Telefon raqami: faqat raqamlar, O'zbekiston uchun 998 bilan (901234567 -> 998901234567). */
object Phone {
    fun normalize(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            d.length == 9 -> "998$d"
            d.length == 12 && d.startsWith("998") -> d
            d.length == 13 && d.startsWith("0998") -> d.drop(1)
            else -> d
        }
    }

    fun isValid(raw: String): Boolean = normalize(raw).length in 9..15

    /** "+998 90 123 45 67" */
    fun pretty(p: String): String {
        val d = normalize(p)
        return if (d.length == 12 && d.startsWith("998")) "+998 ${d.substring(3, 5)} ${d.substring(5, 8)} ${d.substring(8, 10)} ${d.substring(10)}" else "+$d"
    }
}

/**
 * 2-ilovaga telefon raqami + parol bilan kirish (Firebase Auth'siz, qo'shimcha sozlashsiz).
 *
 * 1-ilova har bir ishchi/mijoz telefoni uchun bulutga `a/{H}` yozuvini qo'yadi:
 *   H = sha256("savdo-login-v1|" + baza + "|" + telefon) (40 belgi) — telefonni bilmasdan topib bo'lmaydi;
 *   ichida mijozning o'qish kaliti AES-GCM bilan shifrlangan, kalit = PBKDF2-HMAC-SHA256(parol, tuz, 120 000).
 * Parol noto'g'ri bo'lsa shifr ochilmaydi. Parol bulutga va 2-ilovadan tashqariga chiqmaydi.
 * Telefon o'chirilsa — 1-ilova mijoz kalitini almashtiradi va qolganlarni qayta yozadi.
 */
object LoginRecord {
    const val ITER = 120_000
    const val MIN_PASSWORD = 6

    data class Grant(
        val readKey: String,
        val queueKey: String,
        val clientName: String,
        val memberName: String,
        val clientId: String,
    )

    fun key(dbUrl: String, phone: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val h = md.digest(("savdo-login-v1|" + CloudDb.normalizeUrl(dbUrl) + "|" + Phone.normalize(phone)).toByteArray(Charsets.UTF_8))
        return h.joinToString("") { "%02x".format(it) }.take(40)
    }

    /** PBKDF2-HMAC-SHA256 (Android 6+ da ham ishlaydi — tizim provayderiga bog'liq emas). */
    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(String(password).toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val out = ByteArray(bytes)
        var block = 1
        var pos = 0
        while (pos < bytes) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(t.size, bytes - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            block++
        }
        return out
    }

    fun encrypt(g: Grant, password: String, random: SecureRandom = SecureRandom(), iterations: Int = ITER): Map<String, Any?> {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val key = pbkdf2(password.toCharArray(), salt, iterations)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val plain = Json.write(linkedMapOf("r" to g.readKey, "q" to g.queueKey, "n" to g.clientName, "m" to g.memberName, "cid" to g.clientId, "v" to 1))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return linkedMapOf("s" to B64.encode(salt), "i" to iterations, "c" to B64.encode(iv + ct), "v" to 1)
    }

    class WrongPassword : Exception("wrong_password")

    @Suppress("UNCHECKED_CAST")
    fun decrypt(record: Any?, password: String): Grant {
        val o = JObj(record as? Map<String, Any?> ?: throw IllegalArgumentException("no_record"))
        val salt = B64.decode(o.str("s"))
        val iter = o.long("i", ITER.toLong()).toInt().coerceIn(1_000, 2_000_000)
        val blob = B64.decode(o.str("c"))
        if (blob.size < 12 + 16) throw IllegalArgumentException("bad_record")
        val key = pbkdf2(password.toCharArray(), salt, iter)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        val plain = try { c.doFinal(blob.copyOfRange(12, blob.size)) } catch (e: Exception) { throw WrongPassword() }
        val p = JObj.parse(String(plain, Charsets.UTF_8))
        return Grant(p.str("r"), p.str("q"), p.str("n"), p.str("m"), p.str("cid"))
    }
}
