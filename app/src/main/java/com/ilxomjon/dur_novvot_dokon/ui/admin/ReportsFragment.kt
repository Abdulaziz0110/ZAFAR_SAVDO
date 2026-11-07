package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uz.imsoft.shared.android.ChartColors
import uz.imsoft.shared.android.DonutChart
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.OneCReports
import uz.imsoft.shared.report.Stamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * v4.1 Admin: 1C hisobotlari — kompyuter 1C registrlaridan o'qiydi (1C'dagi "Фойда", "Харажатлар", "Касса",
 * "Остаток по товаров" bilan bir xil): yalpi va sof foyda, xarajatlar, kassa qoldig'i va aylanmasi, ombor qoldig'i,
 * qarzlar. Davr tanlanadi → so'rov kompyuterga ketadi → javob kelganda chiziladi (oxirgisi telefonda saqlanadi).
 */
class ReportsFragment : CodeFragment() {
    private lateinit var tvStatus: TextView
    private lateinit var body: LinearLayout
    private var period = 0
    private var askedAt = 0L
    private var shownAt = -1L

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.rp_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.rp_hint), 4))
        c.addView(Ui.toggle(ctx, listOf(getString(R.string.rp_today), getString(R.string.rp_yesterday), getString(R.string.rp_week), getString(R.string.rp_month), getString(R.string.rp_last30)), 0, 8) { i ->
            period = i
            ask()
        })
        tvStatus = Ui.hint(ctx, "", 6).also { c.addView(it) }
        c.addView(Ui.button(ctx, getString(R.string.rp_refresh), outlined = true, icon = R.drawable.ic_sync) { ask() })
        body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(body)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { ServiceLocator.onec.load(); render(force = true) }
        launchOnStarted { ServiceLocator.onec.state.collect { render() } }
        launchOnStarted {
            // So'rov yuborilgan bo'lsa — kompyuter yuborgan javobni tezroq olish
            while (isActive) {
                delay(6_000)
                if (askedAt > 0 && System.currentTimeMillis() - askedAt < 5 * 60_000L) runCatching { ServiceLocator.share.processInbox() }
            }
        }
    }

    private fun range(): Pair<String, String> {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        val a = Calendar.getInstance()
        val b = Calendar.getInstance()
        when (period) {
            1 -> { a.add(Calendar.DAY_OF_MONTH, -1); b.add(Calendar.DAY_OF_MONTH, -1) }
            2 -> { a.firstDayOfWeek = Calendar.MONDAY; while (a.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) a.add(Calendar.DAY_OF_MONTH, -1) }
            3 -> a.set(Calendar.DAY_OF_MONTH, 1)
            4 -> a.add(Calendar.DAY_OF_MONTH, -29)
        }
        return f.format(a.time) to f.format(b.time)
    }

    private fun ask() {
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        val (f, t) = range()
        launchUi {
            runCatching { ServiceLocator.onec.requestData("rep", f, t) }
                .onSuccess { askedAt = System.currentTimeMillis(); tvStatus.text = getString(R.string.rp_asked) }
                .onFailure { snack(getString(R.string.oc_no_cloud)) }
        }
    }

    private fun render(force: Boolean = false) {
        if (!::body.isInitialized) return
        val r = ServiceLocator.onec.state.value.reports
        if (r == null) {
            if (askedAt == 0L) tvStatus.text = getString(R.string.rp_none)
            body.removeAllViews()
            return
        }
        if (!force && r.at == shownAt) return
        shownAt = r.at
        askedAt = 0L
        tvStatus.text = getString(R.string.rp_period, Stamp.formatDate(r.from), Stamp.formatDate(r.to), Stamp.formatDateTime(uz.imsoft.shared.report.PendingMerge.stampOf(r.at)))
        body.removeAllViews()
        val rate = ServiceLocator.catalog.state.value.rate
        if (r.errors.isNotEmpty()) body.addView(Ui.hint(ctx, getString(R.string.rp_errors, r.errors.joinToString("\n")), 6).apply { setTextColor(ChartColors.RETURNS) })
        renderProfit(r, rate)
        renderCash(r, rate)
        renderStock(r, rate)
        renderDebts(r, rate)
    }

    private fun total(a: OneCReports.Amount, rate: Double) = som(a.total(rate))

    private fun amount(som: Double, usd: Double): String = money2(som, usd)

    private fun renderProfit(r: OneCReports, rate: Double) {
        val p = r.profit ?: return
        body.addView(Ui.section(ctx, getString(R.string.rp_profit)))
        val net = r.netProfit(rate) ?: 0.0
        val exp = r.expenses.orEmpty()
        val expTotal = exp.sumOf { it.total(rate) }
        tiles(
            listOf(
                Triple(getString(R.string.rp_sales), total(p.sale, rate), ChartColors.SALES),
                Triple(getString(R.string.rp_gross), total(p.profit, rate), ChartColors.PROFIT),
                Triple(getString(R.string.rp_expenses), som(expTotal), ChartColors.RETURNS),
                Triple(getString(R.string.rp_net), som(net), if (net >= 0) ChartColors.UP else ChartColors.DOWN),
            ),
        )
        val (card, col) = Ui.card(ctx)
        col.addView(Ui.item(ctx, getString(R.string.rp_cost), "", amount(p.cost.som, p.cost.usd)))
        col.addView(Ui.item(ctx, getString(R.string.rp_sales), "", amount(p.sale.som, p.sale.usd)))
        col.addView(Ui.item(ctx, getString(R.string.rp_gross), "", amount(p.profit.som, p.profit.usd)))
        if (p.discount.som != 0.0 || p.discount.usd != 0.0) col.addView(Ui.item(ctx, getString(R.string.rp_discount), "", amount(p.discount.som, p.discount.usd)))
        if (p.sale.total(rate) > 0) col.addView(Ui.hint(ctx, getString(R.string.rp_margin, pct(p.profit.total(rate) / p.sale.total(rate) * 100)), 4))
        body.addView(card)
        if (exp.isNotEmpty()) {
            body.addView(Ui.section(ctx, getString(R.string.rp_expenses)))
            val (c2, col2) = Ui.card(ctx)
            val top = exp.take(6)
            col2.addView(DonutChart(ctx).apply { values = top.map { it.total(rate) } + listOfNotNull(exp.drop(6).sumOf { it.total(rate) }.takeIf { it > 0 }); center = som(expTotal) })
            top.forEachIndexed { i, e -> col2.addView(legend(ChartColors.at(i), e.name, amount(e.som, e.usd))) }
            exp.drop(6).forEach { e -> col2.addView(legend(ChartColors.OTHER, e.name, amount(e.som, e.usd))) }
            body.addView(c2)
        }
        if (p.rows.isNotEmpty()) {
            body.addView(Ui.section(ctx, getString(R.string.rp_by_goods, p.count)))
            val (c3, col3) = Ui.card(ctx)
            p.rows.sortedByDescending { it.profit(rate) }.take(40).forEachIndexed { i, row ->
                if (i > 0) col3.addView(Ui.divider(ctx))
                val v = Ui.item(ctx, row.product, getString(R.string.rp_goods_sub, fmtQty(row.qty), amount(row.saleSom, row.saleUsd)), amount(row.profitSom, row.profitUsd))
                (v.getChildAt(v.childCount - 1) as? TextView)?.setTextColor(if (row.profit(rate) >= 0) ChartColors.UP else ChartColors.DOWN)
                col3.addView(v)
            }
            val losses = p.rows.filter { it.profit(rate) < 0 }
            if (losses.isNotEmpty()) col3.addView(Ui.hint(ctx, getString(R.string.rp_losses, losses.size), 6).apply { setTextColor(ChartColors.DOWN) })
            body.addView(c3)
        }
    }

    private fun renderCash(r: OneCReports, rate: Double) {
        val c = r.cash ?: return
        body.addView(Ui.section(ctx, getString(R.string.rp_cash)))
        tiles(
            listOf(
                Triple(getString(R.string.rp_cash_cash), som(c.cashSom), ChartColors.SALES),
                Triple(getString(R.string.rp_cash_card), som(c.cardSom), ChartColors.PAYMENTS),
                Triple(getString(R.string.rp_cash_click), som(c.clickSom), ChartColors.at(2)),
                Triple(getString(R.string.rp_cash_usd), usd(c.totalUsd), ChartColors.at(1)),
            ),
        )
        body.addView(Ui.hint(ctx, getString(R.string.rp_cash_total, som(c.totalSom + if (rate > 0) c.totalUsd * rate else 0.0)), 6))
        if (c.turn.isNotEmpty()) {
            val (card, col) = Ui.card(ctx)
            col.addView(Ui.text(ctx, getString(R.string.rp_cash_turn), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge))
            c.turn.take(30).forEachIndexed { i, t ->
                if (i > 0) col.addView(Ui.divider(ctx))
                col.addView(Ui.item(ctx, t.name, getString(R.string.rp_in_out, amount(t.inSom, t.inUsd), amount(t.outSom, t.outUsd)), amount(t.inSom - t.outSom, t.inUsd - t.outUsd)))
            }
            body.addView(card)
        }
    }

    private fun renderStock(r: OneCReports, rate: Double) {
        val s = r.stock ?: return
        body.addView(Ui.section(ctx, getString(R.string.rp_stock)))
        tiles(
            listOf(
                Triple(getString(R.string.rp_stock_value), som(s.total(rate)), ChartColors.at(2)),
                Triple(getString(R.string.rp_stock_count), s.count.toString(), ChartColors.at(5)),
            ),
        )
        if (s.negative > 0) body.addView(Ui.hint(ctx, getString(R.string.rp_stock_negative, s.negative), 6).apply { setTextColor(ChartColors.DOWN) })
        val (card, col) = Ui.card(ctx)
        s.rows.take(40).forEachIndexed { i, row ->
            if (i > 0) col.addView(Ui.divider(ctx))
            col.addView(Ui.item(ctx, row.product, getString(R.string.rp_stock_row, fmtQty(row.qty), if (row.usd) usd(row.cost) else som(row.cost)), if (row.usd) usd(row.value) else som(row.value)))
        }
        if (s.count > 40) col.addView(Ui.hint(ctx, getString(R.string.rp_more, s.count - 40), 4))
        body.addView(card)
    }

    private fun renderDebts(r: OneCReports, rate: Double) {
        val d = r.debts ?: return
        body.addView(Ui.section(ctx, getString(R.string.rp_debts)))
        tiles(
            listOf(
                Triple(getString(R.string.rp_owed_to_us), total(d.owedToUs, rate), ChartColors.UP),
                Triple(getString(R.string.rp_we_owe), total(d.weOwe, rate), ChartColors.DOWN),
            ),
        )
        fun list(title: Int, rows: List<OneCReports.DebtRow>) {
            if (rows.isEmpty()) return
            val (card, col) = Ui.card(ctx)
            col.addView(Ui.text(ctx, getString(title), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge))
            rows.take(15).forEachIndexed { i, row ->
                if (i > 0) col.addView(Ui.divider(ctx))
                col.addView(Ui.item(ctx, row.name, getString(if (row.supplier) R.string.rp_supplier else R.string.rp_client), amount(row.som, row.usd)))
            }
            body.addView(card)
        }
        list(R.string.rp_top_debtors, d.top)
        list(R.string.rp_top_owe, d.owe)
    }

    private fun legend(color: Int, name: String, value: String): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 2))
        }
        row.addView(View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color) }
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 12), Ui.dp(ctx, 12)))
        row.addView(Ui.text(ctx, name).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(ctx, 8) })
        row.addView(Ui.text(ctx, value, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge))
        return row
    }

    /** Rangli ko'rsatkichlar (2 tadan qatorda). */
    private fun tiles(items: List<Triple<String, String, Int>>) {
        items.chunked(2).forEach { pair ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, (label, value, color) ->
                val card = MaterialCardView(ctx).apply {
                    radius = Ui.dp(ctx, 14).toFloat()
                    cardElevation = 0f
                    strokeWidth = 0
                    setCardBackgroundColor(ColorStateList.valueOf(ChartColors.alpha(color, 0.16f)))
                }
                val col = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    val pd = Ui.dp(ctx, 12)
                    setPadding(pd, pd, pd, pd)
                }
                col.addView(Ui.text(ctx, label, com.google.android.material.R.style.TextAppearance_Material3_LabelMedium).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                col.addView(Ui.text(ctx, value, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, 4).apply {
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                card.addView(col)
                row.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i == 1) marginStart = Ui.dp(ctx, 8)
                    topMargin = Ui.dp(ctx, 8)
                })
            }
            if (pair.size == 1) row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = Ui.dp(ctx, 8) })
            body.addView(row)
        }
    }

    private fun pct(v: Double): String = (if (kotlin.math.abs(v) >= 10) "%.0f" else "%.1f").format(Locale.US, v) + "%"
}
