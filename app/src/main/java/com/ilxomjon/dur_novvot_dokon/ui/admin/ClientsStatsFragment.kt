package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.DonutChart
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.report.PendingMerge
import uz.imsoft.shared.report.Stats

/**
 * v5.0.3: har mijozning o'z statistikasi. Ro'yxatda savdo, foyda, chek soni, o'rtacha chek, ulushi va oxirgi savdo;
 * mijoz bosilsa — uning to'liq statistikasi (grafiklar, tez sotilayotgan tovarlari, soatlari).
 * Hisob faqat savdo yozuvlari va oxirgi kirim narxlaridan.
 */
class ClientsStatsFragment : CodeFragment() {
    private lateinit var box: LinearLayout
    private var period = 2

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.stc_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.stc_sub), 4))
        c.addView(
            Ui.toggle(
                ctx,
                listOf(getString(R.string.stt_day), getString(R.string.stt_week), getString(R.string.stt_month), getString(R.string.stt_year)),
                period, 8,
            ) { i -> period = i; render() },
        )
        box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(box)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchOnStarted { DokonLedgerHost.dbData.collect { render() } }
    }

    private fun from(now: Long): Long {
        val c = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        when (period) {
            0 -> Unit
            1 -> c.add(java.util.Calendar.DAY_OF_MONTH, -7)
            2 -> c.add(java.util.Calendar.MONTH, -1)
            else -> c.add(java.util.Calendar.YEAR, -1)
        }
        return PendingMerge.stampOf(c.timeInMillis)
    }

    private fun render() {
        if (!::box.isInitialized) return
        box.removeAllViews()
        val rate = ServiceLocator.catalog.state.value.rate
        val now = System.currentTimeMillis()
        val list = Stats.clients(
            DokonLedgerHost.dbData.value.entries, from(now), PendingMerge.stampOf(now + 60_000L), rate,
            StatsFragment.costByName(rate),
        )
        if (list.isEmpty()) { box.addView(Ui.hint(ctx, getString(R.string.stc_none), 16)); return }
        val total = list.sumOf { it.sales }

        // Ulushlar — halqa grafik (eng katta 6 ta, qolgani "boshqa")
        val top = list.take(6)
        val other = total - top.sumOf { it.sales }
        val values = top.map { it.sales } + (if (other > 0.5) listOf(other) else emptyList())
        val donut = DonutChart(ctx).apply {
            this.values = values
            center = som(total)
            centerSub = getString(R.string.stt_sec_clients)
        }
        box.addView(donut, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(ctx, 8) })

        box.addView(Ui.section(ctx, getString(R.string.stt_sec_clients)))
        val (card, col) = Ui.card(ctx)
        list.forEachIndexed { i, cl ->
            if (i > 0) col.addView(Ui.divider(ctx))
            val share = if (total > 0) Math.round(cl.sales / total * 100).toInt() else 0
            val sub = buildString {
                append(getString(R.string.stc_row, cl.docs, som(cl.avg)))
                if (cl.profit != 0.0) append(" · ").append(getString(R.string.stc_margin, som(cl.profit), Math.round(cl.margin).toInt()))
                append(" · ").append(getString(R.string.stc_share, share))
                if (cl.lastStamp > 0) append(" · ").append(getString(R.string.stc_last, uz.imsoft.shared.report.Stamp.formatDate(cl.lastStamp)))
            }
            col.addView(Ui.item(ctx, cl.name, sub, som(cl.sales), R.drawable.ic_person, if (i == 0) 1 else 0) { navigator().push(StatsFragment.of(cl.name)) })
        }
        box.addView(card)
    }
}
