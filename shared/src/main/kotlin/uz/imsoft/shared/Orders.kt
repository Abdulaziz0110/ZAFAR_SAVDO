package uz.imsoft.shared

/**
 * 2-ilova -> 1-ilova buyurtma so'rovi ("shu savdoni yana olib keling"). Bu SAVDO EMAS: 1-ilova egasi ko'rib chiqib,
 * o'zi savatchaga oladi va odatdagidek rasmiylashtiradi. Holatlar: yuborildi -> 1-ilova ko'rdi -> savatchaga olindi
 * (yoki rad etildi). "Serverda tasdiqlandi" degan holat YO'Q.
 *
 * Bulutda: s/{readKey}/ord/{id} = {at, v, src, n, l} (l — JSON satr). 2-ilova faqat yangi yozuv qo'sha oladi
 * (qoidalar: bor yozuvni o'zgartirib/o'chirib bo'lmaydi). Holat (st) ni 1-ilova secret bilan yozadi.
 */
data class OrderLine(val name: String, val qty: Double, val productId: String = "")

data class OrderRequest(
    val id: String,
    val at: Long,
    val note: String,
    /** Qaysi 1C hujjatidan (LedgerDoc.key) — bo'sh bo'lishi mumkin. */
    val source: String,
    val lines: List<OrderLine>,
    val status: Status = Status.SENT,
    val statusAt: Long = 0L,
    /** Kim yubordi (ishchi/mijoz ismi yoki telefoni). */
    val by: String = "",
) {
    enum class Status(val code: String) {
        SENT(""), SEEN("seen"), TAKEN("taken"), REJECTED("rejected");
        companion object { fun of(c: String) = entries.firstOrNull { it.code == c && c.isNotEmpty() } ?: SENT }
    }
}

object OrderCodec {
    const val VERSION = 1
    const val MAX_LINES = 200
    const val MAX_NOTE = 300

    private val ALPHA = "abcdefghijkmnpqrstuvwxyz23456789".toCharArray()

    /** Vaqt bo'yicha saralanadigan id: "o" + ms + 4 tasodifiy belgi. */
    fun newId(now: Long = System.currentTimeMillis(), random: java.util.Random = java.security.SecureRandom()): String =
        "o" + now.toString() + String(CharArray(4) { ALPHA[random.nextInt(ALPHA.size)] })

    fun linesJson(lines: List<OrderLine>): String =
        Json.write(lines.take(MAX_LINES).map { listOf(it.name.take(200), it.qty, it.productId.take(64)) })

    /** 2-ilova yozadigan obyekt ("at" — server vaqti). */
    fun toCloud(note: String, source: String, lines: List<OrderLine>, by: String = ""): Map<String, Any?> = linkedMapOf<String, Any?>(
        "at" to CloudDb.SERVER_TIME, "v" to VERSION, "src" to source.take(200), "n" to note.take(MAX_NOTE), "l" to linesJson(lines),
    ).apply { if (by.isNotBlank()) put("by", by.take(80)) }

    @Suppress("UNCHECKED_CAST")
    fun fromCloud(id: String, value: Any?): OrderRequest? {
        val m = value as? Map<String, Any?> ?: return null
        val o = JObj(m)
        val lines = runCatching {
            (Json.parse(o.str("l")) as? List<Any?>).orEmpty().mapNotNull { x ->
                val l = x as? List<Any?> ?: return@mapNotNull null
                val name = l.getOrNull(0)?.toString().orEmpty()
                val qty = (l.getOrNull(1) as? Number)?.toDouble() ?: 0.0
                if (name.isBlank() || qty <= 0.0) null else OrderLine(name, qty, l.getOrNull(2)?.toString().orEmpty())
            }
        }.getOrDefault(emptyList())
        val st = o.obj("st")
        return OrderRequest(
            id, o.long("at"), o.str("n"), o.str("src"), lines,
            OrderRequest.Status.of(st?.str("s").orEmpty()), st?.long("at") ?: 0L, o.str("by"),
        )
    }

    /** 1-ilova yozadigan holat. */
    fun statusValue(s: OrderRequest.Status): Map<String, Any?> = linkedMapOf("s" to s.code, "at" to CloudDb.SERVER_TIME)
}
