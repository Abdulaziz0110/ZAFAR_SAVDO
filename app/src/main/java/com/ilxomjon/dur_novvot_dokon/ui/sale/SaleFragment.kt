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
        vm = viewModel { SaleViewModel(ServiceLocator.catalog, ServiceLocator.draft, ServiceLocator.history, ServiceLocator.appPrefs) }

        adapter = ProductAdapter(
            onPlus = { p, price, amount -> vm.plus(p, price, amount) },
            onMinus = { p, price -> vm.minus(p, price) },
            onEdit = { p, focusPrice -> ItemEditSheet.show(childFragmentManager, p.id, focusPrice, vm.showCost.value) },
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
        binding.recycler.setItemViewCacheSize(24)
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, bundle ->
            val id = bundle.getString(StorePickerSheet.KEY_ID).orEmpty()
            vm.storeById(id)?.let { vm.setStore(it) }
        }
        binding.rowStore.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_STORE) }

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

        binding.toggleType.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            val type = if (id == R.id.btnCash) PriceType.CASH else PriceType.WHOLESALE
            if (type != lastDraft.priceType) confirmReprice { vm.setPriceType(type) }
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
        binding.btnCart.setOnClickListener { navigator().push(CartFragment()) }

        launchOnStarted { vm.list.collect { lastList = it; renderList(it) } }
        launchOnStarted { vm.draft.collect { lastDraft = it; renderDraft(it) } }
        launchOnStarted { vm.pricing.collect { adapter.setPricing(it, vm.showCost.value) } }
        launchOnStarted { vm.showCost.collect { on -> adapter.setPricing(vm.pricing.value, on); setSwitch(on) } }
        launchOnStarted { vm.canRepeat.collect { binding.btnRepeat.show(it) } }
        launchOnStarted { vm.suggestedQty.collect { adapter.setSuggested(it) } }
        launchOnStarted { vm.favorites.collect { updateEmpty() } }
        launchOnStarted {
            vm.events.collect { e ->
                val msg = when (e) {
                    is SaleEvent.Repeated -> getString(R.string.sale_repeat_done, e.count)
                    SaleEvent.NothingToRepeat -> getString(R.string.sale_repeat_none)
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reloadFavorites()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) vm.reloadFavorites()
        super.onHiddenChanged(hidden)
        if (hidden) autoPicked = false else maybeAutoPickStore()
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
        if (lastList.loaded && lastList.hasProducts && lastDraft.store == null && vm.favorites.value.isEmpty()) {
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
        adapter.setProducts(s.products)
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
        // Tez do'konlar: faqat do'kon tanlanmagan holatda katta tugma bo'lib chiqadi.
        val favs = vm.favorites.value
        val showFavs = noStore && s.hasProducts
        binding.btnFav0.show(showFavs && favs.isNotEmpty())
        binding.btnFav1.show(showFavs && favs.size > 1)
        binding.btnOtherStore.show(showFavs)
        favs.getOrNull(0)?.let { binding.btnFav0.text = it.name }
        favs.getOrNull(1)?.let { binding.btnFav1.text = it.name }
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

        b.toggleType.check(if (d.priceType == PriceType.CASH) R.id.btnCash else R.id.btnWholesale)
        b.toggleCurrency.check(if (d.currency == Currency.USD) R.id.btnUsd else R.id.btnSom)
        b.toggleMode.check(if (d.mode == DealMode.RETURN) R.id.btnReturn else R.id.btnSale)
        b.btnClear.show(d.lines.isNotEmpty())
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
