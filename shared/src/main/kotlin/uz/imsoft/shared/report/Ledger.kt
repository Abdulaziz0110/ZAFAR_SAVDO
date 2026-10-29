package uz.imsoft.shared.report

/** So'm va dollar juftligi. */
data class Money2(val som: Double = 0.0, val usd: Double = 0.0) {
    val isZero: Boolean get() = som == 0.0 && usd == 0.0
    operator fun plus(o: Money2) = Money2(som + o.som, usd + o.usd)
    operator fun minus(o: Money2) = Money2(som - o.som, usd - o.usd)

    companion object { val ZERO = Money2() }
}

enum class EntryKind(val code: String) {
    SALE("S"), RETURN("R"), PAYMENT("P"), PAYMENT_RETURN("PR"), DISCOUNT("D"), OTHER("O");

    companion object {
        fun fromCode(c: String) = entries.firstOrNull { it.code == c } ?: OTHER
    }
}

/** Hisobot ustunlari (1C "Mijoz kartochkasi"). Noma'lum ustunlar [LedgerEntry.extra] ga nomi bilan tushadi. */
enum class Col(val code: String) {
    PRICE("pr"), SUM("sm"), DISCOUNT("ds"), PAYMENT("py"), RETURN("rt"), RETURN_DISCOUNT("rd"), PAYMENT_RETURN("pt"),
    /** Qoldiq / "Qarzi (Dt)" — mijoz qarzi (hisobot bo'yicha, shu yozuvdan keyin). */
    BALANCE("bl"),
    /** "Xaqqi (Kt)" — bizning mijozdan qarzimiz (oldindan to'lov). */
    CREDIT("cr");

    companion object { fun fromCode(c: String) = entries.firstOrNull { it.code == c } }
}

/**
 * Hisobotning bitta yozuvi. [stamp] — mahalliy vaqt yyyyMMddHHmmss ko'rinishidagi son (vaqt mintaqasidan mustaqil,
 * ikki telefonda bir xil ko'rinadi; masalan 20261027104117).
 */
data class LedgerEntry(
    val stamp: Long,
    val kind: EntryKind,
    val kindText: String,
    val product: String,
    val qty: Double,
    val values: Map<Col, Money2>,
    val extra: Map<String, Money2> = emptyMap(),
) {
    fun v(c: Col): Money2 = values[c] ?: Money2.ZERO
    val day: Long get() = stamp / 1_000_000

    /** Ikki fayldagi bir xil yozuvni aniqlash uchun kalit. */
    fun identity(): String = "$stamp|${kind.code}|$product|$qty|${v(Col.SUM)}|${v(Col.PAYMENT)}"
}

object Stamp {
    /** "27.10.2026 10:41:17", "27.10.2026\n9:02", "27.10.26" -> 20261027104117. Mos kelmasa null. */
    private val RE = Regex("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})(?:\\D{1,3}(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?")

    fun parse(text: String): Long? {
        val m = RE.find(text) ?: return null
        val d = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        var y = m.groupValues[3].toInt()
        if (y < 100) y += 2000
        if (d !in 1..31 || mo !in 1..12 || y !in 1990..2100) return null
        val h = m.groupValues[4].toIntOrNull() ?: 0
        val mi = m.groupValues[5].toIntOrNull() ?: 0
        val s = m.groupValues[6].toIntOrNull() ?: 0
        if (h > 23 || mi > 59 || s > 59) return null
        return of(y, mo, d, h, mi, s)
    }

    fun of(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
        ((((y * 100L + mo) * 100 + d) * 100 + h) * 100 + mi) * 100 + s

    /** Excel sanasi (1900 tizimi, kun soni) -> stamp. */
    fun fromExcel(serial: Double): Long? {
        if (serial < 20000 || serial > 80000) return null
        val days = Math.floor(serial).toLong()
        val secs = Math.round((serial - days) * 86400.0)
        // 1899-12-30 dan kunlar (Excel'ning 1900 kabisa xatosi shu bazada hisobga olingan)
        val epochDay = days - 25569 // 1970-01-01
        val (y, mo, d) = civil(epochDay)
        return of(y, mo, d, (secs / 3600).toInt(), ((secs % 3600) / 60).toInt(), (secs % 60).toInt())
    }

    /** Kunlar (1970-01-01 dan) -> (yil, oy, kun). Howard Hinnant algoritmi. */
    fun civil(z0: Long): Triple<Int, Int, Int> {
        val z = z0 + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
    }

    fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }

    fun year(st: Long) = (st / 10_000_000_000L).toInt()
    fun month(st: Long) = ((st / 100_000_000L) % 100).toInt()
    fun dayOfMonth(st: Long) = ((st / 1_000_000L) % 100).toInt()
    fun hour(st: Long) = ((st / 10_000L) % 100).toInt()
    fun minute(st: Long) = ((st / 100L) % 100).toInt()

    fun dayStart(st: Long) = st / 1_000_000 * 1_000_000
    fun dayEnd(st: Long) = st / 1_000_000 * 1_000_000 + 235959

    fun formatDate(st: Long): String = "%02d.%02d.%04d".format(dayOfMonth(st), month(st), year(st))
    fun formatDateTime(st: Long): String = formatDate(st) + " %02d:%02d".format(hour(st), minute(st))

    /** Kalendar kuniga [days] qo'shadi (stamp'ning sana qismi, vaqt 00:00). */
    fun addDays(st: Long, days: Int): Long {
        val (y, m, d) = civil(daysFromCivil(year(st), month(st), dayOfMonth(st)) + days)
        return of(y, m, d)
    }
}

/** Raqamni hisobotdagi matndan o'qish: "3 740 000", "2,5", "-1 250,50", "11 130 000.00". */
object Num {
    fun parse(text: String): Double? {
        val t = text.replace(' ', ' ').replace(' ', ' ').trim()
        if (t.isEmpty()) return null
        var s = t.filter { it != ' ' && it != '\'' }
        var neg = false
        if (s.startsWith("(") && s.endsWith(")")) { neg = true; s = s.substring(1, s.length - 1) }
        if (s.startsWith("-")) { neg = !neg; s = s.substring(1) }
        if (s.endsWith("-")) { neg = !neg; s = s.dropLast(1) }
        if (s.isEmpty() || s.any { !(it.isDigit() || it == ',' || it == '.') }) return null
        val commas = s.count { it == ',' }
        val dots = s.count { it == '.' }
        s = when {
            commas == 1 && dots == 0 -> s.replace(',', '.')
            commas > 0 && dots == 1 && s.lastIndexOf('.') > s.lastIndexOf(',') -> s.replace(",", "")
            commas == 1 && dots > 0 && s.lastIndexOf(',') > s.lastIndexOf('.') -> s.replace(".", "").replace(',', '.')
            commas > 1 && dots == 0 -> s.replace(",", "")
            dots > 1 && commas == 0 -> s.replace(".", "")
            else -> s
        }
        val v = s.toDoubleOrNull() ?: return null
        return if (neg) -v else v
    }

    fun of(cell: Cell?): Double? = cell?.num ?: cell?.text?.let { parse(it) }
}
