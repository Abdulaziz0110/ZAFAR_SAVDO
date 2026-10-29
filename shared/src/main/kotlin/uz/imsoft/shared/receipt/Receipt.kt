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
    /** Narx: so'm bo'lsa so'm, aks holda $. */
    fun price(m: Money2): String = if (m.som != 0.0) som(m.som) else if (m.usd != 0.0) usd(m.usd) + " $" else ""
}

/** Chek (ekranda, rasmda, printerda va PDF'da bir xil). */
data class Receipt(
    val header: String,
    val title: String,
    val client: String,
    val dateText: String,
    val lines: List<Line>,
    val totals: List<Total>,
    val notes: List<String>,
) {
    data class Line(val name: String, val detail: String, val amount: String)
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
    )

    fun fromDoc(doc: LedgerDoc, header: String, clientName: String, l: Labels): Receipt {
        val lines = ArrayList<Receipt.Line>()
        if (doc.isGoods) {
            for (e in doc.goods) {
                val price = e.v(Col.PRICE)
                val p = NumFmt.price(price)
                val detail = if (e.qty != 0.0 && p.isNotEmpty()) NumFmt.qty(e.qty) + " × " + p else if (e.qty != 0.0) NumFmt.qty(e.qty) else ""
                lines += Receipt.Line(e.product, detail, NumFmt.money2(LedgerDoc.amountOf(e), l.somUnit))
            }
        } else {
            for (e in doc.lines) lines += Receipt.Line(e.product.ifBlank { e.kindText }, "", NumFmt.money2(LedgerDoc.amountOf(e), l.somUnit))
        }
        val totals = ArrayList<Receipt.Total>()
        if (doc.isGoods) totals += Receipt.Total(l.items, doc.goods.size.toString() + " · " + NumFmt.qty(doc.qty))
        if (doc.kind == EntryKind.SALE && !doc.discount.isZero) {
            totals += Receipt.Total(l.total, NumFmt.money2(doc.total, l.somUnit))
            totals += Receipt.Total(l.discount, "−" + NumFmt.money2(doc.discount, l.somUnit))
            totals += Receipt.Total(l.payable, NumFmt.money2(doc.total - doc.discount, l.somUnit), bold = true)
        } else {
            totals += Receipt.Total(l.total, NumFmt.money2(doc.total, l.somUnit), bold = true)
        }
        doc.balanceAfter?.let { totals += Receipt.Total(l.balance, NumFmt.money2(it, l.somUnit)) }
        return Receipt(
            header, l.titleFor(doc.kind), l.client + ": " + clientName, Stamp.formatDateTime(doc.stamp),
            lines, totals, listOf(l.note),
        )
    }

    /** Oddiy matn (Telegram'ga yuborish, nusxa olish). */
    fun toText(r: Receipt): String = buildString {
        if (r.header.isNotBlank()) append(r.header).append('\n')
        append(r.title).append('\n')
        append(r.client).append('\n')
        append(r.dateText).append('\n')
        append("————————————\n")
        for ((i, x) in r.lines.withIndex()) {
            append(i + 1).append(". ").append(x.name).append('\n')
            if (x.detail.isNotEmpty()) append("   ").append(x.detail).append(" = ").append(x.amount).append('\n')
            else append("   ").append(x.amount).append('\n')
        }
        append("————————————\n")
        for (t in r.totals) {
            if (t.bold) append("*").append(t.label).append(": ").append(t.value).append("*\n")
            else append(t.label).append(": ").append(t.value).append('\n')
        }
        for (n in r.notes) append('\n').append(n)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** PDF / chop etish (80 mm qog'ozga o'xshash ustun, A4 da ham chiroyli). */
    fun toHtml(r: Receipt): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>")
        append("body{font-family:Arial,sans-serif;color:#111;margin:0;padding:16px}.r{max-width:360px;margin:0 auto}")
        append("h1{font-size:18px;margin:0;text-align:center}h2{font-size:15px;margin:6px 0 0;text-align:center;font-weight:normal}")
        append(".m{text-align:center;font-size:13px;color:#444;margin:2px 0}.sep{border-top:1px dashed #888;margin:10px 0}")
        append(".l{font-size:14px;margin:6px 0}.n{font-weight:bold}.d{display:flex;justify-content:space-between;color:#333}")
        append(".t{display:flex;justify-content:space-between;font-size:14px;margin:3px 0}.b{font-weight:bold;font-size:16px}")
        append(".note{font-size:11px;color:#666;text-align:center;margin-top:12px}</style></head><body><div class='r'>")
        if (r.header.isNotBlank()) append("<h1>").append(esc(r.header)).append("</h1>")
        append("<h2>").append(esc(r.title)).append("</h2>")
        append("<div class='m'>").append(esc(r.client)).append("</div><div class='m'>").append(esc(r.dateText)).append("</div><div class='sep'></div>")
        for ((i, x) in r.lines.withIndex()) {
            append("<div class='l'><div class='n'>").append(i + 1).append(". ").append(esc(x.name)).append("</div><div class='d'><span>")
                .append(esc(x.detail)).append("</span><span>").append(esc(x.amount)).append("</span></div></div>")
        }
        append("<div class='sep'></div>")
        for (t in r.totals) append("<div class='t").append(if (t.bold) " b" else "").append("'><span>").append(esc(t.label)).append("</span><span>").append(esc(t.value)).append("</span></div>")
        for (n in r.notes) append("<div class='note'>").append(esc(n)).append("</div>")
        append("</div></body></html>")
    }
}
