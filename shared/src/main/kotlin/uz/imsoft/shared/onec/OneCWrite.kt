package uz.imsoft.shared.onec

import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

/**
 * Admin -> kompyuter -> 1C yozish navbati (v3.2, "Tasdiqlab kiritish"). Admin faqat egasi tasdiqlagan so'rovni qo'yadi;
 * kompyuter kunlik zaxiradan keyin 1C'ga kiritadi, jurnalga yozadi va natijani qaytaradi. Bekor qilish — hujjatga
 * o'chirish belgisi (1C'da qaytarib olsa bo'ladi).
 *
 * Bulutda: w/{inbox}/{id} = {at, k, by, p} (p — JSON satr); natija: wr/{inbox}/{id} = {at, ok, st, no, ref, e}.
 */
object WriteKind {
    /** Tovar kirimi (Поступления). */
    const val RECEIPT = "in"
    /** Ta'minotchiga to'lov (ЮкБерувчигаТулов). */
    const val SUPPLIER_PAY = "sp"
    /** Ta'minotchiga qaytarish (Возврат_товара_поставщику). */
    const val SUPPLIER_RETURN = "rs"
    /** Mijoz qaytargan tovar (Возврат_товара_от_клиента). */
    const val CLIENT_RETURN = "rc"
    /** Tovarga shtrix kod (Справочник.Штрих). */
    const val BARCODE = "bc"
    /** Oldingi yozuvni bekor qilish (o'chirish belgisi). */
    const val UNDO = "undo"

    val ALL = listOf(RECEIPT, SUPPLIER_PAY, SUPPLIER_RETURN, CLIENT_RETURN, BARCODE, UNDO)
}

data class WriteLine(val name: String, val code: String, val qty: Double, val priceSom: Double, val priceUsd: Double) {
    val sumSom: Double get() = qty * priceSom
    val sumUsd: Double get() = qty * priceUsd
}

data class WriteRequest(
    val id: String,
    val kind: String,
    val at: Long,
    /** Kontragent (ta'minotchi yoki mijoz) nomi va 1C kodi. */
    val party: String = "",
    val partyCode: String = "",
    val note: String = "",
    val lines: List<WriteLine> = emptyList(),
    /** To'lov: naqd so'm, naqd $, plastik so'm, plastik $. */
    val cashSom: Double = 0.0,
    val cashUsd: Double = 0.0,
    val cardSom: Double = 0.0,
    val cardUsd: Double = 0.0,
    /** Shtrix: tovar va kod. */
    val barcode: String = "",
    /** Bekor qilinadigan so'rov id'si. */
    val target: String = "",
) {
    val totalSom: Double get() = if (kind == WriteKind.SUPPLIER_PAY) cashSom + cardSom else lines.sumOf { it.sumSom }
    val totalUsd: Double get() = if (kind == WriteKind.SUPPLIER_PAY) cashUsd + cardUsd else lines.sumOf { it.sumUsd }

    /** Tekshiruv: bo'sh bo'lsa — to'g'ri; aks holda xato kaliti. */
    fun problem(): String? = when (kind) {
        WriteKind.RECEIPT, WriteKind.SUPPLIER_RETURN, WriteKind.CLIENT_RETURN -> when {
            party.isBlank() -> "party"
            lines.isEmpty() -> "lines"
            lines.any { it.qty <= 0.0 || (it.priceSom < 0.0 || it.priceUsd < 0.0) || it.name.isBlank() } -> "line"
            else -> null
        }
        WriteKind.SUPPLIER_PAY -> when {
            party.isBlank() -> "party"
            cashSom < 0 || cashUsd < 0 || cardSom < 0 || cardUsd < 0 -> "amount"
            cashSom + cashUsd + cardSom + cardUsd <= 0.0 -> "amount"
            else -> null
        }
        WriteKind.BARCODE -> when {
            lines.isEmpty() && partyCode.isBlank() && party.isBlank() -> "party"
            barcode.length !in 4..40 || !barcode.all { it.isLetterOrDigit() } -> "barcode"
            else -> null
        }
        WriteKind.UNDO -> if (target.isBlank()) "target" else null
        else -> "kind"
    }
}

data class WriteResult(val id: String, val at: Long, val ok: Boolean, val state: String, val number: String, val ref: String, val error: String) {
    companion object {
        const val DONE = "done"
        const val ERROR = "err"
        const val UNDONE = "undone"
        const val SKIPPED = "skip"
    }
}

object WriteCodec {
    const val VERSION = 1
    private val ALPHA = "abcdefghijkmnpqrstuvwxyz23456789".toCharArray()

    fun newId(now: Long = System.currentTimeMillis(), random: java.util.Random = java.security.SecureRandom()): String =
        "w" + now.toString() + String(CharArray(6) { ALPHA[random.nextInt(ALPHA.size)] })

    fun payload(r: WriteRequest): String = Json.write(
        linkedMapOf<String, Any?>(
            "v" to VERSION, "c" to r.party, "cc" to r.partyCode, "n" to r.note.take(300),
            "l" to r.lines.map { listOf(it.name, it.code, it.qty, it.priceSom, it.priceUsd) },
            "ns" to r.cashSom, "nu" to r.cashUsd, "ps" to r.cardSom, "pu" to r.cardUsd, "bc" to r.barcode, "t" to r.target,
        ),
    )

    fun toCloud(r: WriteRequest, by: String): Map<String, Any?> =
        linkedMapOf("at" to CloudDb.SERVER_TIME, "k" to r.kind, "by" to by.take(80), "p" to payload(r), "c" to r.at)

    @Suppress("UNCHECKED_CAST")
    fun fromCloud(id: String, value: Any?): WriteRequest? {
        val o = JObj(value as? Map<String, Any?> ?: return null)
        val kind = o.str("k")
        if (kind !in WriteKind.ALL) return null
        val p = runCatching { JObj.parse(o.str("p")) }.getOrNull() ?: return null
        fun d(x: Any?) = (x as? Number)?.toDouble() ?: 0.0
        val lines = p.arr("l").mapNotNull { x ->
            val l = x as? List<Any?> ?: return@mapNotNull null
            WriteLine(l.getOrNull(0)?.toString().orEmpty(), l.getOrNull(1)?.toString().orEmpty(), d(l.getOrNull(2)), d(l.getOrNull(3)), d(l.getOrNull(4)))
        }
        return WriteRequest(
            id, kind, o.long("c").takeIf { it > 0 } ?: o.long("at"), p.str("c"), p.str("cc"), p.str("n"), lines,
            p.num("ns"), p.num("nu"), p.num("ps"), p.num("pu"), p.str("bc"), p.str("t"),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun resultFromCloud(id: String, value: Any?): WriteResult? {
        val o = JObj(value as? Map<String, Any?> ?: return null)
        val st = o.str("st").ifEmpty { if (o.bool("ok")) WriteResult.DONE else WriteResult.ERROR }
        return WriteResult(id, o.long("at"), o.bool("ok"), st, o.str("no"), o.str("ref"), o.str("e"))
    }
}
