package uz.imsoft.shared.receipt

import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.Stamp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Ikki ilova uchun bir xil son ko'rinishi. */
object NumFmt {
    private val sym = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' '; decimalSeparator = ',' }
    fun som(v: Double): String = DecimalFormat("#,##0.##", sym).format(v)
    fun usd(v: Double): String = DecimalFormat("#,##0.00", sym).format(v)
    fun qty(v: Double): String = DecimalFormat("#,##0.###", sym).format(v)
    fun money(v: Double, usd: Boolean, somUnit: String): String = if (usd) usd(v) + " $" else som(v) + " " + somUnit
    fun money2(m: Money2, somUnit: String): String {
        val p = ArrayList<String>()
        if (m.som != 0.0) p += money(m.som, false, somUnit)
        if (m.usd != 0.0) p += money(m.usd, true, somUnit)
        return if (p.isEmpty()) money(0.0, false, somUnit) else p.joinToString(" + ")
    }
    /** Chekdagi summa (birliksiz, do'kon chekidagidek): "4 680 000", dollar bo'lsa "12,50 $". */
    fun bare(m: Money2): String {
        val p = ArrayList<String>()
        if (m.som != 0.0) p += som(m.som)
        if (m.usd != 0.0) p += usd(m.usd) + " $"
        return if (p.isEmpty()) "0" else p.joinToString(" + ")
    }
    fun bare(v: Double, usd: Boolean): String = if (usd) usd(v) + " $" else som(v)
    /** Narx: so'm bo'lsa so'm, aks holda $. */
    fun price(m: Money2): String = if (m.som != 0.0) som(m.som) else if (m.usd != 0.0) usd(m.usd) + " $" else ""
}

/**
 * Chek (ekranda, rasmda, printerda va PDF'da bir xil). v2.6: do'konning qog'oz chekiga o'xshash tuzilish —
 * do'kon nomi, Xodim / Chek № / Vaqt, Mijoz, (oldingi qarz), QAYTIB OLISH banneri, ramkali qatorlar
 * ("1 ) Nom" / "40 x 117 000 = 4 680 000"), Summa, to'lov va qarz qoldig'i, telefonlar.
 */
data class Receipt(
    val header: String,
    val title: String,
    val client: String,
    val dateText: String,
    val lines: List<Line>,
    /** Summa qatorlari (bold — asosiy "Summa"). */
    val totals: List<Total>,
    val notes: List<String>,
    val clientLabel: String = "",
    val clientName: String = "",
    /** "Xodim: Dokon", "Chek №: B-0412", "Vaqt: 16:46:17" — oxirgisining o'ng tomonida [date]. */
    val info: List<Pair<String, String>> = emptyList(),
    val date: String = "",
    /** Mijoz ostida: oldingi qarz. */
    val before: List<Total> = emptyList(),
    /** "QAYTIB OLISH !!!" kabi ajratilgan yozuv (bo'sh — yo'q). */
    val banner: String = "",
    /** Summa ostida: to'lov, qarz qoldig'i. */
    val after: List<Total> = emptyList(),
    /** Eng pastda katta harflar bilan: "Tel: +998 ..." */
    val footer: List<String> = emptyList(),
    /** [info] qatorlarining turi ([KEY_STAFF], [KEY_NUMBER], [KEY_TIME]) — ko'rinish sozlamasi yashirishi uchun. */
    val infoKeys: List<String> = emptyList(),
    /** Sarlavha ostidagi qatorlar (manzil) — [ReceiptStyle.sub]. */
    val subheader: List<String> = emptyList(),
    /** Eng pastdagi xabar — [ReceiptStyle.thanks]. */
    val thanks: String = "",
) {
    companion object {
        const val KEY_STAFF = "staff"
        const val KEY_NUMBER = "no"
        const val KEY_TIME = "time"
    }

    data class Line(val name: String, val detail: String, val amount: String) {
        /** "40 x 117 000 = 4 680 000" (miqdor bo'lmasa — faqat summa). */
        val formula: String get() = if (detail.isNotEmpty()) "$detail = $amount" else amount
    }
    data class Total(val label: String, val value: String, val bold: Boolean = false)
}

object ReceiptBuilder {
    /** Matnlar ilova resurslaridan (lotin/kirill) beriladi. */
    class Labels(
        val somUnit: String,
        val titleFor: (EntryKind) -> String,
        val client: String,
        val total: String,
        val discount: String,
        val payable: String,
        val balance: String,
        val items: String,
        val note: String,
        val sum: String = "Summa",
        val staff: String = "Xodim",
        val number: String = "Chek №",
        val time: String = "Vaqt",
        val returnBanner: String = "QAYTIB OLISH !!!",
    )

    /** 2026-10-14 16:46:17 -> ("16:46:17", "14.10.26"). */
    fun timeAndDate(stamp: Long): Pair<String, String> =
        "%02d:%02d:%02d".format(Stamp.hour(stamp), Stamp.minute(stamp), (stamp % 100).toInt()) to
            "%02d.%02d.%02d".format(Stamp.dayOfMonth(stamp), Stamp.month(stamp), Stamp.year(stamp) % 100)

    fun fromDoc(doc: LedgerDoc, header: String, clientName: String, l: Labels, footer: List<String> = emptyList(), staff: String = ""): Receipt {
        val lines = ArrayList<Receipt.Line>()
        if (doc.isGoods && doc.goods.isNotEmpty()) {
            for (e in doc.goods) {
                val p = NumFmt.price(e.v(Col.PRICE))
                val detail = if (e.qty != 0.0 && p.isNotEmpty()) NumFmt.qty(e.qty) + " x " + p else if (e.qty != 0.0) NumFmt.qty(e.qty) else ""
                lines += Receipt.Line(e.product, detail, NumFmt.bare(LedgerDoc.amountOf(e)))
            }
        } else {
            for (e in doc.lines) lines += Receipt.Line(e.product.ifBlank { uz.imsoft.shared.report.LedgerOwner.plain(e.kindText) }, "", NumFmt.bare(LedgerDoc.amountOf(e)))
        }
        val totals = ArrayList<Receipt.Total>()
        if (doc.kind == EntryKind.SALE && !doc.discount.isZero) {
            totals += Receipt.Total(l.total, NumFmt.bare(doc.total))
            totals += Receipt.Total(l.discount, "−" + NumFmt.bare(doc.discount))
            totals += Receipt.Total(l.payable, NumFmt.bare(doc.total - doc.discount), bold = true)
        } else {
            totals += Receipt.Total(l.sum, NumFmt.bare(doc.total), bold = true)
        }
        val after = ArrayList<Receipt.Total>()
        doc.balanceAfter?.let { after += Receipt.Total(l.balance, NumFmt.bare(it)) }
        val (time, date) = timeAndDate(doc.stamp)
        val info = ArrayList<Pair<String, String>>()
        val keys = ArrayList<String>()
        if (staff.isNotBlank()) { info += l.staff to staff; keys += Receipt.KEY_STAFF }
        info += l.time to time
        keys += Receipt.KEY_TIME
        val banner = when (doc.kind) {
            EntryKind.SALE -> ""
            EntryKind.RETURN -> l.returnBanner
            else -> l.titleFor(doc.kind).uppercase()
        }
        return Receipt(
            header, l.titleFor(doc.kind), l.client + ": " + clientName, Stamp.formatDateTime(doc.stamp),
            lines, totals, listOf(l.note),
            clientLabel = l.client, clientName = clientName, info = info, date = date, banner = banner, after = after, footer = footer, infoKeys = keys,
        )
    }

    /** Oddiy matn (Telegram'ga yuborish, nusxa olish). */
    fun toText(r: Receipt): String = buildString {
        if (r.header.isNotBlank()) append(r.header).append('\n')
        for (x in r.subheader) append(x).append('\n')
        for ((k, v) in r.info) append(k).append(": ").append(v).append('\n')
        if (r.date.isNotEmpty() && r.info.isNotEmpty()) append(r.date).append('\n')
        if (r.info.isEmpty()) append(r.title).append('\n').append(r.dateText).append('\n')
        append(r.client).append('\n')
        for (t in r.before) append(t.label).append(": ").append(t.value).append('\n')
        if (r.banner.isNotEmpty()) append("— ").append(r.banner).append(" —\n")
        append("————————————\n")
        for ((i, x) in r.lines.withIndex()) {
            append(i + 1).append(") ").append(x.name).append('\n')
            append("   ").append(x.formula).append('\n')
        }
        append("————————————\n")
        for (t in r.totals) {
            if (t.bold) append("*").append(t.label).append(": ").append(t.value).append("*\n")
            else append(t.label).append(": ").append(t.value).append('\n')
        }
        for (t in r.after) append(t.label).append(": ").append(t.value).append('\n')
        for (f in r.footer) append(f).append('\n')
        if (r.thanks.isNotEmpty()) append(r.thanks).append('\n')
        for (n in r.notes) append('\n').append(n)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** PDF / chop etish: qog'oz chekka o'xshash ustun (80 mm), A4 da ham chiroyli. */
    fun toHtml(r: Receipt): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>")
        append("body{font-family:Arial,sans-serif;color:#000;margin:0;padding:16px}.r{max-width:380px;margin:0 auto}")
        append("h1{font-family:'Times New Roman',Georgia,serif;font-size:26px;font-weight:900;margin:0 0 10px;text-align:center;line-height:1.15}")
        append(".i{display:flex;font-size:14px;margin:2px 0}.i b{font-weight:normal;width:42%;text-align:right;padding-right:8px}.i span{flex:1}.i i{font-style:normal}")
        append(".c{display:flex;font-size:17px;font-weight:bold;margin:8px 0 4px}.c span{margin-left:24px}")
        append(".t{display:flex;justify-content:space-between;font-size:14px;margin:4px 0}.t.b{font-weight:bold;font-size:20px;margin:10px 0}")
        append(".bn{border-top:2px solid #000;border-bottom:2px solid #000;text-align:center;font-weight:bold;font-size:18px;letter-spacing:6px;padding:8px 0;margin:8px 0}")
        append("table{width:100%;border-collapse:collapse;margin:8px 0}td{border:1.5px solid #000;padding:4px 6px;font-size:14px}")
        append(".n{display:block}.f{display:block;text-align:right}.ft{font-weight:bold;font-size:17px;letter-spacing:1px;margin:4px 0 0 24px}")
        append(".note{font-size:11px;color:#555;text-align:center;margin-top:12px}.sub{text-align:center;font-size:14px;margin:0 0 4px}.th{text-align:center;font-weight:bold;font-size:16px;margin-top:12px}</style></head><body><div class='r'>")
        if (r.header.isNotBlank()) append("<h1>").append(esc(r.header)).append("</h1>")
        for (x in r.subheader) append("<div class='sub'>").append(esc(x)).append("</div>")
        if (r.info.isEmpty()) append("<div class='i'><span style='text-align:center'>").append(esc(r.title + " · " + r.dateText)).append("</span></div>")
        for ((idx, kv) in r.info.withIndex()) {
            append("<div class='i'><b>").append(esc(kv.first)).append(":</b><span>").append(esc(kv.second)).append("</span>")
            if (idx == r.info.lastIndex && r.date.isNotEmpty()) append("<i>").append(esc(r.date)).append("</i>")
            append("</div>")
        }
        append("<div class='c'>").append(esc(r.clientLabel.ifEmpty { r.client })).append(if (r.clientLabel.isNotEmpty()) ":" else "")
        if (r.clientLabel.isNotEmpty()) append("<span>").append(esc(r.clientName)).append("</span>")
        append("</div>")
        for (t in r.before) append("<div class='t'><span>").append(esc(t.label)).append(":</span><span>").append(esc(t.value)).append("</span></div>")
        if (r.banner.isNotEmpty()) append("<div class='bn'>").append(esc(r.banner)).append("</div>")
        append("<table>")
        for ((i, x) in r.lines.withIndex()) {
            append("<tr><td><span class='n'>").append(i + 1).append(" ) ").append(esc(x.name)).append("</span><span class='f'>")
                .append(esc(x.formula)).append("</span></td></tr>")
        }
        append("</table>")
        for (t in r.totals) append("<div class='t").append(if (t.bold) " b" else "").append("'><span>").append(esc(t.label)).append(":</span><span>").append(esc(t.value)).append("</span></div>")
        for (t in r.after) append("<div class='t'><span>").append(esc(t.label)).append(":</span><span>").append(esc(t.value)).append("</span></div>")
        for (f in r.footer) append("<div class='ft'>").append(esc(f)).append("</div>")
        if (r.thanks.isNotEmpty()) append("<div class='th'>").append(esc(r.thanks)).append("</div>")
        for (n in r.notes) append("<div class='note'>").append(esc(n)).append("</div>")
        append("</div></body></html>")
    }
}
