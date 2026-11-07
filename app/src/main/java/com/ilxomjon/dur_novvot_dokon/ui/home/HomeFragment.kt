package com.ilxomjon.dur_novvot_dokon.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentHomeBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.settings.SettingsFragment
import com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxFragment
import com.ilxomjon.dur_novvot_dokon.ui.report.DayEndFragment
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import com.ilxomjon.dur_novvot_dokon.ui.viewModel

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: HomeViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { HomeViewModel(ServiceLocator.catalog, ServiceLocator.draft, ServiceLocator.history, ServiceLocator.outbox) }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sync -> { vm.sync(); true }
                R.id.action_boss -> { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.BossFragment()); true }
                R.id.action_stats -> { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment()); true }
                R.id.action_barcodes -> { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment()); true }
                R.id.action_profile -> { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.settings.ProfileFragment()); true }
                else -> false
            }
        }
        binding.btnLoad.setOnClickListener { vm.sync() }
        binding.btnRetry.setOnClickListener { vm.retryPending() }
        binding.btnQueue.setOnClickListener { navigator().push(OutboxFragment()) }
        binding.btnDayEnd.setOnClickListener { navigator().push(DayEndFragment()) }
        binding.cardSale.setOnClickListener { startDeal(DealMode.SALE) }
        binding.cardScan.setOnClickListener {
            startDeal(DealMode.SALE)
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.ScanFragment())
        }
        binding.cardReturn.setOnClickListener { startDeal(DealMode.RETURN) }
        // v5.0: "To'lov" pastki bo'limda bor — bosh sahifadan olib tashlandi (takrorlanmasin)
        binding.cardPayment.show(false)
        binding.cardReport.setOnClickListener { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.report.ReportFragment()) }
        // Yorliqlar — Admin ishi (Boshqaruv ▸ Tovarlar)
        binding.cardLabels.show(com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin)
        binding.cardLabels.setOnClickListener { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.labels.LabelsFragment()) }
        // v5.0: Boss va statistika — faqat Admin menyusida
        binding.toolbar.menu.findItem(R.id.action_boss)?.isVisible = com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin
        binding.toolbar.menu.findItem(R.id.action_stats)?.isVisible = com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin
        // Ism va telefon "Men" ekranida (bosh sahifada ish qoladi)
        binding.tvGreeting.show(false)
        binding.tvPhone.show(false)
        binding.cardLedger.setOnClickListener {
            if (com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.share.state.value.newOrders > 0) {
                com.ilxomjon.dur_novvot_dokon.ui.share.OrdersSheet.show(childFragmentManager)
                return@setOnClickListener
            }
            val hist = parentFragmentManager.findFragmentByTag("tab_" + R.id.nav_history) as? com.ilxomjon.dur_novvot_dokon.ui.history.HistoryFragment
            com.ilxomjon.dur_novvot_dokon.ui.history.HistoryFragment.open1C(navigator(), hist)
        }
        launchOnStarted {
            com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.share.state.collect { st ->
                val bb = _binding ?: return@collect
                bb.cardLedger.show((st.clientId.isNotEmpty() && st.entries > 0) || st.newOrders > 0)
                bb.tvLedgerTitle.text = if (st.newOrders > 0) getString(R.string.home_orders_title) else getString(R.string.home_ledger_title, st.clientName)
                bb.tvLedgerInfo.text = when {
                    st.newOrders > 0 -> getString(R.string.ord_banner, st.newOrders)
                    st.coverage.isNotEmpty() -> getString(R.string.home_ledger_info, uz.imsoft.shared.report.Stamp.formatDate(st.coverage.maxOf { it.to }))
                    else -> ""
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch { runCatching { com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.share.load() } }
        com.ilxomjon.dur_novvot_dokon.ui.share.Tour.show(this, com.ilxomjon.dur_novvot_dokon.ui.share.Tour.HOME)
        binding.btnContinue.setOnClickListener { navigator().openTab(R.id.nav_sale) }
        addCartsCard()
        if (com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin) addAdminCard()

        launchOnStarted { vm.state.collect { render(it) } }
        launchOnStarted {
            vm.events.collect { e ->
                when (e) {
                    HomeEvent.SyncOk -> Snackbar.make(binding.root, R.string.sync_ok, Snackbar.LENGTH_SHORT).show()
                    is HomeEvent.SyncFailed -> Snackbar.make(
                        binding.root, getString(R.string.sync_failed) + "\n" + requireContext().describeError(e.cause), Snackbar.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    /** v3.2 Admin: "Boshqaruv" kartasi (1C, ta'minotchilar, mijoz so'rovlari, kompyuter) — Savdo kartasi ustida. */
    private fun addAdminCard() {
        val parent = binding.cardSale.parent as? android.view.ViewGroup ?: return
        if (parent.findViewWithTag<View>("admin_card") != null) return
        val ctx = requireContext()
        // v4.0 "Boss": bugungi savdo, foyda, tushum va tizim holati — bir bosishda batafsil
        val (bossCard, bossCol) = uz.imsoft.shared.android.Ui.card(ctx, 0)
        bossCard.tag = "boss_card"
        val bossItem = uz.imsoft.shared.android.Ui.item(ctx, getString(R.string.boss_title), getString(R.string.boss_card_sub), icon = R.drawable.ic_trending, tone = 1) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.BossFragment())
        }
        bossCol.addView(bossItem)
        val bossLine = uz.imsoft.shared.android.Ui.title(ctx, "", 4)
        bossCol.addView(bossLine)
        val lp0 = binding.cardSale.layoutParams
        parent.addView(bossCard, parent.indexOfChild(binding.cardSale), android.view.ViewGroup.MarginLayoutParams(lp0.width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            (lp0 as? android.view.ViewGroup.MarginLayoutParams)?.let { setMargins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin) }
        })
        launchOnStarted {
            com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost.dbData.collect { d ->
                val rate = ServiceLocator.catalog.state.value.rate
                val now = System.currentTimeMillis()
                val c0 = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis
                val t = uz.imsoft.shared.report.Stats.totals(d.entries, uz.imsoft.shared.report.PendingMerge.stampOf(c0), uz.imsoft.shared.report.PendingMerge.stampOf(now + 60_000L), rate,
                    com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment.costByName(rate))
                bossLine.text = getString(R.string.boss_line, ctx.money(t.sales, com.ilxomjon.dur_novvot_dokon.domain.Currency.SOM),
                    ctx.money(t.profit, com.ilxomjon.dur_novvot_dokon.domain.Currency.SOM), ctx.money(t.payments, com.ilxomjon.dur_novvot_dokon.domain.Currency.SOM))
            }
        }
        val (card, col) = uz.imsoft.shared.android.Ui.card(ctx, 0)
        card.tag = "admin_card"
        val badge = uz.imsoft.shared.android.Ui.hint(ctx, "", 0)
        col.addView(uz.imsoft.shared.android.Ui.item(ctx, getString(R.string.ad_title), getString(R.string.ad_card_sub), icon = R.drawable.ic_tune, tone = 1) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.AdminHubFragment())
        })
        col.addView(badge)
        val lp = binding.cardSale.layoutParams
        parent.addView(card, parent.indexOfChild(binding.cardSale), android.view.ViewGroup.MarginLayoutParams(lp.width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            (lp as? android.view.ViewGroup.MarginLayoutParams)?.let { setMargins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin) }
        })
        launchOnStarted {
            kotlinx.coroutines.flow.combine(ServiceLocator.clientRequests.state, ServiceLocator.onec.state) { r, o -> r.newCount to o.writes.count { it.result == null } }
                .collect { (req, wr) ->
                    val parts = ArrayList<String>()
                    if (req > 0) parts += getString(R.string.ad_requests_new, req)
                    if (wr > 0) parts += getString(R.string.ad_writes_wait, wr)
                    badge.text = parts.joinToString(" · ")
                    badge.show(parts.isNotEmpty())
                }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /**
     * v5.0: bir vaqtda bir nechta savdo. Yangi savdo boshlanganda ochiq savdo o'zi saqlanadi va
     * "Ochiq savdolar" ro'yxatida turadi — istagan paytda qaytib davom ettiriladi.
     */
    private fun startDeal(mode: DealMode) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { if (ServiceLocator.draft.state.value.lines.isNotEmpty()) ServiceLocator.parked.parkActive() }
            vm.startDeal(mode)
            navigator().openTab(R.id.nav_sale)
        }
    }

    /** Ochiq savdolar ro'yxati: hozirgisi va saqlanganlari. Bosilsa — o'sha savdo davom etadi. */
    private fun addCartsCard() {
        val parent = binding.cardSale.parent as? android.view.ViewGroup ?: return
        val ctx = requireContext()
        val (card, col) = uz.imsoft.shared.android.Ui.card(ctx, 0)
        card.tag = "carts_card"
        val lp0 = binding.cardSale.layoutParams
        parent.addView(card, parent.indexOfChild(binding.cardSale), android.view.ViewGroup.MarginLayoutParams(lp0.width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            (lp0 as? android.view.ViewGroup.MarginLayoutParams)?.let { setMargins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin) }
        })
        launchOnStarted {
            kotlinx.coroutines.flow.combine(ServiceLocator.parked.items, ServiceLocator.draft.state) { p, d -> p to d }.collect { (parkedList, d) ->
                col.removeAllViews()
                val any = parkedList.isNotEmpty() || d.lines.isNotEmpty()
                card.show(any)
                if (!any) return@collect
                col.addView(uz.imsoft.shared.android.Ui.section(ctx, getString(R.string.carts_title)))
                if (d.lines.isNotEmpty()) {
                    col.addView(
                        uz.imsoft.shared.android.Ui.item(
                            ctx, d.store?.name ?: getString(R.string.sale_select_store),
                            getString(R.string.carts_now, d.itemCount), ctx.money(d.total, d.currency),
                            icon = R.drawable.ic_cart, tone = 1,
                        ) { navigator().openTab(R.id.nav_sale) },
                    )
                }
                for (p in parkedList) {
                    col.addView(uz.imsoft.shared.android.Ui.divider(ctx))
                    col.addView(
                        uz.imsoft.shared.android.Ui.item(
                            ctx, p.storeName.ifBlank { getString(R.string.sale_select_store) },
                            getString(R.string.carts_saved, p.lines.size, formatDateTime(p.created)), ctx.money(p.total, p.currency),
                            icon = R.drawable.ic_pause,
                        ) { resumeCart(p.id) },
                    )
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch { runCatching { ServiceLocator.parked.refresh() } }
    }

    private fun resumeCart(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { if (ServiceLocator.draft.state.value.lines.isNotEmpty()) ServiceLocator.parked.parkActive() }
            val missing = runCatching { ServiceLocator.parked.resume(id) }.getOrNull()
            if (missing != null && missing > 0) {
                Snackbar.make(binding.root, getString(R.string.sale_repeat_missing, missing), Snackbar.LENGTH_LONG).show()
            }
            navigator().openTab(R.id.nav_sale)
        }
    }

    private fun render(s: HomeState) {
        binding.progress.showInvisible(s.syncing)
        binding.tvGreeting.text = getString(R.string.home_greeting, s.agentName)
        binding.tvPhone.text = s.phone
        binding.tvRate.text = if (s.rate > 0) getString(R.string.home_rate, requireContext().money(s.rate, Currency.SOM)) else getString(R.string.home_rate_missing)
        binding.tvSync.text = if (s.lastSyncAt > 0) getString(R.string.home_last_sync, formatDateTime(s.lastSyncAt)) else getString(R.string.home_never_synced)
        binding.cardNoData.show(s.loaded && !s.hasProducts && !s.syncing)
        binding.cardDraft.show(false)
        if (s.draftCount > 0) {
            binding.tvDraftStore.text = s.draftStore ?: getString(R.string.sale_select_store)
            binding.tvDraftInfo.text = getString(R.string.home_resume_info, s.draftCount, requireContext().money(s.draftTotal, s.draftCurrency))
        }
        val ctx = requireContext()
        val orderSums = ArrayList<String>()
        if (s.today.ordersSom > 0.0) orderSums += ctx.money(s.today.ordersSom, Currency.SOM)
        if (s.today.ordersUsd > 0.0) orderSums += ctx.money(s.today.ordersUsd, Currency.USD)
        binding.tvTodayOrders.text = getString(R.string.home_today_orders, s.today.orders, orderSums.ifEmpty { listOf("0") }.joinToString(" + "))
        binding.tvTodayPayments.text = getString(R.string.home_today_payments, s.today.payments, ctx.money(s.today.paymentsSum, Currency.SOM))
        binding.cardPending.show(s.pending > 0)
        binding.tvPendingTitle.text = getString(R.string.home_pending_title, s.pending)
        binding.tvPendingHint.text = if (s.needsCheck > 0) getString(R.string.home_pending_needs_check, s.needsCheck)
        else getString(R.string.home_pending_hint)
        binding.toolbar.menu.findItem(R.id.action_sync)?.isEnabled = !s.syncing
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
