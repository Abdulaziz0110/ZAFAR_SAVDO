package uz.imsoft.shared.android

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Stats

/**
 * v4.0: statistika paneli (Admin, Agent, Mijoz — bir xil): davr (kun/hafta/oy/yil), ko'rsatkich (savdo/to'lov/qaytarish/
 * foyda), ustunli grafik, tanlangan ustun va davr jami, eng ko'p sotilgan tovarlar, narx o'zgarishlari.
 * Matnlar chaqiruvchidan (strings.xml).
 */
class StatsPanel(
    private val ctx: Context,
    private val texts: Texts,
    private val money: (Double) -> String,
    private val metrics: List<Metric>,
) {
    enum class Metric { SALES, PAYMENTS, RETURNS, PROFIT }

    /**
     * [periods] — "Kun", "Hafta", "Oy", "Yil"; [metricNames] — [Metric] tartibida; [selected] — "%1$s: %2$s";
     * [total] — "Jami (%1$s): %2$s"; [priceChange] — "%1$s → %2$s".
     */
    class Texts(
        val periods: List<String>, val metricNames: List<String>, val selected: String, val total: String,
        val top: String, val prices: String, val empty: String, val priceChange: String, val dateOf: (Long) -> String,
    )

    val view: LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private var period = Stats.Period.DAY
    private var metric = metrics.first()
    private var entries: List<LedgerEntry> = emptyList()
    private var rate = 0.0
    private var cost: (String) -> Double? = { null }
    private val chart = BarChart(ctx)
    private val tvSel: TextView = Ui.title(ctx, "", 4)
    private val tvTotal: TextView = Ui.hint(ctx, "", 2)
    private val lists = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private var series: List<Stats.Bucket> = emptyList()

    init {
        view.addView(Ui.toggle(ctx, texts.periods, 0, 4) { i -> period = Stats.Period.entries[i]; render() })
        if (metrics.size > 1) view.addView(Ui.toggle(ctx, metrics.map { texts.metricNames[it.ordinal] }, 0, 6) { i -> metric = metrics[i]; render() })
        val (card, col) = Ui.card(ctx, 10)
        col.addView(tvSel)
        col.addView(tvTotal)
        col.addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(ctx, 8) })
        view.addView(card)
        view.addView(lists)
        chart.format = money
        chart.onSelect = { i -> showSelected(i) }
    }

    fun setData(entries: List<LedgerEntry>, rate: Double, cost: (String) -> Double? = { null }) {
        this.entries = entries
        this.rate = rate
        this.cost = cost
        render()
    }

    private fun valueOf(b: Stats.Bucket) = when (metric) {
        Metric.SALES -> b.sales
        Metric.PAYMENTS -> b.payments
        Metric.RETURNS -> b.returns
        Metric.PROFIT -> b.profit
    }

    private fun showSelected(i: Int) {
        val b = series.getOrNull(i) ?: return
        tvSel.text = texts.selected.format(b.label, money(valueOf(b)))
    }

    private fun render() {
        series = Stats.series(entries, period, System.currentTimeMillis(), rate, cost)
        chart.bars = series.map { it.label to valueOf(it).coerceAtLeast(0.0) }
        showSelected(series.lastIndex)
        tvTotal.text = texts.total.format(texts.periods[period.ordinal], money(series.sumOf { valueOf(it) }))
        lists.removeAllViews()
        val from = series.firstOrNull()?.from ?: 0L
        val top = Stats.top(entries, from, rate, 10)
        lists.addView(Ui.section(ctx, texts.top))
        val (c1, col1) = Ui.card(ctx)
        if (top.isEmpty()) col1.addView(Ui.hint(ctx, texts.empty, 0))
        top.forEachIndexed { i, t ->
            if (i > 0) col1.addView(Ui.divider(ctx))
            col1.addView(Ui.item(ctx, "${i + 1}. ${t.product}", fmtQty(t.qty), money(t.sum)))
        }
        lists.addView(c1)
        val changes = Stats.priceChanges(entries, from, rate, 10)
        if (changes.isNotEmpty()) {
            lists.addView(Ui.section(ctx, texts.prices))
            val (c2, col2) = Ui.card(ctx)
            changes.forEachIndexed { i, (p, pr, st) ->
                if (i > 0) col2.addView(Ui.divider(ctx))
                col2.addView(Ui.item(ctx, p, texts.dateOf(st), texts.priceChange.format(money(pr.first), money(pr.second))))
            }
            lists.addView(c2)
        }
    }

    private fun fmtQty(q: Double): String = if (q == Math.floor(q)) q.toLong().toString() else "%.2f".format(java.util.Locale.US, q).trimEnd('0').trimEnd('.')
}
