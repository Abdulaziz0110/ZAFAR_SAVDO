package uz.imsoft.shared.report

import uz.imsoft.shared.Fold

/** O'qilgan hisobot (ko'rib chiqish oynasi va saqlash uchun). */
class CardReport(
    val clientName: String?,
    /** Hisobot davri (kun boshi/oxiri stamp), hisobot sarlavhasidan; bo'lmasa yozuvlardan. */
    val from: Long?,
    val to: Long?,
    val entries: List<LedgerEntry>,
    /** Tanilgan ustunlar (ko'rsatish uchun). */
    val columns: List<String>,
    val warnings: List<String>,
    /** Sana topilmagani uchun tashlab yuborilgan qatorlar (oraliq jami qatorlari bundan tashqari). */
    val skippedRows: Int,
    val subtotalRows: Int,
    val hasUsdRows: Boolean,
    /** Hisobotdagi "kun boshiga qarzdorlik qoldiq" (stamp, summa: Dt − Kt). */
    val opening: Pair<Long, Money2>? = null,
    /** "kun oxiriga qarzdorlik qoldiq" yoki oxirgi yozuvdagi qoldiq. */
    val closing: Pair<Long, Money2>? = null,
) {
    val periodFrom: Long? get() = from ?: entries.minOfOrNull { it.stamp }?.let { Stamp.dayStart(it) }
    val periodTo: Long? get() = to ?: entries.maxOfOrNull { it.stamp }?.let { Stamp.dayEnd(it) }
}

/**
 * 1C "Mijoz kartochkasi" (va shunga o'xshash) hisobotini o'qiydi. Ustunlarni sarlavha MATNI bo'yicha topadi
 * (tartibi va soni boshqacha bo'lsa ham ishlaydi):
 *   Sana | Xarakat | Izoxlar | Soni | Narxi | Summa | Chegirma | To'lov | Qaytarish | ... | Qoldiq
 * Sarlavha ostida "So'm" va "$" qatorlari bo'lsa — har yozuv ikki qatordan iborat (birinchisi so'm, ikkinchisi dollar).
 * Kunlik oraliq jami qatorlari ("27.10.2026 0:00:00" + jami) avtomatik tashlab yuboriladi.
 */
object CardParser {
    class NotACard(message: String) : Exception(message)

    private enum class Role { DATE, KIND, PRODUCT, QTY, VALUE }

    private class Group(val role: Role, val col: Col?, val label: String, val cols: MutableList<Int>, val origin: Cell)

    fun parse(grid: Grid): CardReport {
        val header = findHeader(grid) ?: throw NotACard("Jadval sarlavhasi (Sana, Xarakat, Izoxlar, Soni, Summa...) topilmadi")
        val hdr = grid.rows[header]
        val groups = ArrayList<Group>()
        val usedCols = HashSet<Col>()
        for (c in 0 until grid.width) {
            val cell = hdr.getOrNull(c) ?: continue
            if (cell.text.isBlank()) continue
            val existing = groups.firstOrNull { it.origin === cell }
            if (existing != null) { existing.cols += c; continue }
            val (role, col) = classifyHeader(cell.text)
            if (role == null) {
                groups += Group(Role.VALUE, null, label(cell.text), mutableListOf(c), cell)
                continue
            }
            if (role != Role.VALUE && groups.any { it.role == role }) {
                groups += Group(Role.VALUE, null, label(cell.text), mutableListOf(c), cell)
                continue
            }
            if (col != null && !usedCols.add(col)) {
                groups += Group(Role.VALUE, null, label(cell.text), mutableListOf(c), cell)
                continue
            }
            groups += Group(role, col, label(cell.text), mutableListOf(c), cell)
        }
        val dateG = groups.firstOrNull { it.role == Role.DATE } ?: throw NotACard("'Sana' ustuni topilmadi")
        val kindG = groups.firstOrNull { it.role == Role.KIND }
        val prodG = groups.firstOrNull { it.role == Role.PRODUCT }
        val qtyG = groups.firstOrNull { it.role == Role.QTY }
        val valueGroups = groups.filter { it.role == Role.VALUE }
        val warnings = ArrayList<String>()
        if (prodG == null) warnings += "'Izoxlar' (tovar) ustuni topilmadi"
        if (valueGroups.none { it.col == Col.SUM }) warnings += "'Summa' ustuni topilmadi"

        // Valyuta qatorlari (So'm / $) sarlavha ostida
        val currencies = ArrayList<Boolean>() // true = USD
        var r = header + 1
        val headerCells = hdr.filterNotNull().toHashSet()
        while (r < grid.rows.size && r <= header + 4) {
            val row = grid.rows[r]
            val own = row.filterNotNull().filter { it !in headerCells && !it.isEmpty }.distinct()
            if (own.isEmpty()) {
                if (row.all { it == null || it in headerCells || it.isEmpty }) { r++; if (currencies.isNotEmpty()) break else continue }
            }
            val cur = own.map { currencyOf(it.text) }
            if (own.isNotEmpty() && cur.all { it != null } && cur.distinct().size == 1) {
                currencies += cur[0]!!
                r++
            } else break
        }
        if (currencies.isEmpty()) currencies += false
        val dataStart = r

        val entries = ArrayList<LedgerEntry>()
        var skipped = 0
        var subtotals = 0
        var opening: Pair<Long, Money2>? = null
        var closing: Pair<Long, Money2>? = null
        var i = dataStart
        while (i < grid.rows.size) {
            val dateCell = grid[i, dateG.cols[0]]
            if (dateCell == null || dateCell.isEmpty || dateCell in headerCells) {
                if (rowHasNumbers(grid, i, valueGroups)) skipped++
                i++
                continue
            }
            // Blok: shu sana katagini bo'lishgan qatorlar (yoki birlashtirilmagan bo'lsa — keyingi bo'sh-sanali qatorlar)
            var end = i + 1
            while (end < grid.rows.size && grid[end, dateG.cols[0]] === dateCell) end++
            if (end == i + 1 && currencies.size > 1) {
                while (end < grid.rows.size && end - i < currencies.size) {
                    val dc = grid[end, dateG.cols[0]]
                    if (dc != null && !dc.isEmpty) break
                    end++
                }
            }
            val kindCell = kindG?.let { grid[i, it.cols[0]] }
            val prodCell = prodG?.let { grid[i, it.cols[0]] }
            val stamp = Stamp.parse(dateCell.text) ?: dateCell.num?.let { Stamp.fromExcel(it) }
            val isSubtotal = (kindCell != null && kindCell === dateCell) || (prodCell != null && prodCell === dateCell) ||
                ((kindCell?.text.isNullOrBlank()) && (prodCell?.text.isNullOrBlank()))
            val dk = key(dateCell.text)
            if (stamp != null && isSubtotal && (dk.has("boshiga") || dk.has("oxiriga") || dk.has("koldik"))) {
                val e = buildEntry(grid, i, end, stamp, "", "", null, valueGroups, currencies)
                if (e.values.containsKey(Col.BALANCE) || e.values.containsKey(Col.CREDIT)) {
                    val m = e.v(Col.BALANCE) - e.v(Col.CREDIT)
                    if (dk.has("boshiga")) opening = Stamp.dayStart(stamp) to m else closing = Stamp.dayEnd(stamp) to m
                }
            }
            when {
                stamp == null -> if (blockHasNumbers(grid, i, end, valueGroups)) skipped++
                isSubtotal -> subtotals++
                else -> entries += buildEntry(grid, i, end, stamp, kindCell?.text.orEmpty(), prodCell?.text.orEmpty(), qtyG, valueGroups, currencies)
            }
            i = end
        }
        if (entries.isEmpty()) warnings += "Birorta ham yozuv o'qilmadi"
        val (client, from, to) = titleInfo(grid, header)
        if (closing == null) entries.lastOrNull { it.values.containsKey(Col.BALANCE) }?.let { closing = it.stamp to (it.v(Col.BALANCE) - it.v(Col.CREDIT)) }
        return CardReport(
            client, from, to, entries,
            groups.map { it.label }, warnings, skipped, subtotals, currencies.contains(true), opening, closing,
        )
    }

    private fun buildEntry(
        grid: Grid, r0: Int, end: Int, stamp: Long, kindText: String, product: String,
        qtyG: Group?, values: List<Group>, currencies: List<Boolean>,
    ): LedgerEntry {
        val qty = qtyG?.let { g -> g.cols.firstNotNullOfOrNull { c -> Num.of(grid[r0, c]) } } ?: 0.0
        val known = HashMap<Col, Money2>()
        val extra = LinkedHashMap<String, Money2>()
        for (g in values) {
            var som = 0.0
            var usd = 0.0
            var any = false
            for (k in 0 until (end - r0)) {
                val r = r0 + k
                val isUsd = currencies.getOrElse(k) { false }
                for (c in g.cols) {
                    val cell = grid[r, c] ?: continue
                    // Ikki qatorga cho'zilgan katak (masalan "Soni") dollar qatorida qayta sanalmasin
                    if (k > 0 && grid[r - 1, c] === cell) continue
                    val v = Num.of(cell) ?: continue
                    if (isUsd) usd += v else som += v
                    any = true
                    break
                }
            }
            if (!any) continue
            val m = Money2(som, usd)
            if (g.col != null) known[g.col] = m else extra[g.label] = m
        }
        val kind = classifyKind(kindText, known)
        return LedgerEntry(stamp, kind, kindText.replace('\n', ' ').trim(), product.replace('\n', ' ').trim(), qty, known, extra)
    }

    private fun rowHasNumbers(grid: Grid, r: Int, groups: List<Group>) =
        groups.any { g -> g.cols.any { c -> Num.of(grid[r, c]) != null } }

    private fun blockHasNumbers(grid: Grid, a: Int, b: Int, groups: List<Group>) = (a until b).any { rowHasNumbers(grid, it, groups) }

    private fun findHeader(grid: Grid): Int? {
        var best = -1
        var bestScore = 0
        for (r in 0 until minOf(grid.rows.size, 120)) {
            val roles = HashSet<Any>()
            var hasDate = false
            for (cell in grid.rows[r].filterNotNull().distinct()) {
                val (role, col) = classifyHeader(cell.text)
                if (role == null) continue
                if (role == Role.DATE) hasDate = true
                roles += col ?: role
            }
            if (hasDate && roles.size >= 3 && roles.size > bestScore) { best = r; bestScore = roles.size }
        }
        return best.takeIf { it >= 0 }
    }

    private fun key(text: String): String = Fold.key(text).replace(" ", "").replace('h', 'x').replace('q', 'k')

    /** Kalit so'zlar ham xuddi shu buklashdan o'tadi ("chegirma" -> "cxegirma"), shuning uchun yozilishi tabiiy qoladi. */
    private val kwCache = HashMap<String, String>()
    private fun kw(w: String): String = kwCache.getOrPut(w) { key(w) }
    private fun String.has(w: String) = contains(kw(w))
    private fun String.starts(w: String) = startsWith(kw(w))
    private fun String.eq(w: String) = this == kw(w)

    private fun classifyHeader(text: String): Pair<Role?, Col?> {
        if (text.length > 60) return null to null
        val k = key(text)
        if (k.isEmpty()) return null to null
        val ret = k.has("kaytar") || k.has("vozvrat")
        // Yetkazib beruvchi tomoni, qarz olish/berish, tovar kirimi — mijoz uchun emas: nomi bilan "qo'shimcha" bo'lib qoladi.
        if (k.has("yukberuvchi") || k.has("postavsh") || k.starts("karzolish") || k.starts("karzgaber") || k.has("kirimi")) return null to null
        return when {
            k.starts("karzi") || k.has("koldik") || k.has("ostatok") || k.has("saldo") || k.starts("dolg") || k.eq("dt") -> Role.VALUE to Col.BALANCE
            k.starts("xakki") || k.has("kredit") || k.eq("kt") -> Role.VALUE to Col.CREDIT
            k.eq("sana") || k.starts("sana") || k.eq("data") || k.starts("datavrem") -> Role.DATE to null
            k.starts("xarakat") || k.starts("operatsi") || k.starts("xujjat") || k.starts("dokument") || k.eq("vid") || k.starts("turi") -> Role.KIND to null
            k.starts("izox") || k.starts("tovar") || k.starts("maxsulot") || k.starts("nomenklatura") ||
                k.starts("naimenovan") || k.eq("nomi") || k.starts("kommentari") -> Role.PRODUCT to null
            k.starts("soni") || k.starts("mikdor") || k.starts("kol") -> Role.QTY to null
            k.has("chegirma") || k.has("skidk") -> Role.VALUE to (if (ret) Col.RETURN_DISCOUNT else Col.DISCOUNT)
            k.has("tolov") || k.has("oplat") -> Role.VALUE to (if (ret) Col.PAYMENT_RETURN else Col.PAYMENT)
            ret -> Role.VALUE to Col.RETURN
            k.starts("narx") || k.starts("sena") || k.starts("tsena") -> Role.VALUE to Col.PRICE
            k.starts("summa") || k.eq("jami") -> Role.VALUE to Col.SUM
            else -> null to null
        }
    }

    private fun label(text: String) = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

    /** "So'm", "сўм", "UZS" -> false; "$", "USD", "Dollar" -> true; boshqasi -> null. */
    private fun currencyOf(text: String): Boolean? {
        val t = text.trim()
        if (t == "$" || t == "\$" || t == "у.е." || t == "y.e.") return true
        return when (Fold.key(t).replace(" ", "")) {
            "som", "sum", "uzs", "sm" -> false
            "usd", "dollar", "doll", "ue", "ye" -> true
            else -> null
        }
    }

    private fun classifyKind(text: String, values: Map<Col, Money2>): EntryKind {
        val k = key(text)
        return when {
            (k.has("tolov") || k.has("oplat")) && (k.has("kaytar") || k.has("vozvrat")) -> EntryKind.PAYMENT_RETURN
            k.has("tolov") || k.has("oplat") || k.has("kassa") -> EntryKind.PAYMENT
            k.has("chegirma") || k.has("skidk") -> EntryKind.DISCOUNT
            k.has("kaytar") || k.has("vozvrat") -> EntryKind.RETURN
            k.has("savdo") || k.has("sotuv") || k.has("realiz") || k.has("prodaj") || k.has("otgruz") || k.has("chikim") -> EntryKind.SALE
            (values[Col.PAYMENT]?.isZero == false) && (values[Col.SUM]?.isZero != false) -> EntryKind.PAYMENT
            (values[Col.RETURN]?.isZero == false) && (values[Col.SUM]?.isZero != false) -> EntryKind.RETURN
            values[Col.SUM]?.isZero == false -> EntryKind.SALE
            else -> EntryKind.OTHER
        }
    }

    private val PERIOD = Regex("(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4})\\s*(?:dan|дан|-|–|—|по|s|c|до|gacha)?\\s*(?:-|–|—|по|до|gacha)?\\s*(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4})")

    /** Sarlavha qatorlaridan: "01.10.2022 dan 29.10.2026 : Dadaxon Aka (TRZ)". */
    private fun titleInfo(grid: Grid, header: Int): Triple<String?, Long?, Long?> {
        for (r in 0 until header) {
            val line = grid.rows[r].filterNotNull().distinct().joinToString(" ") { it.text }.replace('\n', ' ').trim()
            val m = PERIOD.find(line) ?: continue
            val a = Stamp.parse(m.groupValues[1]) ?: continue
            val b = Stamp.parse(m.groupValues[2]) ?: continue
            val after = line.substring(m.range.last + 1).trim().trimStart(':', ' ', '-', '—').trim()
            val name = after.takeIf { it.isNotEmpty() && it.length < 120 }
            return Triple(name, Stamp.dayStart(minOf(a, b)), Stamp.dayEnd(maxOf(a, b)))
        }
        return Triple(null, null, null)
    }
}
