package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentSaleBinding
import com.ilxomjon.dur_novvot_dokon.databinding.ItemGroupChipBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
import com.ilxomjon.dur_novvot_dokon.domain.Group
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.viewModel
import com.ilxomjon.dur_novvot_dokon.ui.UiBus

class SaleFragment : Fragment() {
    private var _binding: FragmentSaleBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SaleViewModel
    private lateinit var adapter: ProductAdapter

    private var lastDraft = DraftState()
    private var lastList = ListState()
    private var rendering = false
    private var shownGroupIds: List<String>? = null
    private var autoPicked = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSaleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel {
            SaleViewModel(ServiceLocator.catalog, ServiceLocator.draft, ServiceLocator.history, ServiceLocator.appPrefs, ServiceLocator.parked, ServiceLocator.kpi)
        }
        com.ilxomjon.dur_novvot_dokon.ui.share.Tour.show(this, com.ilxomjon.dur_novvot_dokon.ui.share.Tour.SALE)

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
                    com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.sale_zeroed, p.name), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
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
        binding.switchCost.show(ServiceLocator.appPrefs.fCostSwitch)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.setItemViewCacheSize(24)
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, bundle ->
            val id = bundle.getString(StorePickerSheet.KEY_ID).orEmpty()
            vm.storeById(id)?.let { vm.setStore(it) }
        }
        binding.rowStore.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_STORE) }
        // Do'kon kartasi (qarz, oxirgi buyurtma, to'lov) — uzoq bosilganda
        binding.rowStore.setOnLongClickListener { lastDraft.store?.let { StoreCardSheet.show(childFragmentManager, it.id) }; true }

        binding.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_cart_title)
                .setMessage(R.string.clear_cart_message)
                .setPositiveButton(R.string.delete) { _, _ -> vm.clearCart() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.btnRepeat.setOnClickListener { vm.repeatLast() }
        binding.btnAddSuggested.setOnClickListener { vm.addAllSuggested() }
        binding.btnFav0.setOnClickListener { vm.favorites.value.getOrNull(0)?.let { vm.setStore(it) } }
        binding.btnFav1.setOnClickListener { vm.favorites.value.getOrNull(1)?.let { vm.setStore(it) } }
        binding.btnOtherStore.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_STORE) }
        // v3.1: qidirayotganda (klaviatura ochiq) — narx/rejim qatorlari va pastki jami yashiriladi, ro'yxatga joy qoladi
        // (insets tinglovchisi o'rnatilmaydi — tizim chetlari ishlovini buzmaslik uchun; joylashuv o'zgarganda tekshiriladi)
        val imeWatch = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val bb = _binding ?: return@OnGlobalLayoutListener
            val ime = androidx.core.view.ViewCompat.getRootWindowInsets(bb.root)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            setSearchCompact(ime && bb.edtSearch.hasFocus())
        }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(imeWatch)
        viewLifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                _binding?.root?.viewTreeObserver?.removeOnGlobalLayoutListener(imeWatch)
            }
        })
        binding.edtSearch.setOnFocusChangeListener { _, has ->
            val ime = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            setSearchCompact(has && ime)
        }

        binding.toggleType.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            val level = levelOfButton(id)
            if (level != lastDraft.level) confirmReprice { vm.setPriceLevel(level) }
        }
        binding.toggleCurrency.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            val cur = if (id == R.id.btnUsd) Currency.USD else Currency.SOM
            if (cur != lastDraft.currency) confirmReprice { vm.setCurrency(cur) }
        }
        binding.toggleMode.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            val mode = if (id == R.id.btnReturn) DealMode.RETURN else DealMode.SALE
            if (mode != lastDraft.mode) vm.setMode(mode)
        }
        binding.switchCost.setOnCheckedChangeListener { _, on -> if (!rendering) vm.setShowCost(on) }

        binding.chipGroups.setOnCheckedStateChangeListener { group, checkedIds ->
            if (rendering) return@setOnCheckedStateChangeListener
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            vm.setFilter(chip?.tag as? ListFilter ?: ListFilter.All)
        }
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
        binding.btnPark.setOnClickListener {
            vm.park()
            Snackbar.make(binding.root, R.string.park_done, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnParked.setOnClickListener { ParkedSheet.show(childFragmentManager) }

        launchOnStarted { vm.list.collect { lastList = it; renderList(it) } }
        launchOnStarted { vm.draft.collect { lastDraft = it; renderDraft(it) } }
        launchOnStarted { vm.pricing.collect { adapter.setPricing(it, vm.showCost.value) } }
        launchOnStarted { vm.showCost.collect { on -> adapter.setPricing(vm.pricing.value, on); setSwitch(on) } }
        launchOnStarted { vm.canRepeat.collect { binding.btnRepeat.show(it) } }
        launchOnStarted { vm.suggestedQty.collect { adapter.setSuggested(it) } }
        launchOnStarted { vm.suggestionList.collect { list -> adapter.setExplanations(if (vm.flags.fExplainSuggest) SuggestText.map(requireContext(), list) else emptyMap()) } }
        launchOnStarted { vm.smart.collect { renderSmart(it) } }
        launchOnStarted { vm.parkedCount.collect { renderParked(it) } }
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
            SmartAction.Finish -> if (lastDraft.store != null) FinishSheet.show(childFragmentManager)
            is SmartAction.TakePayment -> {
                UiBus.paymentStore.value = lastDraft.store?.id
                navigator().openTab(R.id.nav_payment)
            }
            SmartAction.None -> Unit
        }
    }

    private fun renderParked(n: Int) {
        val on = vm.flags.fMultiCart
        binding.btnParked.show(on && n > 0)
        binding.btnParked.text = getString(R.string.parked_chip, n)
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
                com.google.android.material.snackbar.Snackbar.make(b.root, a.product.name + "\n" + com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.describe(requireContext(), a), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .setAction(R.string.scan_undo) { com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.undo(a) }.show()
            }
        }, onIdle = { _binding?.edtSearch?.requestFocus() })
        return true
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) vm.reloadFavorites()
        super.onHiddenChanged(hidden)
        if (!hidden) com.ilxomjon.dur_novvot_dokon.ui.share.Tour.show(this, com.ilxomjon.dur_novvot_dokon.ui.share.Tour.SALE)
        if (hidden) autoPicked = false else maybeAutoPickStore()
    }

    private var compact = false

    private fun setSearchCompact(on: Boolean) {
        val bb = _binding ?: return
        if (on == compact) return
        compact = on
        bb.rowPrice.show(!on)
        bb.rowMode.show(!on)
        bb.bottomBar.show(!on)
        if (!on) updateEmpty()
    }

    private fun levelOfButton(id: Int): Int = when (id) {
        R.id.btnP1 -> 1
        R.id.btnP2 -> 2
        R.id.btnCash -> 3
        else -> 4
    }

    private fun buttonOfLevel(level: Int): Int = when (level) {
        1 -> R.id.btnP1
        2 -> R.id.btnP2
        3 -> R.id.btnCash
        else -> R.id.btnWholesale
    }

    private fun confirmReprice(apply: () -> Unit) {
        if (lastDraft.lines.isEmpty()) { apply(); return }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reprice_title)
            .setMessage(R.string.reprice_message)
            .setPositiveButton(R.string.ok) { _, _ -> apply() }
            .setNegativeButton(R.string.cancel) { _, _ -> renderDraft(lastDraft) }
            .setOnCancelListener { renderDraft(lastDraft) }
            .show()
    }

    private fun maybeAutoPickStore() {
        if (autoPicked || isHidden || _binding == null) return
        if (lastList.loaded && lastList.hasProducts && lastDraft.store == null) {
            autoPicked = true
            StorePickerSheet.show(childFragmentManager, REQ_STORE)
        }
    }

    private fun setSwitch(on: Boolean) {
        rendering = true
        if (binding.switchCost.isChecked != on) binding.switchCost.isChecked = on
        rendering = false
    }

    /** Ro'yxat: qidiruv/filtr/katalog o'zgarganda. */
    private fun renderList(s: ListState) {
        adapter.setHighlights(s.highlights)
        adapter.setProducts(s.products)
        binding.tvApprox.show(s.searching && s.approximate)
        renderGroups(s.groups, s.filter)
        val showSuggest = s.filter == ListFilter.Suggested && s.products.isNotEmpty()
        binding.suggestBar.show(showSuggest)
        if (showSuggest) binding.tvSuggest.text = getString(R.string.sale_suggest_bar, s.products.size)
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
                s.filter == ListFilter.Suggested && !s.searching -> R.string.frequent_empty
                s.filter == ListFilter.InCart && !s.searching -> R.string.incart_empty
                else -> R.string.sale_empty_products
            },
        )
        // v3.1: do'kon tanlanmagan — orqa fon bo'sh (qidiruv va filtrlar yashirin), faqat "Do'kon tanlash"; tanlash oynasi o'zi ochiladi
        val pick = noStore && s.hasProducts
        binding.searchBox.show(!pick)
        binding.chipScroll.show(!pick)
        binding.btnFav0.show(false)
        binding.btnFav1.show(false)
        binding.btnOtherStore.show(pick)
    }

    /** Sarlavha, savatcha va pastki panel: savatcha har o'zgarganda (faqat o'zgargan qatorlar yangilanadi). */
    private fun renderDraft(d: DraftState) {
        val b = binding
        val ctx = requireContext()
        rendering = true

        val store = d.store
        b.tvStoreName.text = store?.name ?: getString(R.string.sale_select_store)
        val info = ArrayList<String>()
        store?.regionName?.takeIf { it.isNotEmpty() }?.let { info += it }
        store?.let { st -> StorePickerSheet.debtText(ctx, st).takeIf { it.isNotEmpty() }?.let { info += it } }
        b.tvStoreInfo.text = info.joinToString(" · ")
        b.tvStoreInfo.show(info.isNotEmpty())

        b.toggleType.check(buttonOfLevel(d.level))
        // v3.1: 2-filial mijozlariga (Mijozlar ro'yxatidagi do'konlar) narx doim 4 (optom) — almashtirib bo'lmaydi
        val fixed4 = store != null && ServiceLocator.shareStore.clients().any { it.storeId.isNotEmpty() && it.storeId == store.id }
        for (i in 0 until b.toggleType.childCount) b.toggleType.getChildAt(i).isEnabled = !fixed4
        if (fixed4 && d.level != 4) b.root.post { vm.setPriceLevel(4) }
        b.toggleCurrency.check(if (d.currency == Currency.USD) R.id.btnUsd else R.id.btnSom)
        b.toggleMode.check(if (d.mode == DealMode.RETURN) R.id.btnReturn else R.id.btnSale)
        b.btnClear.show(d.lines.isNotEmpty())
        b.btnPark.show(d.lines.isNotEmpty() && vm.flags.fMultiCart)
        b.tvWarning.show(lastList.hasProducts && vm.pricing.value.rate <= 0.0)

        adapter.setCart(d.lines.associateBy { it.productId })

        b.tvCount.text = getString(R.string.sale_items_count, d.itemCount)
        b.tvTotal.text = ctx.money(d.total, d.currency)
        b.btnCart.isEnabled = d.itemCount > 0
        rendering = false
        updateEmpty()
        maybeAutoPickStore()
    }

    private fun renderGroups(groups: List<Group>, selected: ListFilter) {
        val ids = groups.map { it.id }
        val chips = binding.chipGroups
        rendering = true
        if (ids != shownGroupIds) {
            shownGroupIds = ids
            chips.removeAllViews()
            addChip(ListFilter.All, getString(R.string.group_all))
            addChip(ListFilter.Suggested, getString(R.string.filter_frequent))
            addChip(ListFilter.InCart, getString(R.string.filter_in_cart))
            groups.forEach { addChip(ListFilter.OfGroup(it.id), it.name) }
        }
        for (i in 0 until chips.childCount) {
            val chip = chips.getChildAt(i) as Chip
            if (chip.tag == selected) { if (!chip.isChecked) chips.check(chip.id); break }
        }
        rendering = false
    }

    private fun addChip(filter: ListFilter, title: String) {
        val chip = ItemGroupChipBinding.inflate(layoutInflater, binding.chipGroups, false).root
        chip.id = View.generateViewId()
        chip.tag = filter
        chip.text = title
        binding.chipGroups.addView(chip)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        shownGroupIds = null
    }

    private companion object {
        const val REQ_STORE = "sale_store"
    }
}
