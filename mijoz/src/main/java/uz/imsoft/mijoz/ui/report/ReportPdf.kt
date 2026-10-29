package uz.imsoft.mijoz.ui.report

import android.content.Context
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.money2
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.Stamp

/** Joriy ko'rinishdagi hisobotni chop etish/PDF uchun oddiy HTML (A4). */
object ReportPdf {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun html(ctx: Context, client: String, period: String, s: ReportState, debt: Money2?, debtAt: Long, reportBalance: Pair<Long, Money2>? = null): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>")
        sb.append("body{font-family:Arial,sans-serif;font-size:11px;margin:16px;color:#111}h2{margin:0 0 4px;color:#006A60}")
        sb.append("table{width:100%;border-collapse:collapse;margin-top:8px}th,td{border:1px solid #bbb;padding:4px 6px;text-align:left}")
        sb.append("th{background:#006A60;color:#fff}td.n{text-align:right;white-space:nowrap}tr.d td{background:#E7F1EE;font-weight:bold}")
        sb.append(".s{margin:6px 0;padding:6px;background:#F4FBF9;border:1px solid #CBD8D4}</style></head><body>")
        sb.append("<h2>").append(esc(client)).append("</h2><div>").append(esc(period)).append("</div>")
        val t = s.totals
        if (t != null) {
            sb.append("<div class='s'>")
            sb.append(esc(ctx.getString(R.string.sum_bought, ctx.money2(t.purchased), t.purchaseCount))).append("<br>")
            if (!t.returned.isZero) sb.append(esc(ctx.getString(R.string.sum_returned, ctx.money2(t.returned)))).append("<br>")
            if (!t.discount.isZero) sb.append(esc(ctx.getString(R.string.sum_discount, ctx.money2(t.discount)))).append("<br>")
            sb.append(esc(ctx.getString(R.string.sum_paid, ctx.money2(t.paid), t.paymentCount)))
            if (reportBalance != null) sb.append("<br>").append(esc(ctx.getString(R.string.home_report_balance, Stamp.formatDate(reportBalance.first), ctx.money2(reportBalance.second))))
            if (debt != null) sb.append("<br><b>").append(esc(ctx.getString(R.string.pdf_debt, ctx.money2(debt), if (debtAt > 0) Fmt.dateTime(debtAt) else "—"))).append("</b>")
            sb.append("</div>")
        }
        sb.append("<table><tr><th>").append(esc(ctx.getString(R.string.col_time))).append("</th><th>").append(esc(ctx.getString(R.string.col_what)))
            .append("</th><th>").append(esc(ctx.getString(R.string.col_qty))).append("</th><th>").append(esc(ctx.getString(R.string.col_price)))
            .append("</th><th>").append(esc(ctx.getString(R.string.col_sum))).append("</th></tr>")
        for (day in ClientStats.byDay(s.entries).reversed()) {
            sb.append("<tr class='d'><td colspan='5'>").append(Stamp.formatDate(day.day)).append("</td></tr>")
            for (e in day.entries) {
                val price = e.v(Col.PRICE)
                val p = if (price.som != 0.0) Fmt.som(price.som) else if (price.usd != 0.0) Fmt.usd(price.usd) + " $" else ""
                val amount = when (e.kind) {
                    EntryKind.PAYMENT, EntryKind.PAYMENT_RETURN -> ctx.money2(ClientStats.paymentOf(e))
                    EntryKind.RETURN -> "−" + ctx.money2(ClientStats.returnOf(e))
                    EntryKind.DISCOUNT -> "−" + ctx.money2(e.v(Col.DISCOUNT))
                    else -> ctx.money2(e.v(Col.SUM))
                }
                val what = listOf(e.kindText, e.product).filter { it.isNotBlank() }.joinToString(" · ")
                sb.append("<tr><td>").append("%02d:%02d".format(Stamp.hour(e.stamp), Stamp.minute(e.stamp))).append("</td><td>").append(esc(what))
                    .append("</td><td class='n'>").append(if (e.qty != 0.0) Fmt.qty(e.qty) else "").append("</td><td class='n'>").append(esc(p))
                    .append("</td><td class='n'>").append(esc(amount)).append("</td></tr>")
            }
        }
        sb.append("</table></body></html>")
        return sb.toString()
    }
}
