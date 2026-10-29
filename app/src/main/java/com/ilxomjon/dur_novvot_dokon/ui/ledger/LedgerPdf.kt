package com.ilxomjon.dur_novvot_dokon.ui.ledger

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.Stamp

/** Ekrandagi ro'yxat (qidiruv va filtr bilan) — PDF / chop etish uchun HTML (A4). [extra] — qo'shimcha qatorlar (masalan qarz). */
object LedgerPdf {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun html(ctx: Context, client: String, s: LedgerUiState, extra: List<String> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>")
        sb.append("body{font-family:Arial,sans-serif;font-size:11px;margin:16px;color:#111}h2{margin:0 0 4px;color:#006A60}")
        sb.append("table{width:100%;border-collapse:collapse;margin-top:8px}th,td{border:1px solid #bbb;padding:4px 6px;text-align:left;vertical-align:top}")
        sb.append("th{background:#006A60;color:#fff}td.n{text-align:right;white-space:nowrap}tr.d td{background:#E6E9E7;font-weight:bold}")
        sb.append(".s{margin:6px 0;padding:6px;background:#F2F4F2;border:1px solid #BEC9C6}</style></head><body>")
        sb.append("<h2>").append(esc(client)).append("</h2>")
        val period = if (s.period == PeriodFilter.ALL) ctx.getString(R.string.lg_p_all) else Stamp.formatDate(s.from) + " – " + Stamp.formatDate(minOf(s.to, Stamp.dayEnd(s.to)))
        sb.append("<div>").append(esc(period))
        if (s.query.isNotBlank()) sb.append(" · ").append(esc(ctx.getString(R.string.lg_pdf_query, s.query)))
        sb.append("</div>")
        val r = s.result
        if (r != null) {
            val m = r.summary
            sb.append("<div class='s'>")
            val lines = ArrayList<String>()
            if (m.sales > 0) lines += ctx.getString(R.string.lg_sum_sales, m.sales, LedgerUi.money(ctx, m.salesTotal))
            if (m.returns > 0) lines += ctx.getString(R.string.lg_sum_returns, m.returns, LedgerUi.money(ctx, m.returnsTotal))
            if (m.payments > 0) lines += ctx.getString(R.string.lg_sum_payments, m.payments, LedgerUi.money(ctx, m.paymentsTotal))
            if (s.query.isNotBlank() && r.hasLineConds && m.goodsLines > 0) lines += ctx.getString(R.string.lg_sum_goods, m.goodsLines, NumFmt.qty(m.goodsQty), LedgerUi.money(ctx, m.goodsSum))
            lines += extra
            sb.append(lines.joinToString("<br>") { esc(it) })
            sb.append("</div>")
        }
        sb.append("<table><tr><th>").append(esc(ctx.getString(R.string.lg_col_time))).append("</th><th>").append(esc(ctx.getString(R.string.lg_col_what)))
            .append("</th><th>").append(esc(ctx.getString(R.string.lg_col_qty))).append("</th><th>").append(esc(ctx.getString(R.string.lg_col_price)))
            .append("</th><th>").append(esc(ctx.getString(R.string.lg_col_sum))).append("</th></tr>")
        for (row in s.rows) {
            when (row) {
                is LedgerRow.Day -> sb.append("<tr class='d'><td colspan='5'>").append(esc(LedgerUi.dayLabel(ctx, row.day))).append("</td></tr>")
                is LedgerRow.Doc -> {
                    val d = row.hit.doc
                    val head = LedgerUi.time(d.stamp) + " " + LedgerUi.kindTitle(ctx, d.kind)
                    if (d.isGoods) {
                        val show = if (s.query.isNotBlank() && row.hit.lines.isNotEmpty()) row.hit.lines.sorted().map { d.lines[it] } else d.goods
                        for ((i, e) in show.withIndex()) {
                            sb.append("<tr><td>").append(if (i == 0) esc(head) else "").append("</td><td>").append(esc(e.product))
                                .append("</td><td class='n'>").append(NumFmt.qty(e.qty)).append("</td><td class='n'>").append(esc(NumFmt.price(e.v(Col.PRICE))))
                                .append("</td><td class='n'>").append(esc(LedgerUi.money(ctx, LedgerDoc.amountOf(e)))).append("</td></tr>")
                        }
                    } else {
                        sb.append("<tr><td>").append(esc(head)).append("</td><td>").append(esc(d.comment))
                            .append("</td><td></td><td></td><td class='n'>").append(esc(LedgerUi.money(ctx, d.total))).append("</td></tr>")
                    }
                }
            }
        }
        sb.append("</table></body></html>")
        return sb.toString()
    }
}
