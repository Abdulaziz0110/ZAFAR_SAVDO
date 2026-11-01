package uz.imsoft.shared.report

import uz.imsoft.shared.Fold

/**
 * v3.8: server "Hisobot" (tur 5) javobi — "Yuk beruvchi va Mijozlar bilan solishtirma dalolatnoma" (akt sverki):
 *   № | Operatsiyalar | [mijoz nomi: Dt (So'm) | Kt (So'm) ...] | Izohlar
 *   "... sanada kun boshiga qoldiq summasi" | "... umumiy aylanma summasi" | "... kun oxiriga qarzdorlik qoldiq summasi"
 * Har hujjat — bitta yozuv (tovar qatorlari yo'q): Dt — savdo (qarz oshadi), Kt — to'lov/qaytarish (qarz kamayadi).
 * Sana va hujjat turi "Operatsiyalar" matnidan olinadi.
 */
object AktParser {
    private fun key(text: String): String = Fold.key(text).replace(" ", "").replace('h', 'x').replace('q', 'k')
    private fun kw(w: String) = key(w)
    private fun String.has(w: String) = contains(kw(w))
    private fun String.starts(w: String) = startsWith(kw(w))

    private class Head(val row: Int, val opCol: Int)

    private fun findHead(grid: Grid): Head? {
        for (r in 0 until minOf(grid.rows.size, 60)) {
            for (c in 0 until grid.width) {
                val cell = grid[r, c] ?: continue
                val k = key(cell.text)
                if (k.starts("operatsi") || k.starts("operasi") || k.starts("xujjat") || k.starts("dokument")) {
                    // Pastda (shu yoki keyingi 3 qatorda) Dt/Kt ustunlari bo'lishi kerak
                    val hasDtKt = (r..minOf(r + 3, grid.rows.size - 1)).any { rr ->
                        (0 until grid.width).any { cc -> grid[rr, cc]?.let { x -> isDt(key(x.text)) || isKt(key(x.text)) } == true }
                    }
                    if (hasDtKt) return Head(r, c)
                }
            }
        }
        return null
    }

    private fun isDt(k: String) = k.starts("dt") || k.starts("debet")
    private fun isKt(k: String) = k.starts("kt") || k.starts("kredit")
    private fun isUsd(text: String): Boolean {
        val t = text.lowercase()
        return t.contains('$') || t.contains("usd") || t.contains("dollar") || t.contains("вал") || t.contains("val)") || t.contains("y.e") || t.contains("у.е")
    }

    fun looksLike(grid: Grid): Boolean = findHead(grid) != null

    private val PERIOD = Regex("(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?)\\D{1,40}?(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?)")

    fun parse(grid: Grid): CardReport {
        val h = findHead(grid) ?: throw CardParser.NotACard("'Operatsiyalar' va Dt/Kt ustunlari topilmadi")
        val dt = ArrayList<Pair<Int, Boolean>>()
        val kt = ArrayList<Pair<Int, Boolean>>()
        var sub = h.row
        for (r in h.row..minOf(h.row + 3, grid.rows.size - 1)) {
            for (c in 0 until grid.width) {
                val cell = grid[r, c] ?: continue
                if (c > 0 && grid[r, c - 1] === cell) continue
                val k = key(cell.text)
                when {
                    isDt(k) -> { dt += c to isUsd(cell.text); sub = maxOf(sub, r) }
                    isKt(k) -> { kt += c to isUsd(cell.text); sub = maxOf(sub, r) }
                }
            }
        }
        if (dt.isEmpty() && kt.isEmpty()) throw CardParser.NotACard("Dt/Kt ustunlari topilmadi")
        val headCells = (h.row..sub).flatMap { r -> grid.rows[r].filterNotNull() }.toHashSet()
        var noteCol = -1
        for (c in 0 until grid.width) {
            val k = key(grid[h.row, c]?.text.orEmpty())
            if (k.starts("izox") || k.starts("kommentari") || k.starts("primechan")) { noteCol = c; break }
        }
        // Mijoz nomi — Dt/Kt ustida turgan sarlavha katagi
        val firstVal = (dt + kt).minOf { it.first }
        val clientName = (h.row until sub).firstNotNullOfOrNull { r ->
            grid[r, firstVal]?.text?.trim()?.takeIf { it.isNotEmpty() && !isDt(key(it)) && !isKt(key(it)) }
        }
        val opCell0 = grid[h.row, h.opCol]

        fun money(r: Int, cols: List<Pair<Int, Boolean>>, own: Cell?): Money2 {
            var s = 0.0
            var u = 0.0
            for ((c, usd) in cols) {
                val cell = grid[r, c] ?: continue
                if (cell === own || cell in headCells) continue
                val v = Num.of(cell) ?: continue
                if (usd) u += v else s += v
            }
            return Money2(s, u)
        }

        val entries = ArrayList<LedgerEntry>()
        var opening: Pair<Long, Money2>? = null
        var closing: Pair<Long, Money2>? = null
        var from: Long? = null
        var to: Long? = null
        var skipped = 0
        var lastOp: Cell? = null
        for (r in sub + 1 until grid.rows.size) {
            val op = grid[r, h.opCol] ?: continue
            if (op === lastOp || op === opCell0 || op in headCells) continue
            lastOp = op
            val text = op.text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) continue
            val d = money(r, dt, op)
            val k2 = money(r, kt, op)
            val k = key(text)
            when {
                k.has("boshiga") || k.has("nachalo") || k.has("nachal") -> {
                    Stamp.parse(text)?.let { opening = Stamp.dayStart(it) to (d - k2) }
                }
                k.has("oxiriga") || k.has("konets") || k.has("konech") -> {
                    Stamp.parse(text)?.let { closing = it to (d - k2) }
                }
                k.has("aylanma") || k.has("oborot") -> {
                    PERIOD.find(text)?.let { m ->
                        val a = Stamp.parse(m.groupValues[1])
                        val b = Stamp.parse(m.groupValues[2])
                        if (a != null && b != null) { from = Stamp.dayStart(minOf(a, b)); to = Stamp.dayEnd(maxOf(a, b)) }
                    }
                }
                else -> {
                    val note = if (noteCol >= 0) grid[r, noteCol]?.takeIf { it !== op }?.text?.replace('\n', ' ')?.trim().orEmpty() else ""
                    val stamp = Stamp.parse(text) ?: Stamp.parse(note)
                    if (stamp == null || (d.isZero && k2.isZero)) { if (!d.isZero || !k2.isZero) skipped++; continue }
                    val kind = classify(k, d, k2)
                    val v = LinkedHashMap<Col, Money2>()
                    when (kind) {
                        EntryKind.SALE -> v[Col.SUM] = d
                        EntryKind.PAYMENT -> v[Col.PAYMENT] = k2
                        EntryKind.RETURN -> v[Col.RETURN] = k2
                        EntryKind.PAYMENT_RETURN -> v[Col.PAYMENT_RETURN] = d
                        EntryKind.DISCOUNT -> v[Col.DISCOUNT] = k2
                        EntryKind.OTHER -> { if (!d.isZero) v[Col.SUM] = d; if (!k2.isZero) v[Col.PAYMENT] = k2 }
                    }
                    // Savdo/qaytarishda tovar qatori yo'q: izoh hujjat matniga qo'shiladi (tovar sifatida emas)
                    val goods = kind == EntryKind.SALE || kind == EntryKind.RETURN
                    val kindText = if (goods && note.isNotEmpty()) "$text · $note" else text
                    entries += LedgerEntry(stamp, kind, kindText, if (goods) "" else note, 0.0, v)
                }
            }
        }
        if (from == null || to == null) {
            // Sarlavhadagi "Санадан: ... санагача: ..." (server ba'zan ikkalasini bir xil yozadi — shuning uchun oxirgi chora)
            for (r in 0 until h.row) {
                val line = grid.rows[r].filterNotNull().distinct().joinToString(" ") { it.text }
                val m = PERIOD.find(line) ?: continue
                val a = Stamp.parse(m.groupValues[1]) ?: continue
                val b = Stamp.parse(m.groupValues[2]) ?: continue
                from = Stamp.dayStart(minOf(a, b))
                to = Stamp.dayEnd(maxOf(a, b, closing?.first ?: 0L, entries.maxOfOrNull { it.stamp } ?: 0L))
                break
            }
        }
        val warnings = ArrayList<String>()
        if (entries.isEmpty()) warnings += "Davrda hujjat yo'q"
        return CardReport(
            clientName, from, to, entries.sortedBy { it.stamp }, listOf("Operatsiyalar", "Dt", "Kt"), warnings, skipped, 0,
            (dt + kt).any { it.second }, opening, closing,
        )
    }

    private fun classify(k: String, dt: Money2, kt: Money2): EntryKind = when {
        (k.has("tolov") || k.has("oplat")) && (k.has("kaytar") || k.has("vozvrat")) -> EntryKind.PAYMENT_RETURN
        k.has("kaytar") || k.has("vozvrat") -> if (!kt.isZero || dt.isZero) EntryKind.RETURN else EntryKind.PAYMENT_RETURN
        k.has("chegirma") || k.has("skidk") -> EntryKind.DISCOUNT
        k.has("tolov") || k.has("oplat") || k.has("kassa") || k.has("prixod") || k.has("kirim") || k.has("click") || k.has("plastik") -> EntryKind.PAYMENT
        k.has("savdo") || k.has("sotuv") || k.has("realiz") || k.has("prodaj") || k.has("otgruz") || k.has("chikim") || k.has("rasxod") -> EntryKind.SALE
        !dt.isZero && kt.isZero -> EntryKind.SALE
        !kt.isZero && dt.isZero -> EntryKind.PAYMENT
        else -> EntryKind.OTHER
    }
}

/** v3.8: server hisoboti — avval "Mijoz kartochkasi", bo'lmasa akt sverki. */
object ServerReport {
    fun parse(grid: Grid): CardReport = try {
        CardParser.parse(grid)
    } catch (e: CardParser.NotACard) {
        if (AktParser.looksLike(grid)) AktParser.parse(grid) else throw e
    }
}
