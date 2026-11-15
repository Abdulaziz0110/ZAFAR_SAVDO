package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uz.imsoft.shared.android.BarChart
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.report.PendingMerge
import uz.imsoft.shared.report.Stats

/**
 * v4.0 Admin "Boss": bir qarashda butun biznes — bugungi savdo, foyda, tushum; qarzlar; agentlar; tizim holati
 * (kompyuter, server, bulut, telefonlar, 1C'ga yozish navbati). Har 30 soniyada yangilanadi.
 */
class BossFragment : CodeFragment() {
    private lateinit var today: LinearLayout
    private lateinit var month: LinearLayout
    private lateinit var best: LinearLayout
    private lateinit var debts: LinearLayout
    private lateinit var agents: LinearLayout
    private lateinit var system: LinearLayout
    private val chart by lazy { BarChart(ctx) }

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.boss_title)
        val c = p.content
        fun block(title: Int): LinearLayout {
            c.addView(Ui.section(ctx, getString(title)))
            val (card, col) = Ui.card(ctx)
            c.addView(card)
            return col
        }
        // v5.0.3: hamma ko'rsatkichni tanlangan mijozsiz ham ko'rish mumkin
        c.addView(StatsFilter.view(ctx, { clientNames() }) { render() })
        today = block(R.string.boss_today)
        month = block(R.string.boss_month)
        best = block(R.string.stt_sec_clients)
        c.addView(
            Ui.buttonRow(
                ctx,
                Ui.button(ctx, getString(R.string.stt_title), outlined = true, icon = R.drawable.ic_trending) { navigator().push(StatsFragment()) },
                Ui.button(ctx, getString(R.string.stc_title), outlined = true, icon = R.drawable.ic_groups) { navigator().push(ClientsStatsFragment()) },
            ),
        )
        debts = block(R.string.boss_debts)
        agents = block(R.string.boss_agents)
        system = block(R.string.boss_system)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chart.format = { som(it) }
        launchOnStarted {
            while (isActive) {
                render()
                delay(30_000)
            }
        }
        launchOnStarted { DokonLedgerHost.dbData.collect { render() } }
        launchUi { runCatching { ServiceLocator.deviceAdmin.refresh() }; render() }
    }

    private fun row(col: LinearLayout, title: String, sub: String, value: String, icon: Int, tone: Int = 0, onClick: (() -> Unit)? = null) {
        if (col.childCount > 0) col.addView(Ui.divider(ctx))
        col.addView(Ui.item(ctx, title, sub, value, icon = icon, tone = tone, onClick = onClick?.let { f -> { _: View -> f() } }))
    }

    private fun render() {
        if (!::today.isInitialized) return
        val rate = ServiceLocator.catalog.state.value.rate
        val entries = StatsFilter.apply(DokonLedgerHost.dbData.value.entries)
        val cost = StatsFragment.costByName(rate)
        val now = System.currentTimeMillis()
        val day0 = PendingMerge.stampOf(startOfDay(now))
        val day1 = PendingMerge.stampOf(startOfDay(now) + 86_400_000L)
        val yday = PendingMerge.stampOf(startOfDay(now) - 86_400_000L)
        val t = Stats.totals(entries, day0, day1, rate, cost)
        val y = Stats.totals(entries, yday, day0, rate, cost)

        today.removeAllViews()
        row(today, getString(R.string.stt_sales), getString(R.string.boss_docs_vs, t.docs, som(y.sales)), som(t.sales), R.drawable.ic_receipt, 1)
        row(today, getString(R.string.stt_profit), getString(R.string.boss_profit_sub), som(t.profit), R.drawable.ic_trending, if (t.profit < 0) 3 else 0)
        row(today, getString(R.string.boss_income), getString(R.string.boss_income_sub), som(t.payments), R.drawable.ic_wallet)
        if (t.returns > 0) row(today, getString(R.string.stt_returns), "", som(t.returns), R.drawable.ic_assignment_return, 2)
        val s = Stats.series(entries, Stats.Period.DAY, now, rate, cost)
        if (chart.parent == null) today.addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(ctx, 8) })
        else { (chart.parent as? LinearLayout)?.removeView(chart); today.addView(chart) }
        chart.bars = s.map { it.label to it.sales }

        // Oy boshidan: savdo, foyda va foyda ulushi
        val m0 = PendingMerge.stampOf(startOfMonth(now))
        val m = Stats.kpi(entries, m0, day1, rate, cost)
        month.removeAllViews()
        row(month, getString(R.string.stt_sales), getString(R.string.boss_month_sub, m.docs, m.clients), som(m.sales), R.drawable.ic_receipt, 1)
        if (m.hasProfit) {
            val pct = if (m.sales > 0) Math.round(m.profit / m.sales * 100).toInt() else 0
            row(month, getString(R.string.stt_profit), getString(R.string.stc_margin, som(m.profit), pct), som(m.profit), R.drawable.ic_trending, if (m.profit < 0) 3 else 0)
        }
        row(month, getString(R.string.boss_income), "", som(m.payments), R.drawable.ic_wallet)
        if (m.returns > 0) row(month, getString(R.string.stt_returns), "", som(m.returns), R.drawable.ic_assignment_return, 2)
        row(month, getString(R.string.stt_k_avg), "", som(m.avg), R.drawable.ic_calculate)

        // Eng yaxshi mijozlar (oy boshidan)
        best.removeAllViews()
        val cl = Stats.clients(entries, m0, day1, rate, cost)
        val totalSales = cl.sumOf { it.sales }
        if (cl.isEmpty()) best.addView(Ui.hint(ctx, getString(R.string.stc_none), 0))
        cl.take(5).forEachIndexed { i, x ->
            val share = if (totalSales > 0) Math.round(x.sales / totalSales * 100).toInt() else 0
            row(best, x.name, getString(R.string.stc_row, x.docs, som(x.avg)) + " \u00b7 " + getString(R.string.stc_share, share), som(x.sales), R.drawable.ic_person, if (i == 0) 1 else 0) {
                navigator().push(StatsFragment.of(x.name))
            }
        }

        // Qarzlar (serverdan, oxirgi yangilangan)
        debts.removeAllViews()
        val stores = ServiceLocator.catalog.state.value.stores
        val owing = stores.filter { it.debtSom > 0 || it.debtUsd > 0 }.sortedByDescending { it.debtSom + it.debtUsd * rate }
        row(debts, getString(R.string.boss_debt_total, owing.size), getString(R.string.boss_debt_sub), money2(owing.sumOf { it.debtSom }, owing.sumOf { it.debtUsd }), R.drawable.ic_request_quote, 3) {
            navigator().push(PartiesFragment.of(PartiesFragment.CLIENTS))
        }
        // v4.0: qarz "yoshi" — oxirgi to'lovdan beri necha kun (30 kundan oshsa qizil)
        val lastPay = HashMap<String, Long>()
        for (e in entries) if (e.kind == uz.imsoft.shared.report.EntryKind.PAYMENT) {
            val sid = uz.imsoft.shared.report.LedgerOwner.of(e.kindText)?.first.orEmpty()
            if (sid.isNotEmpty() && (lastPay[sid] ?: 0L) < e.stamp) lastPay[sid] = e.stamp
        }
        owing.take(8).forEach { st ->
            val lp = lastPay[st.id]
            val days = lp?.let { ((now - PendingMerge.millisOf(it)) / 86_400_000L).toInt() }
            val sub = listOfNotNull(st.regionName.ifBlank { null }, if (days != null) getString(R.string.boss_paid_ago, days) else getString(R.string.boss_paid_never)).joinToString(" · ")
            row(debts, st.name, sub, money2(st.debtSom, st.debtUsd), R.drawable.ic_person, if (days == null || days > 30) 3 else 0)
        }

        // Agentlar (bugun, Admin orqali yozilganlar)
        agents.removeAllViews()
        val names = ServiceLocator.deviceAdmin.devices.value.mapNotNull { d -> d.assignment?.takeIf { it.agentId.isNotEmpty() }?.let { it.agentId to it.label } }.toMap()
        val day = ServiceLocator.agentStats.today.value
        if (day.isEmpty()) agents.addView(Ui.hint(ctx, getString(R.string.boss_agents_none), 0))
        day.forEach { a ->
            row(agents, names[a.agentId] ?: getString(R.string.boss_agent_id, a.agentId), getString(R.string.boss_agent_sub, a.docs, som(a.payments)), som(a.sales), R.drawable.ic_person)
        }

        // Tizim holati
        system.removeAllViews()
        val sh = ServiceLocator.share.state.value
        val pcOk = sh.pcAt > 0 && now - sh.pcAt < 30 * 60_000L && sh.pcOk
        row(system, getString(R.string.boss_pc), if (sh.pcAt > 0) getString(R.string.boss_last, formatDateTime(sh.pcAt)) + " · " + sh.pcStatus.take(80) else getString(R.string.boss_never),
            getString(if (pcOk) R.string.boss_ok else R.string.boss_bad), R.drawable.ic_computer, if (pcOk) 1 else 3) { navigator().push(PcFragment()) }
        val cat = ServiceLocator.catalog.state.value
        val srvOk = cat.lastSyncAt > 0 && now - cat.lastSyncAt < 3 * 3_600_000L
        row(system, getString(R.string.boss_server), if (cat.lastSyncAt > 0) getString(R.string.boss_last, formatDateTime(cat.lastSyncAt)) else getString(R.string.boss_never),
            getString(if (srvOk) R.string.boss_ok else R.string.boss_bad), R.drawable.ic_server, if (srvOk) 1 else 3)
        val cloud = ServiceLocator.shareStore.isCloudReady
        row(system, getString(R.string.boss_cloud), ServiceLocator.relayHost.stats.error.ifEmpty { getString(R.string.boss_cloud_sub) },
            getString(if (cloud && ServiceLocator.relayHost.stats.error.isEmpty()) R.string.boss_ok else R.string.boss_bad), R.drawable.ic_cloud_off, if (cloud) 1 else 3)
        val devs = ServiceLocator.deviceAdmin.devices.value
        val online = devs.count { now - it.seenAt < 15 * 60_000L }
        val crashes = devs.count { it.crashAt > 0 }
        row(system, getString(R.string.dv_title), getString(R.string.boss_devices_sub, online, devs.size) + if (crashes > 0) " · " + getString(R.string.boss_crashes, crashes) else "",
            "", R.drawable.ic_groups, if (crashes > 0) 3 else 0) { navigator().push(DevicesFragment()) }
        val w = ServiceLocator.onec.state.value.writes
        val wait = w.count { it.result == null }
        val bad = w.count { it.result?.ok == false }
        row(system, getString(R.string.ad_writes), getString(R.string.ad_writes_st, wait, bad), "", R.drawable.ic_sync_alt, if (bad > 0) 3 else 0) { navigator().push(WritesFragment()) }
    }

    private fun clientNames(): List<String> {
        val rate = ServiceLocator.catalog.state.value.rate
        return Stats.clients(DokonLedgerHost.dbData.value.entries, 0L, Long.MAX_VALUE, rate).map { it.name }.take(60)
    }

    private fun startOfMonth(ms: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = ms
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfDay(ms: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = ms; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}
