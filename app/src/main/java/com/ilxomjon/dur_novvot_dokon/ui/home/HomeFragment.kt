package com.ilxomjon.dur_novvot_dokon.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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
                R.id.action_settings -> { navigator().push(SettingsFragment()); true }
                else -> false
            }
        }
        binding.btnLoad.setOnClickListener { vm.sync() }
        binding.btnRetry.setOnClickListener { vm.retryPending() }
        binding.btnQueue.setOnClickListener { navigator().push(OutboxFragment()) }
        binding.btnDayEnd.setOnClickListener { navigator().push(DayEndFragment()) }
        binding.cardSale.setOnClickListener { startDeal(DealMode.SALE) }
        binding.cardReturn.setOnClickListener { startDeal(DealMode.RETURN) }
        binding.cardPayment.setOnClickListener { navigator().openTab(R.id.nav_payment) }
        binding.cardReport.setOnClickListener { navigator().openTab(R.id.nav_report) }
        binding.btnContinue.setOnClickListener { navigator().openTab(R.id.nav_sale) }

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

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun startDeal(mode: DealMode) {
        vm.startDeal(mode)
        navigator().openTab(R.id.nav_sale)
    }

    private fun render(s: HomeState) {
        binding.progress.showInvisible(s.syncing)
        binding.tvGreeting.text = getString(R.string.home_greeting, s.agentName)
        binding.tvPhone.text = s.phone
        binding.tvRate.text = if (s.rate > 0) getString(R.string.home_rate, requireContext().money(s.rate, Currency.SOM)) else getString(R.string.home_rate_missing)
        binding.tvSync.text = if (s.lastSyncAt > 0) getString(R.string.home_last_sync, formatDateTime(s.lastSyncAt)) else getString(R.string.home_never_synced)
        binding.cardNoData.show(s.loaded && !s.hasProducts && !s.syncing)
        binding.cardDraft.show(s.draftCount > 0)
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
