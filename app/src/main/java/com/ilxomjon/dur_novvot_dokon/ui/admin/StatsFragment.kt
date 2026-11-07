package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost
import uz.imsoft.shared.android.StatsPanel
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.report.Stamp

/** v4.0: statistika (Admin va Agent): kun/hafta/oy/yil grafiklari, eng ko'p sotilgan tovarlar, narx o'zgarishlari. */
class StatsFragment : CodeFragment() {
    private lateinit var panel: StatsPanel

    /** Bitta mijozning statistikasi uchun (Mijozlar ro'yxatidan ochilganda). */
    private val fixedClient: String get() = arguments?.getString(ARG_CLIENT).orEmpty()

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.stt_title)
        if (fixedClient.isNotEmpty()) p.toolbar.title = fixedClient
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.stt_hint), 4))
        if (AppRole.isAdmin && fixedClient.isEmpty()) {
            c.addView(StatsFilter.view(ctx, { clientNames() }) { refresh() })
            c.addView(
                Ui.button(ctx, getString(R.string.stc_title), outlined = true, top = 8, icon = R.drawable.ic_groups) {
                    navigator().push(ClientsStatsFragment())
                },
            )
        }
        val metrics = if (AppRole.isAdmin) StatsPanel.Metric.entries.toList()
        else listOf(StatsPanel.Metric.SALES, StatsPanel.Metric.PAYMENTS, StatsPanel.Metric.RETURNS)
        panel = StatsPanel(ctx, texts(this), { som(it) }, metrics)
        c.addView(panel.view)
        return p.root
    }

    private fun clientNames(): List<String> {
        val rate = ServiceLocator.catalog.state.value.rate
        val e = DokonLedgerHost.dbData.value.entries
        return uz.imsoft.shared.report.Stats.clients(e, 0L, Long.MAX_VALUE, rate).map { it.name }.take(60)
    }

    /** Ro'yxatni filtrga qarab qayta beradi. */
    private fun refresh() {
        if (!::panel.isInitialized) return
        val rate = ServiceLocator.catalog.state.value.rate
        val all = DokonLedgerHost.dbData.value.entries
        val e = when {
            fixedClient.isNotEmpty() -> uz.imsoft.shared.report.Stats.only(all, fixedClient, uz.imsoft.shared.report.Stats.Who.ONLY)
            AppRole.isAdmin -> StatsFilter.apply(all)
            else -> all
        }
        panel.setData(e, rate, costByName(rate))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchOnStarted { DokonLedgerHost.dbData.collect { refresh() } }
    }

    companion object {
        private const val ARG_CLIENT = "client"

        /** Bitta mijozning statistikasi. */
        fun of(name: String) = StatsFragment().apply { arguments = android.os.Bundle().apply { putString(ARG_CLIENT, name) } }

        fun texts(f: androidx.fragment.app.Fragment) = StatsPanel.Texts(
            listOf(f.getString(R.string.stt_day), f.getString(R.string.stt_week), f.getString(R.string.stt_month), f.getString(R.string.stt_year)),
            listOf(f.getString(R.string.stt_sales), f.getString(R.string.stt_payments), f.getString(R.string.stt_returns), f.getString(R.string.stt_profit)),
            f.getString(R.string.stt_selected), f.getString(R.string.stt_total), f.getString(R.string.stt_top), f.getString(R.string.stt_prices),
            f.getString(R.string.stt_empty), f.getString(R.string.stt_change), { Stamp.formatDateTime(it) },
            StatsPanel.More(
                f.getString(R.string.stt_kpi_for), f.getString(R.string.stt_k_sales), f.getString(R.string.stt_k_profit), f.getString(R.string.stt_k_docs),
                f.getString(R.string.stt_k_avg), f.getString(R.string.stt_k_clients), f.getString(R.string.stt_k_pay),
                f.getString(R.string.stt_share_clients), f.getString(R.string.stt_share_goods), f.getString(R.string.stt_other),
                f.getString(R.string.stt_fast), f.getString(R.string.stt_fast_sub),
                f.getString(R.string.stt_weekdays), weekdays(f), f.getString(R.string.stt_best_day),
                f.getString(R.string.stt_hours), f.getString(R.string.stt_best_hour),
                f.getString(R.string.stt_idle), f.getString(R.string.stt_idle_sub),
                f.getString(R.string.stt_no_goods),
            ),
        )

        fun weekdays(f: androidx.fragment.app.Fragment) = listOf(
            R.string.stt_wd_1, R.string.stt_wd_2, R.string.stt_wd_3, R.string.stt_wd_4, R.string.stt_wd_5, R.string.stt_wd_6, R.string.stt_wd_7,
        ).map { f.getString(it) }

        /** Tovar nomi -> kelgan narx (so'm): oxirgi kirim, bo'lmasa serverdagi. Foyda shu bilan. */
        fun costByName(rate: Double): (String) -> Double? {
            val m = HashMap<String, Double>()
            for (p in ServiceLocator.catalog.state.value.products) {
                val c = Pricing.costPrice(p, Currency.SOM, rate)
                if (c > 0) m[p.name.trim().lowercase()] = c
            }
            return { name -> m[name.trim().lowercase()] }
        }
    }
}
