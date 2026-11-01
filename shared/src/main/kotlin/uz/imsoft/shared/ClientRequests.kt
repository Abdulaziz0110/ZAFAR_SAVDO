package uz.imsoft.shared

/**
 * 2-ilova (Mijoz) -> Admin so'rovlari (v3.2): to'lov kiritish va chekni tuzatish. Mijoz faqat yangi yozuv qo'sha oladi,
 * holatni Admin (secret bilan) yozadi. "Tasdiqlandi" faqat Admin serverga yozib ID olgandan keyin qo'yiladi.
 *
 * Bulutda: s/{readKey}/pay/{id} = {at, by, s, u, m, n}; s/{readKey}/ed/{id} = {at, by, d, l, n};
 * holat: .../{id}/st = {s, at, sid, e}.
 */
data class ReqStatus(val state: String = SENT, val at: Long = 0L, val serverId: String = "", val error: String = "") {
    val isFinal: Boolean get() = state == OK || state == REJECTED

    companion object {
        const val SENT = ""
        const val SEEN = "seen"
        /** Admin tasdiqladi, serverga yozilmoqda (hali tasdiqlangan EMAS). */
        const val ACCEPTED = "acc"
        const val OK = "ok"
        const val REJECTED = "no"
    }
}

data class PayRequest(
    val id: String,
    val at: Long,
    val by: String,
    val som: Double,
    val usd: Double,
    /** n — naqd, p — plastik, c — click/o'tkazma. */
    val method: String,
    val note: String,
    val status: ReqStatus = ReqStatus(),
)

data class EditLine(val name: String, val qty: Double, val priceSom: Double, val priceUsd: Double)

data class EditRequest(
    val id: String,
    val at: Long,
    val by: String,
    /** LedgerDoc.key */
    val doc: String,
    val lines: List<EditLine>,
    val note: String,
    val status: ReqStatus = ReqStatus(),
)

object ClientReqCodec {
    const val MAX_NOTE = 300
    private val ALPHA = "abcdefghijkmnpqrstuvwxyz23456789".toCharArray()

    fun newId(prefix: Char, now: Long = System.currentTimeMillis(), random: java.util.Random = java.security.SecureRandom()): String =
        prefix + now.toString() + String(CharArray(5) { ALPHA[random.nextInt(ALPHA.size)] })

    fun payToCloud(som: Double, usd: Double, method: String, note: String, by: String): Map<String, Any?> = linkedMapOf(
        "at" to CloudDb.SERVER_TIME, "by" to by.take(80), "s" to som, "u" to usd, "m" to method.take(2), "n" to note.take(MAX_NOTE),
    )

    fun editToCloud(doc: String, lines: List<EditLine>, note: String, by: String): Map<String, Any?> = linkedMapOf(
        "at" to CloudDb.SERVER_TIME, "by" to by.take(80), "d" to doc.take(200), "n" to note.take(MAX_NOTE),
        "l" to Json.write(lines.take(200).map { listOf(it.name.take(200), it.qty, it.priceSom, it.priceUsd) }),
    )

    fun statusToCloud(s: ReqStatus): Map<String, Any?> = linkedMapOf("s" to s.state, "at" to CloudDb.SERVER_TIME, "sid" to s.serverId, "e" to s.error.take(200))

    @Suppress("UNCHECKED_CAST")
    private fun status(v: Any?): ReqStatus {
        val o = JObj(v as? Map<String, Any?> ?: return ReqStatus())
        return ReqStatus(o.str("s"), o.long("at"), o.str("sid"), o.str("e"))
    }

    @Suppress("UNCHECKED_CAST")
    fun payFromCloud(id: String, v: Any?): PayRequest? {
        val o = JObj(v as? Map<String, Any?> ?: return null)
        val s = o.num("s")
        val u = o.num("u")
        if (s < 0 || u < 0 || s + u <= 0) return null
        return PayRequest(id, o.long("at"), o.str("by"), s, u, o.str("m").ifEmpty { "n" }, o.str("n"), status(o.map["st"]))
    }

    @Suppress("UNCHECKED_CAST")
    fun editFromCloud(id: String, v: Any?): EditRequest? {
        val o = JObj(v as? Map<String, Any?> ?: return null)
        val doc = o.str("d")
        if (doc.isBlank()) return null
        val lines = runCatching {
            (Json.parse(o.str("l")) as? List<Any?>).orEmpty().mapNotNull { x ->
                val l = x as? List<Any?> ?: return@mapNotNull null
                val name = l.getOrNull(0)?.toString().orEmpty()
                fun d(i: Int) = (l.getOrNull(i) as? Number)?.toDouble() ?: 0.0
                if (name.isBlank() || d(1) < 0) null else EditLine(name, d(1), d(2), d(3))
            }
        }.getOrDefault(emptyList())
        return EditRequest(id, o.long("at"), o.str("by"), doc, lines, o.str("n"), status(o.map["st"]))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> all(raw: Any?, parse: (String, Any?) -> T?): List<T> =
        (raw as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> parse(k, v) }
}
