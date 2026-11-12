package uz.imsoft.shared.onec

/**
 * v5.2 (A11): 1C'dagi KO'P tovarga birdan amal — nomdagi belgini olib tashlash, nom boshiga/oxiriga
 * matn qo'shish, maydonni o'zgartirish. Hammasi COM orqali (kompyuterda tugma bosish emas), telefon
 * o'chsa ham kompyuter davom etadi.
 *
 * Har amal uch bosqichli: KO'RIB CHIQISH (nechta tovar o'zgaradi va misollar) -> tasdiq -> bajarish.
 * Eski qiymatlar kompyuter jurnalida saqlanadi — bitta tugma bilan qaytariladi.
 */
object BulkOp {
    /** Nomdan matnni olib tashlash yoki almashtirish. */
    const val REPLACE = "repl"
    /** Nomga matn qo'shish (boshiga, oxiriga yoki ko'rsatilgan o'ringa). */
    const val ADD = "add"
    /** Maydonni o'zgartirish (guruh, brend, o'lchov, hajm, shtuk, nac, narxlar). */
    const val FIELD = "field"

    /**
     * Qoldiqni belgilangan songa tushirish — 1C'da "СписанияТоваров" (hisobdan chiqarish) hujjati bilan.
     * Qoldig'i kam bo'lganlar tegilmaydi (oshirish uchun kirim hujjati kerak) va ro'yxati qaytariladi.
     */
    const val STOCK = "stock"

    val ALL = setOf(REPLACE, ADD, FIELD, STOCK)
}

/** Amal maydonlari ([WriteRequest.fields]). */
object BulkField {
    const val OP = "bop"
    /** REPLACE: topiladigan matn; bir nechtasi "|" bilan ajratiladi. */
    const val FIND = "bfind"
    /** REPLACE: o'rniga qo'yiladigan matn (bo'sh — o'chiriladi). */
    const val TO = "bto"
    /** ADD: qo'shiladigan matn. */
    const val TEXT = "btxt"
    /** ADD: "start", "end" yoki son (indeks). */
    const val WHERE = "bwhere"
    /** FIELD: qaysi maydon ([GoodsField] kaliti) va qiymati. */
    const val KEY = "bkey"
    const val VALUE = "bval"
    /** STOCK: qoldiq shu songa tushiriladi. */
    const val QTY = "bqty"
    /** Qamrov: guruh nomi (bo'sh — hammasi). */
    const val GROUP = "bgrp"
    /** Qamrov: nomida shu matn bo'lganlar (bo'sh — hammasi). */
    const val MATCH = "bmatch"
    /** Qamrov: aniq kodlar, vergul bilan (bo'sh — hammasi). */
    const val CODES = "bcodes"

    val ALL = setOf(OP, FIND, TO, TEXT, WHERE, KEY, VALUE, QTY, GROUP, MATCH, CODES)

    /** FIELD amali uchun ruxsat etilgan maydonlar (1C kartochkasidagilar). */
    val FIELDS = setOf(
        GoodsField.GROUP, GoodsField.BRAND, GoodsField.UNIT, GoodsField.VOLUME, GoodsField.PACK,
        GoodsField.MARKUP, GoodsField.SCALE, GoodsField.P1, GoodsField.P2, GoodsField.P3, GoodsField.P4, GoodsField.COST,
    )
}

/** Nom o'zgarishini hisoblash — telefon ham, kompyuter ham AYNAN shu qoidani ishlatadi. */
object BulkText {
    /** Bir nechta topiladigan matn "|" bilan beriladi (masalan "()|gr|-"). Bo'sh qismlar e'tiborsiz. */
    fun needles(find: String): List<String> = find.split('|').map { it }.filter { it.isNotEmpty() }

    /** [find] dagi har bir matnni [to] ga almashtiradi va ortiqcha bo'shliqlarni yig'ishtiradi. */
    fun replace(name: String, find: String, to: String): String {
        var s = name
        for (n in needles(find)) s = s.replace(n, to)
        return tidy(s)
    }

    /**
     * [where]: "start" — boshiga, "end" — oxiriga, son — shu o'ringa (belgilar soni bo'yicha).
     * Chegaradan tashqari son oxiriga qo'yadi.
     */
    fun add(name: String, text: String, where: String): String {
        if (text.isEmpty()) return name
        val s = when (where) {
            "start" -> text + name
            "end" -> name + text
            else -> {
                val i = where.toIntOrNull()?.coerceIn(0, name.length) ?: name.length
                name.substring(0, i) + text + name.substring(i)
            }
        }
        return tidy(s)
    }

    /** Ketma-ket bo'shliqlar bittaga, chetlari kesiladi. */
    fun tidy(s: String): String {
        val sb = StringBuilder(s.length)
        var space = false
        for (c in s) {
            val sp = c == ' ' || c == '\u00A0'
            if (sp) { space = true; continue }
            if (space && sb.isNotEmpty()) sb.append(' ')
            space = false
            sb.append(c)
        }
        return sb.toString().trim()
    }
}

/** Qamrov: qaysi tovarlarga tegadi. */
class BulkScope(val group: String = "", val match: String = "", val codes: List<String> = emptyList()) {
    fun covers(code: String, name: String, groupName: String): Boolean {
        if (codes.isNotEmpty() && code !in codes) return false
        if (group.isNotEmpty() && !groupName.equals(group, ignoreCase = true)) return false
        if (match.isNotEmpty() && !name.contains(match, ignoreCase = true)) return false
        return true
    }

    val isAll: Boolean get() = group.isEmpty() && match.isEmpty() && codes.isEmpty()

    companion object {
        fun of(f: Map<String, String>) = BulkScope(
            f[BulkField.GROUP].orEmpty().trim(),
            f[BulkField.MATCH].orEmpty().trim(),
            f[BulkField.CODES].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}

/** Ko'rib chiqish natijasi: nechta tovar o'zgaradi va birinchi misollar. */
class BulkPreview(val changed: Int, val total: Int, val samples: List<Triple<String, String, String>>) {
    val nothing: Boolean get() = changed == 0
}

object BulkPlan {
    const val SAMPLES = 20

    /** Tekshiruv: amal to'g'ri tuzilganmi. null — to'g'ri. */
    fun problem(f: Map<String, String>): String? {
        return when (f[BulkField.OP]) {
            BulkOp.REPLACE -> if (BulkText.needles(f[BulkField.FIND].orEmpty()).isEmpty()) "find" else null
            BulkOp.ADD -> when {
                f[BulkField.TEXT].orEmpty().isEmpty() -> "text"
                f[BulkField.WHERE].orEmpty().let { it != "start" && it != "end" && it.toIntOrNull() == null } -> "where"
                else -> null
            }
            BulkOp.FIELD -> when {
                f[BulkField.KEY] !in BulkField.FIELDS -> "key"
                f[BulkField.VALUE].orEmpty().isBlank() -> "value"
                else -> null
            }
            BulkOp.STOCK -> {
                val q = f[BulkField.QTY]?.let { uz.imsoft.shared.NumText.parseOrNull(it) }
                if (q == null || q < 0.0) "qty" else null
            }
            else -> "op"
        }
    }

    /** Nomga tegadigan amallar uchun yangi nom (tegmasa — eskisi). */
    fun newName(f: Map<String, String>, name: String): String = when (f[BulkField.OP]) {
        BulkOp.REPLACE -> BulkText.replace(name, f[BulkField.FIND].orEmpty(), f[BulkField.TO].orEmpty())
        BulkOp.ADD -> BulkText.add(name, f[BulkField.TEXT].orEmpty(), f[BulkField.WHERE].orEmpty())
        else -> name
    }

    /**
     * Telefondagi katalog bo'yicha ko'rib chiqish. [items] — (kod, nom, guruh nomi).
     * Maydon o'zgartirishda nom o'zgarmaydi — qamrovga tushgan hamma tovar sanaladi.
     */
    fun preview(f: Map<String, String>, items: List<Triple<String, String, String>>): BulkPreview {
        val scope = BulkScope.of(f)
        val inScope = items.filter { (code, name, grp) -> scope.covers(code, name, grp) }
        if (f[BulkField.OP] == BulkOp.FIELD || f[BulkField.OP] == BulkOp.STOCK) {
            val v = if (f[BulkField.OP] == BulkOp.STOCK) f[BulkField.QTY].orEmpty() else f[BulkField.VALUE].orEmpty()
            return BulkPreview(inScope.size, items.size, inScope.take(SAMPLES).map { Triple(it.first, it.second, v) })
        }
        val changed = ArrayList<Triple<String, String, String>>()
        for ((code, name, _) in inScope) {
            val next = newName(f, name)
            if (next != name && next.isNotEmpty()) changed += Triple(code, name, next)
        }
        return BulkPreview(changed.size, items.size, changed.take(SAMPLES))
    }
}
