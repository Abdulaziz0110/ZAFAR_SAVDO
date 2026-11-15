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
    /** v4.0: dollar kursi (Константы.Курс + kurslar tarixi). Maydon: fields["rate"]. */
    const val RATE = "rt"
    /** v4.1: yangi kontragent (mijoz yoki ta'minotchi) — Справочник.Контрагенты. [WriteRequest.fields]: [PartyField]. */
    const val PARTY_NEW = "nk"
    /** v4.1: kontragentni tahrirlash (nom, telefon, manzil, region). */
    const val PARTY_EDIT = "ke"
    /** v4.1: tovarni hisobdan chiqarish (СписанияТоваров): qatorlar — tovar va miqdor; tannarx 1C qoldig'idan. */
    const val WRITE_OFF = "wo"
    /**
     * v5.2 (A5): tayyor 1C hujjatidagi tovarlarni o'zgartirish (tarixdagi savdoni tuzatish).
     * Maydonlar: [DocField.KIND] (r/m/z/q/b), [DocField.NUMBER], [DocField.STAMP]; qatorlar — yangi tovarlar.
     */
    const val DOC_EDIT = "de"
    /**
     * v5.2 (A11): KO'P tovarga birdan amal (nomdagi belgini olib tashlash, matn qo'shish, maydonni
     * o'zgartirish). Maydonlar: [BulkField]. Kompyuter COM orqali bajaradi, eski qiymatlar jurnalda qoladi.
     */
    const val BULK = "bk"
    /**
     * v5.3: kassadagi savdo oynasining "Paneli" (tez tovar tugmalari; 1C'da Константы.тов1 … тов336).
     * Qatorlar: miqdor = tugma raqami, tovar nomi va kodi; ikkalasi bo'sh — tugma bo'shatiladi.
     */
    const val PANEL = "pn"

    val ALL = listOf(RECEIPT, SUPPLIER_PAY, SUPPLIER_RETURN, CLIENT_RETURN, BARCODE, UNDO, CLIENT_PAY, CASH_IN, CASH_OUT, GOODS_EDIT, NEW_GOODS, RATE,
        PARTY_NEW, PARTY_EDIT, WRITE_OFF, DOC_EDIT, BULK, PANEL)
    val MONEY = setOf(SUPPLIER_PAY, CLIENT_PAY, CASH_IN, CASH_OUT)
    val GOODS = setOf(GOODS_EDIT, NEW_GOODS)
}

/** v5.2 (A5): tahrirlanadigan hujjatni ko'rsatuvchi maydonlar ([WriteRequest.fields]). */
object DocField {
    /** Hujjat turi: r, m, z, q, b (1C tarixidagi kabi). */
    const val KIND = "dk"
    const val NUMBER = "no"
    /** yyyyMMddHHmmss. */
    const val STAMP = "dt"
    val ALL = setOf(KIND, NUMBER, STAMP)
    val EDITABLE = setOf("r", "m", "z", "q", "b")
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
    const val RATE = "rate"

    // v5.1: yangi tovar — 1C kartochkasidagi maydonlar (kodni 1C o'zi beradi)
    /** Guruh (Справочник.ГруппаТовара). */
    const val GROUP = "grp"
    /** Brend (Справочник.Бренд). */
    const val BRAND = "brd"
    /** O'lchov birligi (Справочник.ЕдИзм). */
    const val UNIT = "unit"
    /** Hajmi (Обьём). */
    const val VOLUME = "vol"
    /** Upakovkadagi dona (Шт). */
    const val PACK = "pack"
    /** Ustama foiz (Нац %). */
    const val MARKUP = "nac"
    /** Tovar turi: [TYPE_GOODS] yoki [TYPE_WEIGHED] (Перечисление.ТипТовара). */
    const val TYPE = "typ"
    const val TYPE_GOODS = "g"
    const val TYPE_WEIGHED = "w"
    /** Tarozi (Справочник.Весы). */
    const val SCALE = "scl"
    /** "1" bo'lsa shtrix kodni 1C tomoni o'zi yaratadi. */
    const val AUTO_BARCODE = "abc"

    /** v5.3: qoldiq me'yori (НормаОст) — odatda 1. */
    const val MIN_STOCK = "nrm"

    val PRICES = listOf(P1, P2, P3, P4, COST)
    val CARD = setOf(GROUP, BRAND, UNIT, VOLUME, PACK, MARKUP, TYPE, SCALE, MIN_STOCK)
    val ALL = setOf(P1, P2, P3, P4, COST, PLU, NAME, CUR, BARCODE, RATE, AUTO_BARCODE) + CARD
}

/**
 * v4.1: kontragent maydonlari. [GROUP]: "c" — mijozlar (Клиенты), "s" — ta'minotchilar (Поставщики).
 * Tahrirlashda faqat yuborilgan maydon o'zgaradi.
 */
object PartyField {
    const val NAME = "nm"
    const val PHONE = "tel"
    const val PHONE2 = "tel2"
    const val ADDRESS = "adr"
    const val REGION = "reg"
    const val GROUP = "grp"
    const val CLIENTS = "c"
    const val SUPPLIERS = "s"
    /** v5.1: 1C kartochkasidagi qolgan maydonlar. */
    const val INN = "inn"
    const val LIMIT = "lim"
    val ALL = setOf(NAME, PHONE, PHONE2, ADDRESS, REGION, GROUP, INN, LIMIT)
    private val PHONE_RE = Regex("[+0-9 ()-]{5,20}")

    fun problem(kind: String, party: String, f: Map<String, String>): String? {
        if (kind == WriteKind.PARTY_EDIT && party.isBlank()) return "party"
        val name = f[NAME]?.trim()
        if (kind == WriteKind.PARTY_NEW && name.isNullOrEmpty()) return "name"
        if (name != null && name.length !in 2..150) return "name"
        for (k in listOf(PHONE, PHONE2)) f[k]?.trim()?.takeIf { it.isNotEmpty() }?.let { if (!PHONE_RE.matches(it)) return "phone" }
        f[ADDRESS]?.let { if (it.length > 250) return "address" }
        f[GROUP]?.let { if (it != CLIENTS && it != SUPPLIERS) return "group" }
        f[INN]?.takeIf { it.isNotEmpty() }?.let { if (!it.all { c -> c.isDigit() } || it.length !in 9..14) return "inn" }
        f[LIMIT]?.takeIf { it.isNotEmpty() }?.let { v -> val d = v.toDoubleOrNull(); if (d == null || d < 0 || d > 1e12) return "limit" }
        if (kind == WriteKind.PARTY_EDIT && f.keys.none { it in setOf(NAME, PHONE, PHONE2, ADDRESS, REGION, INN, LIMIT) }) return "empty"
        return null
    }
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
        // v5.1: yangi tovarda namuna ixtiyoriy — namuna bo'lmasa guruh va o'lchov birligi majburiy
        if (kind == WriteKind.NEW_GOODS) {
            if (party.isBlank() && partyCode.isBlank()) {
                if (fields[GoodsField.GROUP].isNullOrBlank()) return "group"
                if (fields[GoodsField.UNIT].isNullOrBlank()) return "unit"
            }
        } else if (party.isBlank() && partyCode.isBlank()) {
            return "party"
        }
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
        // v5.1: kartochka maydonlari
        for (k in listOf(GoodsField.VOLUME, GoodsField.PACK, GoodsField.MARKUP, GoodsField.MIN_STOCK)) {
            val v = f[k]?.takeIf { it.isNotEmpty() } ?: continue
            val d = v.toDoubleOrNull() ?: return k
            if (d < 0 || d.isNaN() || d > 1_000_000) return k
        }
        for (k in listOf(GoodsField.GROUP, GoodsField.BRAND, GoodsField.UNIT, GoodsField.SCALE)) {
            f[k]?.takeIf { it.isNotEmpty() }?.let { if (it.trim().length > 150) return k }
        }
        f[GoodsField.TYPE]?.let { if (it != GoodsField.TYPE_GOODS && it != GoodsField.TYPE_WEIGHED) return "type" }
        if (f[GoodsField.TYPE] == GoodsField.TYPE_WEIGHED && (f[GoodsField.PLU]?.toIntOrNull() ?: 0) <= 0) return "plu"
        if (f[GoodsField.AUTO_BARCODE] == "1" && !f[GoodsField.BARCODE].isNullOrEmpty()) return "barcode"
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
        WriteKind.PANEL -> when {
            lines.isEmpty() -> "lines"
            lines.any { it.qty != Math.floor(it.qty) || it.qty < 1.0 || it.qty > Panel.SLOTS } -> "line"
            lines.map { it.qty }.distinct().size != lines.size -> "line"
            else -> null
        }
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
        WriteKind.RATE -> fields[GoodsField.RATE]?.toDoubleOrNull()?.takeIf { it > 0 && it < 1_000_000 }?.let { null } ?: "rate"
        WriteKind.PARTY_NEW, WriteKind.PARTY_EDIT -> PartyField.problem(kind, party, fields)
        WriteKind.WRITE_OFF -> when {
            lines.isEmpty() -> "lines"
            lines.any { it.qty <= 0.0 || it.name.isBlank() } -> "line"
            else -> null
        }
        WriteKind.BULK -> BulkPlan.problem(fields)
        WriteKind.DOC_EDIT -> when {
            fields[DocField.KIND] !in DocField.EDITABLE -> "kind"
            fields[DocField.NUMBER].isNullOrBlank() -> "number"
            (fields[DocField.STAMP]?.length ?: 0) < 8 -> "stamp"
            lines.isEmpty() -> "lines"
            lines.any { it.qty <= 0.0 || it.name.isBlank() || it.priceSom < 0.0 || it.priceUsd < 0.0 } -> "line"
            else -> null
        }
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
            "f" to LinkedHashMap(r.fields.filterKeys { it in GoodsField.ALL || it in PartyField.ALL || it in DocField.ALL || it in BulkField.ALL }),
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
        val f = (p.map["f"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> if ((k in GoodsField.ALL || k in PartyField.ALL || k in DocField.ALL || k in BulkField.ALL) && v != null) k to v.toString() else null }.toMap()
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


/**
 * v5.3: kassadagi savdo oynasining "Paneli" — tez sotiladigan tovarlar tugmalari.
 * 1C'da 336 ta joy (8 sahifa x 42 tugma), har biri bitta tovar yoki bo'sh.
 */
class Panel(val slots: List<Slot>) {
    class Slot(val code: String, val name: String) {
        val isEmpty: Boolean get() = code.isEmpty() && name.isEmpty()
    }

    fun page(index: Int): List<Slot> = slots.drop(index * PAGE).take(PAGE)

    /** Paneldagi tovar kodlari (takrorlarsiz). */
    val codes: Set<String> get() = slots.mapNotNullTo(HashSet()) { it.code.takeIf { c -> c.isNotEmpty() } }

    /** Bo'sh joylar (1 dan boshlab raqamlangan), [fromPage] sahifasidan boshlab. */
    fun freeSlots(fromPage: Int = 0): List<Int> {
        val all = slots.indices.filter { slots[it].isEmpty }.map { it + 1 }
        val start = fromPage * PAGE
        return all.filter { it > start } + all.filter { it <= start }
    }

    companion object {
        const val SLOTS = 336
        const val PAGE = 42
        const val PAGES = SLOTS / PAGE
        val EMPTY = Slot("", "")

        /** Kompyuterdan kelgan ro'yxat: [[kod, nom], ...]. Uzunligi noto'g'ri bo'lsa — null (panel kelmagan). */
        fun parse(list: List<Any?>): Panel? {
            if (list.size != SLOTS) return null
            return Panel(list.map { x ->
                val l = x as? List<*>
                val code = l?.getOrNull(0)?.toString()?.trim().orEmpty().let { c -> c.removeSuffix(".0") }
                val name = l?.getOrNull(1)?.toString()?.trim().orEmpty()
                if (name.isEmpty()) EMPTY else Slot(code, name)
            })
        }

        /** O'zgarishlar (tugma raqami -> yangi tovar yoki bo'sh) -> 1C'ga yuboriladigan qatorlar. */
        fun lines(changes: Map<Int, Slot>): List<WriteLine> =
            changes.entries.sortedBy { it.key }.map { (slot, s) -> WriteLine(s.name, s.code, slot.toDouble(), 0.0, 0.0) }

        /**
         * Tavsiya: eng ko'p sotilgan, lekin panelda YO'Q tovarlar — bo'sh joylarga ketma-ket.
         * [top] — kodlar, ko'p sotilgandan kamiga. @return tugma raqami -> tovar kodi.
         */
        fun suggest(panel: Panel, top: List<String>, fromPage: Int = 0, limit: Int = PAGE): Map<Int, String> {
            val have = panel.codes
            val free = panel.freeSlots(fromPage)
            val out = LinkedHashMap<Int, String>()
            var i = 0
            for (code in top) {
                if (i >= free.size || out.size >= limit) break
                if (code in have || code in out.values) continue
                out[free[i]] = code
                i++
            }
            return out
        }
    }
}
