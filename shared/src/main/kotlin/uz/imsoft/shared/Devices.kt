package uz.imsoft.shared

import java.security.MessageDigest

/**
 * v4.0 "Kalitsiz ishlash": Agent va Mijoz ilovasi ochilishi bilan ishlaydi.
 *
 * Admin bir marta "Do'kon kodi"ni (IMSHOP1) GitHub secret'ga (SHOP_CODE) qo'yadi. Build paytida:
 *  - Agent APK ichiga — butun kod (baza manzili, agentlar ko'prigi kaliti, qurilmalar kaliti);
 *  - Mijoz APK ichiga — faqat baza manzili va qurilmalar kaliti (IMSDEV1): agentlar katalogi (kirim narxi bor) Mijozga bormaydi.
 *
 * Har telefon o'zini ro'yxatga qo'yadi: d/{qurilmalar kaliti}/{qurilma id}/i = {m, r, v, vc, os, at}.
 * Admin ko'radi va biriktiradi: .../x = {s: ok|blocked, a: [agent id, ism, boshliq, telefon] yoki l: Mijoz ulanish kodi, n: yorliq}.
 * x ga faqat Admin (secret bilan) yozadi — qoidalar telefonga ruxsat bermaydi.
 */
object DeviceCodes {
    const val SHOP_PREFIX = "IMSHOP1:"
    const val DEV_PREFIX = "IMSDEV1:"

    const val ROLE_AGENT = "agent"
    const val ROLE_ADMIN = "admin"
    const val ROLE_MIJOZ = "mijoz"
    const val ROLE_WEB = "web"

    data class Shop(val dbUrl: String, val agentKey: String, val devKey: String) {
        val dev: Dev get() = Dev(dbUrl, devKey)
        val isValid: Boolean get() = CloudDb.looksLikeFirebase(dbUrl) && CloudDb.isKey(agentKey) && CloudDb.isKey(devKey)
    }

    data class Dev(val dbUrl: String, val devKey: String) {
        val isValid: Boolean get() = CloudDb.looksLikeFirebase(dbUrl) && CloudDb.isKey(devKey)
        fun path(deviceId: String, sub: String = ""): String = "d/$devKey/$deviceId" + if (sub.isEmpty()) "" else "/$sub"
    }

    private fun body(text: String, prefix: String): List<String>? {
        val start = text.indexOf(prefix)
        if (start < 0) return null
        val b = text.substring(start + prefix.length).takeWhile { (it.code < 128 && it.isLetterOrDigit()) || it == '-' || it == '_' }
        if (b.isEmpty()) return null
        return runCatching { String(B64.decode(b), Charsets.UTF_8).split('\n').map { it.trim() } }.getOrNull()
    }

    fun encodeShop(s: Shop): String =
        SHOP_PREFIX + B64.encode((CloudDb.normalizeUrl(s.dbUrl) + "\n" + s.agentKey + "\n" + s.devKey).toByteArray(Charsets.UTF_8), urlSafe = true, pad = false)

    fun decodeShop(text: String): Shop? {
        val p = body(text, SHOP_PREFIX) ?: return null
        if (p.size < 3) return null
        return Shop(CloudDb.normalizeUrl(p[0]), p[1], p[2]).takeIf { it.isValid }
    }

    fun encodeDev(d: Dev): String =
        DEV_PREFIX + B64.encode((CloudDb.normalizeUrl(d.dbUrl) + "\n" + d.devKey).toByteArray(Charsets.UTF_8), urlSafe = true, pad = false)

    /** Mijoz uchun kod; to'liq do'kon kodi berilsa ham ishlaydi (undan faqat kerakli qismi olinadi). */
    fun decodeDev(text: String): Dev? {
        decodeShop(text)?.let { return it.dev }
        val p = body(text, DEV_PREFIX) ?: return null
        if (p.size < 2) return null
        return Dev(CloudDb.normalizeUrl(p[0]), p[1]).takeIf { it.isValid }
    }

    /** Yangilanish fayllari yo'li (Cloudflare'dagi yashirin papka): build skripti ham xuddi shunday hisoblaydi. */
    fun updateDir(devKey: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(("$devKey|upd").toByteArray(Charsets.UTF_8))
        return h.joinToString("") { "%02x".format(it) }.take(24)
    }
}

/** Admin bergan biriktirish. */
data class DeviceAssignment(
    val status: String,
    /** Agent: [id, ism, boshliq, telefon]. */
    val agent: List<String>,
    /** Mijoz: ulanish kodi (LinkCode). */
    val link: String,
    /** Yorliq: "Alisher", "Dadaxon aka — Sardor". */
    val label: String,
    val at: Long,
) {
    val blocked: Boolean get() = status == BLOCKED
    val agentId: String get() = agent.getOrNull(0).orEmpty()

    fun toMap(): Map<String, Any?> = linkedMapOf("s" to status, "a" to agent, "l" to link, "n" to label, "at" to CloudDb.SERVER_TIME)

    companion object {
        const val OK = "ok"
        const val BLOCKED = "blocked"

        @Suppress("UNCHECKED_CAST")
        fun parse(v: Any?): DeviceAssignment? {
            val o = JObj(v as? Map<String, Any?> ?: return null)
            val a = (o.map["a"] as? List<Any?>).orEmpty().map { it?.toString().orEmpty() }
            return DeviceAssignment(o.str("s").ifEmpty { OK }, a, o.str("l"), o.str("n"), o.long("at"))
        }
    }
}

/** Qurilma haqida (telefon o'zi yozadi). */
data class DeviceInfo(val id: String, val model: String, val role: String, val version: String, val versionCode: Long, val seenAt: Long, val assignment: DeviceAssignment?, val crashAt: Long, val crash: String) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun parse(id: String, v: Any?): DeviceInfo? {
            val m = v as? Map<String, Any?> ?: return null
            val i = JObj(m["i"] as? Map<String, Any?> ?: emptyMap())
            val c = JObj(m["c"] as? Map<String, Any?> ?: emptyMap())
            return DeviceInfo(id, i.str("m"), i.str("r"), i.str("v"), i.long("vc"), i.long("at"), DeviceAssignment.parse(m["x"]), c.long("at"), c.str("t"))
        }
    }
}

/** Telefon tomoni: ro'yxatdan o'tish, biriktirishni o'qish, xato hisobotini yuborish. Tarmoq — chaqiruvchi IO oqimida. */
class DeviceClient(private val dev: DeviceCodes.Dev, val deviceId: String, private val db: CloudDb = CloudDb(dev.dbUrl)) {
    fun register(model: String, role: String, version: String, versionCode: Long, sdk: Int) {
        db.update(dev.path(deviceId, "i"), linkedMapOf("m" to model.take(80), "r" to role, "v" to version.take(20), "vc" to versionCode, "os" to sdk, "at" to CloudDb.SERVER_TIME))
    }

    fun assignment(): DeviceAssignment? = DeviceAssignment.parse(db.get(dev.path(deviceId, "x")))

    fun reportCrash(text: String) {
        db.put(dev.path(deviceId, "c"), linkedMapOf("at" to CloudDb.SERVER_TIME, "t" to text.take(6000)))
    }
}
