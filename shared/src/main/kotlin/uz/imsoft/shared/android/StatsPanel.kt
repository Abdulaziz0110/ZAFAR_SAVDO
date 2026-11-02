package uz.imsoft.shared.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.PendingMerge
import uz.imsoft.shared.report.Stamp
import uz.imsoft.shared.report.Stats

/**
 * v4.0 / v4.0.1: statistika paneli (Admin, Agent, Mijoz — bir xil): davr (kun/hafta/oy/yil), ko'rsatkich, rangli ustunli
 * grafik; asosiy ko'rsatkichlar (savdo, foyda, cheklar, o'rtacha chek, mijozlar), mijozlar/tovarlar ulushi (halqa grafik),
 * tez sotilayotgan tovarlar (o'sish/pasayish bilan), hafta kunlari va soatlar, to'xtab qolgan tovarlar, narx o'zgarishlari.
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
     * [total] — "Jami (%1$s): %2$s"; [priceChange] — "%1$s → %2$s". [more] — v4.0.1 qo'shimcha bo'limlar (null — yo'q).
     */
    class Texts(
        val periods: List<String>, val metricNames: List<String>, val selected: String, val total: String,
        val top: String, val prices: String, val empty: String, val priceChange: String, val dateOf: (Long) -> String,
        val more: More? = null,
    )

    /**
     * Bo'sh satr — o'sha ko'rsatkich/bo'lim ko'rsatilmaydi. Formatlar: [fastSub] "%1$d … %2$s", [bestDay] "%1$s",
     * [bestHour] "%1$s … %2$s", [idleSub] "%1$d … %2$d", [kpiFor] "%1$s".
     */
    class More(
        val kpiFor: String, val kpiSales: String, val kpiProfit: String, val kpiDocs: String, val kpiAvg: String,
        val kpiClients: String, val kpiPayments: String,
        val shareClients: String, val shareGoods: String, val other: String,
        val fast: String, val fastSub: String,
        val weekdays: String, val weekdayNames: List<String>, val bestDay: String,
        val hours: String, val bestHour: String,
        val idle: String, val idleSub: String,
        val noGoods: String,
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
    private val kpiBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val lists = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private var series: List<Stats.Bucket> = emptyList()

    init {
        view.addView(Ui.toggle(ctx, texts.periods, 0, 4) { i -> period = Stats.Period.entries[i]; render() })
        if (metrics.size > 1) view.addView(Ui.toggle(ctx, metrics.map { texts.metricNames[it.ordinal] }, 0, 6) { i -> metric = metrics[i]; render() })
        view.addView(kpiBox)
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

    private fun metricColor() = when (metric) {
        Metric.SALES -> ChartColors.SALES
        Metric.PAYMENTS -> ChartColors.PAYMENTS
        Metric.RETURNS -> ChartColors.RETURNS
        Metric.PROFIT -> ChartColors.PROFIT
    }

    private fun showSelected(i: Int) {
        val b = series.getOrNull(i) ?: return
        tvSel.text = texts.selected.format(b.label, money(valueOf(b)))
        renderKpi(b)
    }

    private fun render() {
        series = Stats.series(entries, period, System.currentTimeMillis(), rate, cost)
        chart.color = metricColor()
        chart.bars = series.map { it.label to valueOf(it).coerceAtLeast(0.0) }
        showSelected(series.lastIndex)
        tvTotal.text = texts.total.format(texts.periods[period.ordinal], money(series.sumOf { valueOf(it) }))
        lists.removeAllViews()
        val from = series.firstOrNull()?.from ?: 0L
        val to = series.lastOrNull()?.to ?: Long.MAX_VALUE
        val more = texts.more
        if (more != null) renderMore(more, from, to)
        val top = Stats.top(entries, from, rate, 10)
        lists.addView(Ui.section(ctx, texts.top))
        val (c1, col1) = Ui.card(ctx)
        if (top.isEmpty()) col1.addView(Ui.hint(ctx, texts.empty, 0))
        val maxSum = top.maxOfOrNull { it.sum }?.takeIf { it > 0 } ?: 1.0
        top.forEachIndexed { i, t ->
            if (i > 0) col1.addView(Ui.divider(ctx))
            col1.addView(Ui.item(ctx, "${i + 1}. ${t.product}", fmtQty(t.qty), money(t.sum)))
            col1.addView(meter(t.sum / maxSum, ChartColors.at(i)))
        }
        lists.addView(c1)
        val changes = Stats.priceChanges(entries, from, rate, 10)
        if (changes.isNotEmpty()) {
            lists.addView(Ui.section(ctx, texts.prices))
            val (c2, col2) = Ui.card(ctx)
            changes.forEachIndexed { i, (p, pr, st) ->
                if (i > 0) col2.addView(Ui.divider(ctx))
                val v = Ui.item(ctx, p, texts.dateOf(st), texts.priceChange.format(money(pr.first), money(pr.second)))
                (v.getChildAt(v.childCount - 1) as? TextView)?.setTextColor(if (pr.second >= pr.first) ChartColors.UP else ChartColors.DOWN)
                col2.addView(v)
            }
            lists.addView(c2)
        }
    }

    // ---------------- asosiy ko'rsatkichlar (tanlangan ustun bo'yicha) ----------------

    private fun renderKpi(b: Stats.Bucket) {
        kpiBox.removeAllViews()
        val more = texts.more ?: return
        val k = Stats.kpi(entries, b.from, b.to, rate, cost)
        val title = when (period) {
            Stats.Period.DAY -> Stamp.formatDate(b.from)
            Stats.Period.YEAR -> b.label
            else -> Stamp.formatDate(b.from) + " – " + Stamp.formatDate(PendingMerge.stampOf(PendingMerge.millisOf(b.to) - 1000L))
        }
        kpiBox.addView(Ui.hint(ctx, more.kpiFor.format(title), 10))
        val tiles = ArrayList<Triple<String, String, Int>>()
        if (more.kpiSales.isNotEmpty()) tiles += Triple(more.kpiSales, money(k.sales), ChartColors.SALES)
        if (more.kpiProfit.isNotEmpty() && Metric.PROFIT in metrics && k.hasProfit) tiles += Triple(more.kpiProfit, money(k.profit), ChartColors.PROFIT)
        if (more.kpiDocs.isNotEmpty() && k.docs > 0) tiles += Triple(more.kpiDocs, k.docs.toString(), ChartColors.at(2))
        if (more.kpiAvg.isNotEmpty() && k.docs > 0) tiles += Triple(more.kpiAvg, money(k.avg), ChartColors.at(1))
        if (more.kpiClients.isNotEmpty() && k.clients > 0) tiles += Triple(more.kpiClients, k.clients.toString(), ChartColors.at(3))
        if (more.kpiPayments.isNotEmpty() && Metric.PAYMENTS in metrics) tiles += Triple(more.kpiPayments, money(k.payments), ChartColors.PAYMENTS)
        tiles.chunked(2).forEach { pair ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, (label, value, color) ->
                row.addView(tile(label, value, color), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i == 1) marginStart = Ui.dp(ctx, 8)
                    topMargin = Ui.dp(ctx, 8)
                })
            }
            if (pair.size == 1) row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = Ui.dp(ctx, 8) })
            kpiBox.addView(row)
        }
        if (more.noGoods.isNotEmpty() && k.sales > 0 && k.goodsLines == 0) kpiBox.addView(Ui.hint(ctx, more.noGoods, 6).apply { setTextColor(ChartColors.RETURNS) })
    }

    private fun tile(label: String, value: String, color: Int): View {
        val card = MaterialCardView(ctx).apply {
            radius = Ui.dp(ctx, 14).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(ColorStateList.valueOf(ChartColors.alpha(color, 0.16f)))
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(ctx, 12)
            setPadding(p, p, p, p)
        }
        col.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dot(color))
            addView(Ui.text(ctx, label, com.google.android.material.R.style.TextAppearance_Material3_LabelMedium).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(ctx, 6) }
            })
        })
        col.addView(Ui.text(ctx, value, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, 4).apply {
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        card.addView(col)
        return card
    }

    private fun dot(color: Int, size: Int = 10): View = View(ctx).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, size), Ui.dp(ctx, size))
    }

    /** Ingichka rangli chiziq (ulush/miqdor ko'rsatkichi). */
    private fun meter(frac: Double, color: Int): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = Ui.dp(ctx, 3).toFloat(); setColor(ChartColors.alpha(color, 0.15f)) }
        }
        val f = frac.coerceIn(0.0, 1.0).toFloat()
        box.addView(View(ctx).apply { background = GradientDrawable().apply { cornerRadius = Ui.dp(ctx, 3).toFloat(); setColor(color) } },
            LinearLayout.LayoutParams(0, Ui.dp(ctx, 6), f.coerceAtLeast(0.01f)))
        if (f < 1f) box.addView(View(ctx), LinearLayout.LayoutParams(0, Ui.dp(ctx, 6), 1f - f))
        box.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 6)).apply { bottomMargin = Ui.dp(ctx, 6) }
        return box
    }

    // ---------------- qo'shimcha bo'limlar (butun grafik davri bo'yicha) ----------------

    private fun renderMore(more: More, from: Long, to: Long) {
        // Ulush (halqa grafik): mijozlar, bo'lmasa tovarlar
        val clients = if (more.shareClients.isNotEmpty()) Stats.byClient(entries, from, to, rate) else emptyList()
        val share = clients.ifEmpty { if (more.shareGoods.isNotEmpty()) Stats.byGoods(entries, from, to, rate) else emptyList() }
        if (share.isNotEmpty()) {
            lists.addView(Ui.section(ctx, if (clients.isNotEmpty()) more.shareClients else more.shareGoods))
            val (c, col) = Ui.card(ctx)
            val total = share.sumOf { it.value }
            val donut = DonutChart(ctx).apply {
                values = share.map { it.value }
                center = money(total)
                centerSub = texts.periods[period.ordinal]
            }
            col.addView(donut)
            share.forEachIndexed { i, s ->
                val color = if (s.label.isEmpty()) ChartColors.OTHER else ChartColors.at(i)
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 2))
                }
                row.addView(dot(color, 12))
                row.addView(Ui.text(ctx, s.label.ifEmpty { more.other }).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(ctx, 8) })
                row.addView(Ui.text(ctx, pct(s.value / total * 100) + " · " + money(s.value), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge))
                col.addView(row)
            }
            lists.addView(c)
        }

        // Tez sotilayotgan tovarlar
        if (more.fast.isNotEmpty()) {
            val fast = Stats.fast(entries, from, to, rate, 10)
            if (fast.isNotEmpty()) {
                lists.addView(Ui.section(ctx, more.fast))
                val (c, col) = Ui.card(ctx)
                fast.forEachIndexed { i, f ->
                    if (i > 0) col.addView(Ui.divider(ctx))
                    val trend = f.trend?.let { (if (it >= 0) "▲ " else "▼ ") + pct(kotlin.math.abs(it)) }.orEmpty()
                    val v = Ui.item(ctx, "${i + 1}. ${f.product}", more.fastSub.format(f.days, fmtQty(f.perDay)), trend)
                    if (trend.isNotEmpty()) (v.getChildAt(v.childCount - 1) as? TextView)?.setTextColor(if ((f.trend ?: 0.0) >= 0) ChartColors.UP else ChartColors.DOWN)
                    col.addView(v)
                }
                lists.addView(c)
            }
        }

        // Hafta kunlari
        if (more.weekdays.isNotEmpty() && more.weekdayNames.size == 7) {
            val w = Stats.weekdays(entries, from, to, rate)
            if (w.any { it > 0 }) {
                lists.addView(Ui.section(ctx, more.weekdays))
                val (c, col) = Ui.card(ctx)
                val best = w.indices.maxByOrNull { w[it] } ?: 0
                val label = Ui.title(ctx, more.bestDay.format(more.weekdayNames[best]), 0)
                col.addView(label)
                col.addView(BarChart(ctx).apply {
                    compact = true
                    format = money
                    bars = w.mapIndexed { i, v -> more.weekdayNames[i] to v }
                    colorOf = { i -> if (i == best) ChartColors.at(1) else ChartColors.at(2) }
                    onSelect = { i -> label.text = more.weekdayNames[i] + ": " + money(w[i]) }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(ctx, 6) })
                lists.addView(c)
            }
        }

        // Soatlar (vaqti ma'lum savdolar)
        if (more.hours.isNotEmpty()) {
            val h = Stats.hours(entries, from, to, rate)
            val used = h.indices.filter { h[it] > 0 }
            if (used.size >= 2) {
                val a = used.first()
                val b = used.last()
                lists.addView(Ui.section(ctx, more.hours))
                val (c, col) = Ui.card(ctx)
                val best = (a..b).maxByOrNull { h[it] } ?: a
                val label = Ui.title(ctx, more.bestHour.format(two(best), two((best + 1) % 24)), 0)
                col.addView(label)
                col.addView(BarChart(ctx).apply {
                    compact = true
                    format = money
                    bars = (a..b).map { two(it) to h[it] }
                    colorOf = { i -> if (a + i == best) ChartColors.at(3) else ChartColors.at(5) }
                    onSelect = { i -> label.text = two(a + i) + ":00 — " + money(h[a + i]) }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(ctx, 6) })
                lists.addView(c)
            }
        }

        // To'xtab qolgan tovarlar
        if (more.idle.isNotEmpty()) {
            val idle = Stats.idle(entries, System.currentTimeMillis(), 14, 10)
            if (idle.isNotEmpty()) {
                lists.addView(Ui.section(ctx, more.idle))
                val (c, col) = Ui.card(ctx)
                idle.forEachIndexed { i, d ->
                    if (i > 0) col.addView(Ui.divider(ctx))
                    col.addView(Ui.item(ctx, d.product, more.idleSub.format(d.days, d.count), Stamp.formatDate(d.lastStamp)))
                }
                lists.addView(c)
            }
        }
    }

    private fun two(h: Int) = if (h < 10) "0$h" else h.toString()

    private fun pct(v: Double): String = (if (v >= 10 || v == 0.0) "%.0f" else "%.1f").format(java.util.Locale.US, v) + "%"

    private fun fmtQty(q: Double): String = if (q == Math.floor(q)) q.toLong().toString() else "%.2f".format(java.util.Locale.US, q).trimEnd('0').trimEnd('.')
}
