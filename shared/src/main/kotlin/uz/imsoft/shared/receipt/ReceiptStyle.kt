package uz.imsoft.shared.receipt

import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

/**
 * Chek ko'rinishi sozlamasi (1-ilovada tahrirlanadi, 2-ilovaga snapshot orqali boradi).
 * Hamma qiymat chegaralangan — buzilgan yoki eski matndan ham to'g'ri uslub chiqadi.
 */
data class ReceiptStyle(
    /** Qog'oz eni, mm: 80 (576 nuqta) yoki 58 (384 nuqta). */
    val paper: Int = 80,
    /** Arial (1C chekidagi shrift) — aks holda tizim shrifti. */
    val arial: Boolean = false,
    /** Hamma matn qalin (1C chekidagidek). */
    val bold: Boolean = false,
    /** Sarlavha (do'kon nomi) serifli shriftda (1C: Bookman Old Style). */
    val headerSerif: Boolean = true,
    /** Matn o'lchami, % (70..150). */
    val text: Int = 100,
    /** Sarlavha o'lchami, % (50..180). */
    val header: Int = 100,
    /** Logo (yuklangan bo'lsa) ko'rsatiladi. */
    val logo: Boolean = true,
    /** Logo eni, qog'oz enidan % (20..100). */
    val logoWidth: Int = 60,
    val staff: Boolean = true,
    val number: Boolean = true,
    val time: Boolean = true,
    val date: Boolean = true,
    val client: Boolean = true,
    /** Mijozning oldingi qarzi (tovarlar ustida). */
    val before: Boolean = true,
    /** Qatorlar oldida tartib raqami "1 )". */
    val lineNo: Boolean = true,
    /** Har qator ramkada. */
    val frame: Boolean = true,
    /** To'lov va qarz qoldig'i (Summa ostida). */
    val after: Boolean = true,
    /** Chek osti (telefonlar). */
    val footer: Boolean = true,
    /** Kichik izohlar ("1C hisobotidan nusxa" kabi). */
    val notes: Boolean = true,
    /** Sarlavha ostidagi qatorlar (manzil, mo'ljal) — har qator alohida. */
    val sub: String = "",
    /** Eng pastdagi xabar ("Xaridingiz uchun rahmat!"). */
    val thanks: String = "",
    /** Qoralik chegarasi (100..220): kattasi — qoraroq. */
    val dark: Int = 140,
    /** Oxiridagi bo'sh joy, mm (0..40) — qog'oz kesilganda matn qolmasin. */
    val feed: Int = 0,
) {
    /** Printer nuqtalarida eni (203 dpi). */
    val width: Int get() = if (paper == 58) 384 else 576

    /** Ko'rsatilmaydigan qismlarni olib tashlaydi va sarlavha osti / rahmat matnini qo'shadi. */
    fun apply(r: Receipt): Receipt {
        val keep = r.info.indices.filter { i ->
            when (r.infoKeys.getOrNull(i)) {
                Receipt.KEY_STAFF -> staff
                Receipt.KEY_NUMBER -> number
                Receipt.KEY_TIME -> time
                else -> true
            }
        }
        return r.copy(
            info = keep.map { r.info[it] },
            infoKeys = keep.mapNotNull { r.infoKeys.getOrNull(it) },
            date = if (date) r.date else "",
            clientLabel = if (client) r.clientLabel else "",
            clientName = if (client) r.clientName else "",
            client = if (client) r.client else "",
            before = if (before) r.before else emptyList(),
            after = if (after) r.after else emptyList(),
            footer = if (footer) r.footer else emptyList(),
            notes = if (notes) r.notes else emptyList(),
            subheader = lines(sub),
            thanks = thanks.trim(),
        )
    }

    fun encode(): String = Json.write(
        linkedMapOf(
            "p" to paper, "a" to arial, "b" to bold, "hs" to headerSerif, "t" to text, "h" to header,
            "lg" to logo, "lw" to logoWidth, "st" to staff, "no" to number, "tm" to time, "dt" to date, "cl" to client,
            "bf" to before, "ln" to lineNo, "fr" to frame, "af" to after, "ft" to footer, "nt" to notes,
            "sub" to sub, "th" to thanks, "dk" to dark, "fd" to feed,
        ),
    )

    /** Qiymatlarni ruxsat etilgan oraliqqa keltiradi. */
    fun normalized(): ReceiptStyle = copy(
        paper = if (paper == 58) 58 else 80,
        text = text.coerceIn(70, 150), header = header.coerceIn(50, 180), logoWidth = logoWidth.coerceIn(20, 100),
        dark = dark.coerceIn(100, 220), feed = feed.coerceIn(0, 40),
        sub = lines(sub).take(MAX_SUB_LINES).joinToString("\n") { it.take(MAX_LINE) },
        thanks = thanks.trim().take(MAX_LINE * 2),
    )

    companion object {
        const val MAX_SUB_LINES = 4
        const val MAX_LINE = 60

        /** Hozirgi (v2.6 dan beri) ko'rinish. */
        val STANDARD = ReceiptStyle()

        /** 1C "Чек1/Чек2" uslubi: hammasi Arial qalin, sarlavha Bookman kabi serif. */
        val ONEC = ReceiptStyle(arial = true, bold = true, headerSerif = true, text = 100, header = 110, dark = 150)

        /** Tor (58 mm) printer uchun ixcham ko'rinish. */
        val COMPACT = ReceiptStyle(paper = 58, arial = true, bold = false, text = 125, header = 100, logoWidth = 50, frame = false, notes = false)

        fun decode(text: String?): ReceiptStyle {
            if (text.isNullOrBlank()) return STANDARD
            val o = runCatching { JObj.parse(text) }.getOrNull() ?: return STANDARD
            val d = STANDARD
            return ReceiptStyle(
                paper = o.long("p", d.paper.toLong()).toInt(), arial = o.bool("a", d.arial), bold = o.bool("b", d.bold),
                headerSerif = o.bool("hs", d.headerSerif), text = o.long("t", d.text.toLong()).toInt(), header = o.long("h", d.header.toLong()).toInt(),
                logo = o.bool("lg", d.logo), logoWidth = o.long("lw", d.logoWidth.toLong()).toInt(),
                staff = o.bool("st", d.staff), number = o.bool("no", d.number), time = o.bool("tm", d.time), date = o.bool("dt", d.date),
                client = o.bool("cl", d.client), before = o.bool("bf", d.before), lineNo = o.bool("ln", d.lineNo), frame = o.bool("fr", d.frame),
                after = o.bool("af", d.after), footer = o.bool("ft", d.footer), notes = o.bool("nt", d.notes),
                sub = o.str("sub"), thanks = o.str("th"), dark = o.long("dk", d.dark.toLong()).toInt(), feed = o.long("fd", d.feed.toLong()).toInt(),
            ).normalized()
        }

        private fun lines(s: String): List<String> = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/** Rasmni termoprinter uchun qora-oq qilish (Floyd–Steinberg) — logo kulrang joylari ham ko'rinadi. */
object Dither {
    /** [lum] — 0 (qora)..255 (oq), [w]×[h]. Natija: true = qora. */
    fun floyd(w: Int, h: Int, lum: IntArray, threshold: Int = 128): BooleanArray {
        require(lum.size == w * h)
        val e = FloatArray(w * h) { lum[it].toFloat() }
        val out = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val old = e[i]
            val black = old < threshold
            out[i] = black
            val err = old - (if (black) 0f else 255f)
            if (x + 1 < w) e[i + 1] += err * 7 / 16
            if (y + 1 < h) {
                if (x > 0) e[i + w - 1] += err * 3 / 16
                e[i + w] += err * 5 / 16
                if (x + 1 < w) e[i + w + 1] += err * 1 / 16
            }
        }
        return out
    }
}
