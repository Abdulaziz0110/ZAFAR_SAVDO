package uz.imsoft.shared.report

/**
 * HTML jadval(lar)ni [Grid] ga aylantiradi (rowspan/colspan hisobga olinadi). Bir nechta tashqi jadval bo'lsa —
 * ketma-ket bitta jadvalga qo'shiladi. Ichki (nested) jadval matni katak matniga qo'shiladi.
 * 1C hisobotlari va 1-ilova serverdan oladigan hisobot HTML'i uchun.
 */
object HtmlTable {
    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>|<!--[\\s\\S]*?-->")
    private val ATTR_SPAN = Regex("(rowspan|colspan)\\s*=\\s*[\"']?(\\d+)", RegexOption.IGNORE_CASE)

    fun read(html: String): Grid {
        val b = Grid.Builder()
        var depth = 0
        var row = -1
        var nextRow = 0
        var col = 0
        var inCell = false
        var cellText = StringBuilder()
        var rowSpan = 1
        var colSpan = 1
        var skipUntil: String? = null

        fun closeCell() {
            if (!inCell) return
            inCell = false
            if (row < 0) return
            while (!b.isFree(row, col)) col++
            val text = clean(cellText.toString())
            val cell = Cell(text, null)
            b.span(row, col, row + rowSpan - 1, col + colSpan - 1, cell)
            col += colSpan
        }

        var pos = 0
        for (m in TAG.findAll(html)) {
            if (skipUntil == null && inCell && m.range.first > pos) cellText.append(html, pos, m.range.first)
            pos = m.range.last + 1
            if (m.value.startsWith("<!--")) continue
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            if (skipUntil != null) {
                if (closing && name == skipUntil) skipUntil = null
                continue
            }
            when (name) {
                "script", "style", "head", "title" -> if (!closing) skipUntil = name
                "table" -> {
                    if (!closing) {
                        depth++
                        if (depth > 1 && inCell) cellText.append(' ')
                    } else {
                        if (depth == 1) { closeCell(); }
                        depth = maxOf(0, depth - 1)
                    }
                }
                "tr" -> if (depth == 1) {
                    closeCell()
                    if (!closing) { row = nextRow; nextRow++; col = 0 }
                } else if (depth > 1 && inCell && closing) cellText.append(' ')
                "td", "th" -> if (depth == 1) {
                    closeCell()
                    if (!closing) {
                        if (row < 0) { row = nextRow; nextRow++; col = 0 }
                        inCell = true
                        cellText = StringBuilder()
                        rowSpan = 1; colSpan = 1
                        for (a in ATTR_SPAN.findAll(m.groupValues[3])) {
                            val v = a.groupValues[2].toIntOrNull()?.coerceIn(1, 500) ?: 1
                            if (a.groupValues[1].equals("rowspan", true)) rowSpan = v else colSpan = v
                        }
                    }
                } else if (depth > 1 && inCell) cellText.append(' ')
                "br" -> if (inCell) cellText.append('\n')
                "p", "div" -> if (inCell && closing) cellText.append('\n')
            }
        }
        closeCell()
        return b.build()
    }

    /** Teglarsiz matn: entity'lar ochiladi, bo'shliqlar ixchamlanadi (yangi qator saqlanadi). */
    fun clean(raw: String): String {
        val s = decodeEntities(raw)
        return s.split('\n').joinToString("\n") { it.replace(Regex("[ \\t\\u00A0\\u202F]+"), " ").trim() }.trim('\n', ' ')
    }

    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val NAMED = mapOf(
        "nbsp" to "\u00A0", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "laquo" to "«", "raquo" to "»", "ndash" to "–", "mdash" to "—", "rsquo" to "’", "lsquo" to "‘", "hellip" to "…", "shy" to "",
    )

    fun decodeEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        return ENTITY.replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> NAMED[e.lowercase()] ?: m.value
            }
        }
    }
}
