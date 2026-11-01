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
    /** v3.12: mijozdan to'lov (Оплата_Клента). */
    const val CLIENT_PAY = "cp"
    /** v3.12: kassa kirim (КассаПриход) va chiqim — xarajat (КассаРасход). */
    const val CASH_IN = "ci"
    const val CASH_OUT = "ce"
    /** v3.12: tovar kartochkasi — narx 1-4, kirim narxi, PLU, nomi (Справочник.Товары). */
    const val GOODS_EDIT = "ge"
    /** v3.12: yangi tovar (namuna tovardan nusxa: guruh, o'lchov, valyuta shundan). */
    const val NEW_GOODS = "ng"

    val ALL = listOf(RECEIPT, SUPPLIER_PAY, SUPPLIER_RETURN, CLIENT_RETURN, BARCODE, UNDO, CLIENT_PAY, CASH_IN, CASH_OUT, GOODS_EDIT, NEW_GOODS)
    val MONEY = setOf(SUPPLIER_PAY, CLIENT_PAY, CASH_IN, CASH_OUT)
    val GOODS = setOf(GOODS_EDIT, NEW_GOODS)
}

/** v3.12: tovar kartochkasi maydonlari ([WriteRequest.fields]). Narxlar [CUR] valyutasida ("s" so'm, "u" $). */
object GoodsField {
    const val P1 = "p1"
    const val P2 = "p2"
    const val P3 = "p3"
    const val P4 = "p4"
    /** Kirim narxi. */
    const val COST = "pin"
    const val PLU = "plu"
    const val NAME = "nm"
    const val CUR = "cur"
    const val BARCODE = "bc"
    val PRICES = listOf(P1, P2, P3, P4, COST)
    val ALL = setOf(P1, P2, P3, P4, COST, PLU, NAME, CUR, BARCODE)
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
    /** v3.12: Click (so'm, $) va chegirma (mijoz to'lovida). */
    val clickSom: Double = 0.0,
    val clickUsd: Double = 0.0,
    val discountSom: Double = 0.0,
    val discountUsd: Double = 0.0,
    /** v3.12: kassa moddasi (Справочник.Операции) nomi va kodi. */
    val op: String = "",
    val opCode: String = "",
    /** v3.12: tovar kartochkasi maydonlari ([GoodsField]). */
    val fields: Map<String, String> = emptyMap(),
) {
    val totalSom: Double get() = if (kind in WriteKind.MONEY) cashSom + cardSom + clickSom else lines.sumOf { it.sumSom }
    val totalUsd: Double get() = if (kind in WriteKind.MONEY) cashUsd + cardUsd + clickUsd else lines.sumOf { it.sumUsd }

    private fun goodsProblem(): String? {
        if (party.isBlank() && partyCode.isBlank()) return "party"
        val f = fields
        if (kind == WriteKind.GOODS_EDIT && GoodsField.PRICES.none { it in f } && GoodsField.PLU !in f && GoodsField.NAME !in f) return "empty"
        for (k in GoodsField.PRICES) {
            val v = f[k] ?: continue
            val d = v.toDoubleOrNull() ?: return "price"
            if (d < 0 || d.isNaN()) return "price"
        }
        f[GoodsField.PLU]?.let { v -> val n = v.toIntOrNull(); if (n == null || n !in 0..99999) return "plu" }
        f[GoodsField.NAME]?.let { if (it.trim().length !in 2..150) return "name" }
        if (kind == WriteKind.NEW_GOODS && f[GoodsField.NAME].isNullOrBlank()) return "name"
        f[GoodsField.BARCODE]?.takeIf { it.isNotEmpty() }?.let { if (it.length !in 4..40 || !it.all { c -> c.isLetterOrDigit() && c.code < 128 }) return "barcode" }
        return null
    }

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
        WriteKind.CLIENT_PAY -> {
            val v = listOf(cashSom, cashUsd, cardSom, cardUsd, clickSom, clickUsd, discountSom, discountUsd)
            when {
                party.isBlank() && partyCode.isBlank() -> "party"
                v.any { it < 0 } || v.sum() <= 0.0 -> "amount"
                else -> null
            }
        }
        WriteKind.CASH_IN, WriteKind.CASH_OUT -> {
            val v = listOf(cashSom, cashUsd, cardSom, cardUsd, clickSom)
            when {
                op.isBlank() && opCode.isBlank() -> "op"
                v.any { it < 0 } || v.sum() <= 0.0 -> "amount"
                else -> null
            }
        }
        WriteKind.GOODS_EDIT, WriteKind.NEW_GOODS -> goodsProblem()
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
            "ks" to r.clickSom, "ku" to r.clickUsd, "ds" to r.discountSom, "du" to r.discountUsd, "op" to r.op, "opc" to r.opCode,
            "f" to LinkedHashMap(r.fields.filterKeys { it in GoodsField.ALL }),
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
        val f = (p.map["f"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> if (k in GoodsField.ALL && v != null) k to v.toString() else null }.toMap()
        return WriteRequest(
            id, kind, o.long("c").takeIf { it > 0 } ?: o.long("at"), p.str("c"), p.str("cc"), p.str("n"), lines,
            p.num("ns"), p.num("nu"), p.num("ps"), p.num("pu"), p.str("bc"), p.str("t"),
            p.num("ks"), p.num("ku"), p.num("ds"), p.num("du"), p.str("op"), p.str("opc"), f,
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun resultFromCloud(id: String, value: Any?): WriteResult? {
        val o = JObj(value as? Map<String, Any?> ?: return null)
        val st = o.str("st").ifEmpty { if (o.bool("ok")) WriteResult.DONE else WriteResult.ERROR }
        return WriteResult(id, o.long("at"), o.bool("ok"), st, o.str("no"), o.str("ref"), o.str("e"))
    }
}
