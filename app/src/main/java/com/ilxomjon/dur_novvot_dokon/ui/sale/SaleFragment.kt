package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentSaleBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.viewModel
import com.ilxomjon.dur_novvot_dokon.ui.UiBus
import uz.imsoft.shared.android.Ui

/**
 * v5.0 Savdo: eng tepada qidiruv va shtrix kod, tagida darhol tovarlar. Do'kon, narx turi, valyuta, savdo/qaytarish va
 * kelgan narx — "Sozlash" oynasida (ikonka yoki tepadagi yozuvni bosib). Oyna yopilgach kursor qidiruvga o'tadi.
 * Savdo pauza qilinmaydi — bosh sahifadan yangi savdo boshlanadi, boshlangani "Ochiq savdolar"da turadi.
 */
class SaleFragment : Fragment() {
    private var _binding: FragmentSaleBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SaleViewModel
    private lateinit var adapter: ProductAdapter

    private var lastDraft = DraftState()
    private var lastList = ListState()
    private var rendering = false
    private var autoPicked = false
    private var setupOpen = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSaleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel {
            SaleViewModel(ServiceLocator.catalog, ServiceLocator.draft, ServiceLocator.history, ServiceLocator.appPrefs, ServiceLocator.parked, ServiceLocator.kpi)
        }
        com.ilxomjon.dur_novvot_dokon.ui.share.Tour.show(this, com.ilxomjon.dur_novvot_dokon.ui.share.Tour.SALE)
        vm.setFilter(ListFilter.All)

        adapter = ProductAdapter(
            onPlus = { p, price, amount ->
                if (ServiceLocator.appPrefs.addMode == "ask" && vm.draft.value.lineFor(p.id) == null) {
                    vm.tap(); ItemEditSheet.show(childFragmentManager, p.id, false, vm.showCost.value)
                } else vm.plus(p, price, amount)
            },
            onMinus = { p, price -> vm.minus(p, price) },
            onEdit = { p, focusPrice -> vm.tap(); ItemEditSheet.show(childFragmentManager, p.id, focusPrice, vm.showCost.value) },
            onZero = { p ->
                val before = vm.draft.value.lines
                if (vm.zero(p)) {
                    Snackbar.make(binding.root, getString(R.string.sale_zeroed, p.name), Snackbar.LENGTH_LONG)
                        .setAction(R.string.cart_undo) { vm.undo(before) }.show()
                }
            },
        )
        adapter.haptics = ServiceLocator.appPrefs.fHaptics
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.addItemDecoration(
            com.google.android.material.divider.MaterialDividerItemDecoration(requireContext(), androidx.recyclerview.widget.RecyclerView.VERTICAL).apply {
                dividerInsetStart = (16 * resources.displayMetrics.density).toInt()
                isLastItemDecorated = false
            },
        )
        binding.recycler.setHasFixedSize(true)
        binding.recycler.setItemViewCacheSize(24)
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, bundle ->
            val id = bundle.getString(StorePickerSheet.KEY_ID).orEmpty()
            vm.storeById(id)?.let { vm.setStore(it) }
            focusSearch()
        }
        binding.btnSetup.setOnClickListener { openSetup() }
        binding.tvContext.setOnClickListener { openSetup() }
        binding.tvContext.setOnLongClickListener { lastDraft.store?.let { StoreCardSheet.show(childFragmentManager, it.id) }; true }
        binding.btnOtherStore.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_STORE) }

        binding.edtSearch.onTextChanged { vm.setQuery(it) }
        // v2.6: shtrix kod — kamera yoki ulanadigan skaner (klaviatura kabi yozib, Enter bosadi)
        binding.btnScan.setOnClickListener { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.ScanFragment()) }
        // Uzoq bosish — kod yozish rejimi (bazada yo'q shtrix kodlarni tovarlarga bog'lash)
        binding.btnScan.setOnLongClickListener { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.ScanFragment.link()); true }
        binding.edtSearch.setOnEditorActionListener { v, _, ev ->
            if (ev != null && ev.action != android.view.KeyEvent.ACTION_DOWN && ev.action != android.view.KeyEvent.ACTION_UP) return@setOnEditorActionListener false
            hardwareScan(v.text?.toString().orEmpty())
        }
        binding.edtSearch.setOnKeyListener { v, code, ev ->
            if ((code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) && ev.action == android.view.KeyEvent.ACTION_UP) {
                hardwareScan((v as android.widget.EditText).text?.toString().orEmpty())
            } else false
        }
        binding.btnCart.setOnClickListener { vm.tap(); navigator().push(CartFragment()) }
        binding.btnSmart.setOnClickListener { onSmart() }

        launchOnStarted { vm.list.collect { lastList = it; renderList(it) } }
        launchOnStarted { vm.draft.collect { lastDraft = it; renderDraft(it) } }
        launchOnStarted { vm.pricing.collect { adapter.setPricing(it, vm.showCost.value) } }
        launchOnStarted { vm.showCost.collect { on -> adapter.setPricing(vm.pricing.value, on) } }
        launchOnStarted { vm.suggestedQty.collect { adapter.setSuggested(it) } }
        launchOnStarted { vm.smart.collect { renderSmart(it) } }
        launchOnStarted { vm.favorites.collect { updateEmpty() } }
        launchOnStarted {
            vm.events.collect { e ->
                when (e) {
                    is SaleEvent.Repeated -> {
                        val msg = getString(R.string.sale_repeat_done, e.count) + if (e.missing > 0) "\n" + getString(R.string.sale_repeat_missing, e.missing) else ""
                        undoSnack(msg, e.before)
                    }
                    SaleEvent.NothingToRepeat -> Snackbar.make(binding.root, R.string.sale_repeat_none, Snackbar.LENGTH_SHORT).show()
                    is SaleEvent.AddedUsual -> undoSnack(getString(R.string.usual_added, e.count), e.before)
                    is SaleEvent.Cleared -> undoSnack(getString(R.string.cart_cleared), e.before)
                }
            }
        }
    }

    // ---------------- sozlash oynasi ----------------

    /** Do'kon, narx turi, valyuta, savdo/qaytarish va kelgan narx — bitta oynada. Yopilgach kursor qidiruvga o'tadi. */
    private fun openSetup() {
        if (setupOpen) return
        setupOpen = true
        val ctx = requireContext()
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(ctx, 20)
            setPadding(p, Ui.dp(ctx, 8), p, 0)
        }
        var pickStore = false
        val d = lastDraft
        col.addView(Ui.text(ctx, getString(R.string.sale_store_label), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge))
        col.addView(
            Ui.button(ctx, d.store?.name ?: getString(R.string.sale_select_store), outlined = true, top = 4, icon = R.drawable.ic_store) {
                pickStore = true
                (it.tag as? android.app.Dialog)?.dismiss()
            },
        )
        val fixed4 = d.store != null && ServiceLocator.shareStore.clients().any { it.storeId.isNotEmpty() && it.storeId == d.store?.id }
        col.addView(Ui.text(ctx, getString(R.string.sale_price_label), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 14))
        var level = d.level
        col.addView(
            Ui.toggle(
                ctx,
                listOf(getString(R.string.price_l1), getString(R.string.price_l2), getString(R.string.price_l3), getString(R.string.price_l4)),
                (d.level - 1).coerceIn(0, 3), 4,
            ) { i -> level = i + 1 },
        )
        // v5.0: 2-ilova mijoziga 4-narx tavsiya qilinadi, lekin boshqasiga o'tkazsa ham bo'ladi
        if (fixed4) col.addView(Ui.hint(ctx, getString(R.string.sale_price_branch), 4))
        col.addView(Ui.text(ctx, getString(R.string.sale_currency_label), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 14))
        var currency = d.currency
        col.addView(
            Ui.toggle(ctx, listOf(getString(R.string.currency_som), getString(R.string.currency_usd)), if (d.currency == Currency.USD) 1 else 0, 4) { i ->
                currency = if (i == 1) Currency.USD else Currency.SOM
            },
        )
        col.addView(Ui.text(ctx, getString(R.string.sale_mode_label), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 14))
        var mode = d.mode
        col.addView(
            Ui.toggle(ctx, listOf(getString(R.string.mode_sale), getString(R.string.mode_return)), if (d.mode == DealMode.RETURN) 1 else 0, 4) { i ->
                mode = if (i == 1) DealMode.RETURN else DealMode.SALE
            },
        )
        var cost = vm.showCost.value
        if (ServiceLocator.appPrefs.fCostSwitch) {
            col.addView(Ui.switch(ctx, getString(R.string.sale_cost_switch), cost, 14) { on -> cost = on })
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.sale_setup)
            .setView(col)
            .setPositiveButton(R.string.sale_setup_done, null)
            .setOnDismissListener {
                setupOpen = false
                if (pickStore) {
                    StorePickerSheet.show(childFragmentManager, REQ_STORE)
                } else {
                    applySetup(level, currency, mode, cost)
                    focusSearch()
                }
            }
            .show()
        // "Do'konni tanlash" tugmasi oynani yopishi uchun
        for (i in 0 until col.childCount) (col.getChildAt(i) as? com.google.android.material.button.MaterialButton)?.tag = dialog
    }

    private fun applySetup(level: Int, currency: Currency, mode: DealMode, cost: Boolean) {
        val d = lastDraft
        val priceChanged = level != d.level || currency != d.currency
        if (mode != d.mode) vm.setMode(mode)
        if (cost != vm.showCost.value) vm.setShowCost(cost)
        if (!priceChanged) return
        val apply = {
            if (level != lastDraft.level) vm.setPriceLevel(level)
            if (currency != lastDraft.currency) vm.setCurrency(currency)
        }
        if (d.lines.isEmpty()) apply() else confirmReprice(apply)
    }

    /** Oyna yopilgach: kursor qidiruvga, klaviatura ochiladi. */
    private fun focusSearch() {
        val b = _binding ?: return
        if (lastDraft.store == null) return
        b.edtSearch.post {
            val bb = _binding ?: return@post
            bb.edtSearch.requestFocus()
            runCatching {
                (requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showSoftInput(bb.edtSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    // ---------------- ro'yxat va holat ----------------

    private fun undoSnack(msg: String, before: List<com.ilxomjon.dur_novvot_dokon.domain.CartLine>) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).setAction(R.string.cart_undo) { vm.undo(before) }.show()
    }

    private fun renderSmart(a: SmartAction) {
        val on = vm.flags.fSmartButton
        binding.btnSmart.show(on && a !is SmartAction.None)
        if (!on) return
        binding.btnSmart.text = when (a) {
            SmartAction.PickStore -> getString(R.string.smart_pick_store)
            is SmartAction.AddUsual -> getString(R.string.smart_add_usual, a.count)
            SmartAction.Finish -> getString(R.string.smart_finish)
            is SmartAction.TakePayment -> getString(R.string.smart_payment, requireContext().money(a.debt, Currency.SOM))
            SmartAction.None -> ""
        }
    }

    private fun onSmart() {
        vm.tap()
        when (val a = vm.smart.value) {
            SmartAction.PickStore -> StorePickerSheet.show(childFragmentManager, REQ_STORE)
            is SmartAction.AddUsual -> vm.addAllSuggested()
            SmartAction.Finish -> if (lastDraft.store != null) navigator().push(CartFragment())
            is SmartAction.TakePayment -> {
                UiBus.paymentStore.value = lastDraft.store?.id
                navigator().openTab(R.id.nav_payment)
            }
            SmartAction.None -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reloadFavorites()
    }

    /** Qidiruv maydoniga skaner yozgan kod (Enter bilan). Kod emas — oddiy qidiruv bo'lib qoladi. */
    fun onHardwareScan(text: String): Boolean = _binding != null && hardwareScan(text)

    private fun hardwareScan(text: String): Boolean {
        if (!com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.looksScanned(text)) return false
        binding.edtSearch.setText("")
        com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.handle(this, text.trim(), onAdded = { a ->
            _binding?.let { b ->
                Snackbar.make(b.root, a.product.name + "\n" + com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.describe(requireContext(), a), Snackbar.LENGTH_SHORT)
                    .setAction(R.string.scan_undo) { com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.undo(a) }.show()
            }
        }, onIdle = { _binding?.edtSearch?.requestFocus() })
        return true
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) vm.reloadFavorites()
        super.onHiddenChanged(hidden)
        if (!hidden) com.ilxomjon.dur_novvot_dokon.ui.share.Tour.show(this, com.ilxomjon.dur_novvot_dokon.ui.share.Tour.SALE)
        if (hidden) autoPicked = false else maybeAutoSetup()
    }

    private fun confirmReprice(apply: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reprice_title)
            .setMessage(R.string.reprice_message)
            .setPositiveButton(R.string.ok) { _, _ -> apply() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** v5.0: "Savdo" bo'limiga kirilganda do'kon tanlanmagan bo'lsa — sozlash oynasi o'zi ochiladi. */
    private fun maybeAutoSetup() {
        if (autoPicked || isHidden || _binding == null) return
        if (lastList.loaded && lastList.hasProducts && lastDraft.store == null) {
            autoPicked = true
            StorePickerSheet.show(childFragmentManager, REQ_STORE)
        }
    }

    private fun renderList(s: ListState) {
        adapter.setHighlights(s.highlights)
        adapter.setProducts(s.products)
        updateEmpty()
    }

    private fun updateEmpty() {
        if (_binding == null) return
        val s = lastList
        val noStore = lastDraft.store == null
        val showList = !noStore && s.hasProducts && s.products.isNotEmpty()
        binding.recycler.show(showList)
        val needMessage = noStore || !s.hasProducts || s.products.isEmpty()
        binding.emptyState.show(needMessage)
        binding.tvEmpty.setText(
            when {
                !s.hasProducts -> R.string.home_no_data_text
                noStore -> R.string.sale_no_store
                else -> R.string.sale_empty_products
            },
        )
        val pick = noStore && s.hasProducts
        binding.searchBox.show(!pick)
        binding.tvContext.show(!pick)
        binding.btnOtherStore.show(pick)
    }

    private fun renderDraft(d: DraftState) {
        val b = binding
        val ctx = requireContext()
        rendering = true
        val parts = ArrayList<String>(4)
        parts += d.store?.name ?: getString(R.string.sale_select_store)
        parts += getString(
            when (d.level) {
                1 -> R.string.price_l1
                2 -> R.string.price_l2
                3 -> R.string.price_l3
                else -> R.string.price_l4
            },
        )
        parts += getString(if (d.currency == Currency.USD) R.string.currency_usd else R.string.currency_som)
        if (d.mode == DealMode.RETURN) parts += getString(R.string.mode_return)
        if (vm.showCost.value) parts += getString(R.string.sale_cost_switch)
        b.tvContext.text = parts.joinToString(" · ")
        b.tvWarning.show(lastList.hasProducts && vm.pricing.value.rate <= 0.0)

        adapter.setCart(d.lines.associateBy { it.productId })
        b.tvCount.text = getString(R.string.sale_items_count, d.itemCount)
        b.tvTotal.text = ctx.money(d.total, d.currency)
        b.btnCart.isEnabled = d.itemCount > 0
        rendering = false
        updateEmpty()
        maybeAutoSetup()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val REQ_STORE = "sale_store"
    }
}
