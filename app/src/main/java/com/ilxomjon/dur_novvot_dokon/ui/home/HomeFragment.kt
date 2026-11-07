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
                R.id.action_barcodes -> { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment()); true }
                R.id.action_menu -> { openMenu(); true }
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
        bindSlot(binding.btnSlot3, 3)
        bindSlot(binding.btnSlot4, 4)
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

    // ---------------- tepadagi 4 ta tugma va menyu ----------------

    /** Bosh sahifadagi tanlanadigan amal. */
    private class Slot(val key: String, val title: String, val icon: Int, val admin: Boolean, val go: () -> Unit)

    private fun slots(): List<Slot> {
        fun go(f: () -> androidx.fragment.app.Fragment): () -> Unit = { navigator().push(f()) }
        return listOf(
            Slot("return", getString(R.string.home_return), R.drawable.ic_return, false) { startDeal(DealMode.RETURN) },
            Slot("payment", getString(R.string.home_payment), R.drawable.ic_payment, false) { navigator().openTab(R.id.nav_payment) },
            Slot("report", getString(R.string.home_report), R.drawable.ic_report, false, go { com.ilxomjon.dur_novvot_dokon.ui.report.ReportFragment() }),
            Slot("dayend", getString(R.string.dayend_title), R.drawable.ic_report, false, go { DayEndFragment() }),
            Slot("barcodes", getString(R.string.bb_title), R.drawable.ic_barcode, false, go { com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment() }),
            Slot("queue", getString(R.string.queue_title), R.drawable.ic_sync, false, go { OutboxFragment() }),
            Slot("labels", getString(R.string.home_labels), R.drawable.ic_label, true, go { com.ilxomjon.dur_novvot_dokon.ui.labels.LabelsFragment() }),
            Slot("scale", getString(R.string.sc_title), R.drawable.ic_scale, true, go { com.ilxomjon.dur_novvot_dokon.ui.admin.ScaleFragment() }),
            Slot("priceset", getString(R.string.ps_title), R.drawable.ic_calculate, true, go { com.ilxomjon.dur_novvot_dokon.ui.admin.PriceSetFragment() }),
            Slot("rate", getString(R.string.rt_title), R.drawable.ic_currency, true, go { com.ilxomjon.dur_novvot_dokon.ui.admin.RateFragment() }),
            Slot("boss", getString(R.string.boss_title), R.drawable.ic_trending, true, go { com.ilxomjon.dur_novvot_dokon.ui.admin.BossFragment() }),
            Slot("stats", getString(R.string.stt_title), R.drawable.ic_trending, true, go { com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment() }),
            Slot("admin", getString(R.string.ad_title), R.drawable.ic_tune, true, go { com.ilxomjon.dur_novvot_dokon.ui.admin.AdminHubFragment() }),
            Slot("onecsales", getString(R.string.ad_sales), R.drawable.ic_receipt, true, go { com.ilxomjon.dur_novvot_dokon.ui.admin.OneCSalesFragment() }),
        ).filter { com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin || !it.admin }
    }

    /** 3- va 4-tugma: bosilsa ishlaydi, uzoq bosilsa boshqa amal tanlanadi. */
    private fun bindSlot(btn: com.google.android.material.button.MaterialButton, no: Int) {
        val prefs = ServiceLocator.appPrefs
        val list = slots()
        val key = if (no == 3) prefs.homeSlot3 else prefs.homeSlot4
        val slot = list.firstOrNull { it.key == key } ?: list.first()
        btn.text = slot.title
        btn.setIconResource(slot.icon)
        btn.setOnClickListener { slot.go() }
        btn.setOnLongClickListener {
            uz.imsoft.shared.android.Ui.menuSheet(
                requireContext(), getString(R.string.home_pick_action),
                list.map { s ->
                    uz.imsoft.shared.android.Ui.MenuRow(s.title, "", s.icon, if (s.key == key) 1 else 0) {
                        if (no == 3) prefs.homeSlot3 = s.key else prefs.homeSlot4 = s.key
                        if (_binding != null) bindSlot(btn, no)
                    }
                },
            )
            true
        }
    }

    /** Uch nuqta: Boshqaruv, Boss, Statistika, Profil, Sozlamalar — ikonkalari bilan. */
    private fun openMenu() {
        val admin = com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin
        val rows = ArrayList<uz.imsoft.shared.android.Ui.MenuRow>()
        if (admin) {
            rows += uz.imsoft.shared.android.Ui.MenuRow(getString(R.string.ad_title), getString(R.string.ad_card_sub), R.drawable.ic_tune, 1) {
                navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.AdminHubFragment())
            }
            rows += uz.imsoft.shared.android.Ui.MenuRow(getString(R.string.boss_title), getString(R.string.boss_card_sub), R.drawable.ic_trending, 1) {
                navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.BossFragment())
            }
            rows += uz.imsoft.shared.android.Ui.MenuRow(getString(R.string.stt_title), getString(R.string.sg_stats_sub), R.drawable.ic_trending) {
                navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment())
            }
        }
        rows += uz.imsoft.shared.android.Ui.MenuRow(getString(R.string.menu_settings), "", R.drawable.ic_settings) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.settings.SettingsHubFragment())
        }
        rows += uz.imsoft.shared.android.Ui.MenuRow(getString(R.string.pf_title), "", R.drawable.ic_person) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.settings.ProfileFragment())
        }
        uz.imsoft.shared.android.Ui.menuSheet(requireContext(), "", rows)
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
