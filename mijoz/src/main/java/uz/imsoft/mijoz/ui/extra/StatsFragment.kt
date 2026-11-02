package uz.imsoft.mijoz.ui.extra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.ledgerhost.MijozLedgerHost
import uz.imsoft.shared.android.StatsPanel
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.Stamp

/**
 * v4.0 Mijoz: statistika va "Olgan tovarlarim" — bizdan olgan tovarlar va to'lovlar (kun/hafta/oy/yil, eng ko'p olingan
 * tovarlar — yil bo'yicha tanlansa boshidan beri), narx o'zgarishlari; alohida — o'z savdolari.
 */
class StatsFragment : MFragment() {
    private lateinit var bought: StatsPanel
    private lateinit var own: StatsPanel

    private fun texts(metrics: List<String>, top: Int, more: StatsPanel.More) = StatsPanel.Texts(
        listOf(getString(R.string.stt_day), getString(R.string.stt_week), getString(R.string.stt_month), getString(R.string.stt_year)),
        metrics, getString(R.string.stt_selected), getString(R.string.stt_total), getString(top), getString(R.string.stt_prices),
        getString(R.string.stt_empty), getString(R.string.stt_change), { Stamp.formatDateTime(it) }, more,
    )

    private fun weekdays() = listOf(
        R.string.stt_wd_1, R.string.stt_wd_2, R.string.stt_wd_3, R.string.stt_wd_4, R.string.stt_wd_5, R.string.stt_wd_6, R.string.stt_wd_7,
    ).map { getString(it) }

    /** v4.0.1: bizdan olganlari — olingan summa, xaridlar, o'rtacha xarid, to'lovlar, tovarlar ulushi, tez-tez olinadiganlari. */
    private fun boughtMore() = StatsPanel.More(
        getString(R.string.stt_kpi_for), getString(R.string.stt_k_bought), "", getString(R.string.stt_k_buys), getString(R.string.stt_k_buy_avg),
        "", getString(R.string.stt_k_pay), "", getString(R.string.stt_share_bought), getString(R.string.stt_other),
        getString(R.string.stt_fast_bought), getString(R.string.stt_fast_bought_sub),
        getString(R.string.stt_weekdays), weekdays(), getString(R.string.stt_best_day),
        "", "", "", "", getString(R.string.stt_no_goods),
    )

    /** O'z savdolari — chek, o'rtacha chek, ulush, tez sotilayotgan, hafta kunlari, soatlar, to'xtab qolganlari. */
    private fun ownMore() = StatsPanel.More(
        getString(R.string.stt_kpi_for), getString(R.string.stt_k_sales), "", getString(R.string.stt_k_docs), getString(R.string.stt_k_avg),
        "", "", "", getString(R.string.stt_share_own), getString(R.string.stt_other),
        getString(R.string.stt_fast_own), getString(R.string.stt_fast_own_sub),
        getString(R.string.stt_weekdays), weekdays(), getString(R.string.stt_best_day),
        getString(R.string.stt_hours), getString(R.string.stt_best_hour),
        getString(R.string.stt_idle), getString(R.string.stt_idle_sub), "",
    )

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.stt_title)
        val c = p.content
        c.addView(Ui.section(ctx, getString(R.string.stt_bought)))
        c.addView(Ui.hint(ctx, getString(R.string.stt_bought_hint), 2))
        bought = StatsPanel(ctx, texts(listOf(getString(R.string.stt_bought_m), getString(R.string.stt_payments), getString(R.string.stt_returns), ""), R.string.stt_bought_top, boughtMore()),
            { som(it) }, listOf(StatsPanel.Metric.SALES, StatsPanel.Metric.PAYMENTS, StatsPanel.Metric.RETURNS))
        c.addView(bought.view)
        c.addView(Ui.section(ctx, getString(R.string.stt_own)))
        own = StatsPanel(ctx, texts(listOf(getString(R.string.stt_own_m), "", "", ""), R.string.stt_own_top, ownMore()), { som(it) }, listOf(StatsPanel.Metric.SALES))
        c.addView(own.view)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(MijozLedgerHost.ledgerData, Locator.pos.state) { l, p -> l to p }.collect { (l, p) ->
                    bought.setData(l.entries, 0.0)
                    // O'z savdolari (faqat shu telefonda) — chegirma tovarlarga mutanosib taqsimlanadi
                    val ownEntries = p.sales.flatMap { s ->
                        val gross = s.lines.sumOf { it.qty * it.price }
                        val k = if (gross > 0) (gross - s.discount) / gross else 1.0
                        s.lines.map { ln -> LedgerEntry(s.stamp, EntryKind.SALE, "own", ln.name, ln.qty, mapOf(Col.PRICE to Money2(ln.price), Col.SUM to Money2(ln.qty * ln.price * k))) }
                    }
                    own.setData(ownEntries, 0.0)
                }
            }
        }
    }
}
