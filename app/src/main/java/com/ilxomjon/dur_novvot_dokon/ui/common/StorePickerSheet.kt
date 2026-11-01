package com.ilxomjon.dur_novvot_dokon.ui.common

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.ItemStoreBinding
import com.ilxomjon.dur_novvot_dokon.databinding.SheetStorePickerBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Store
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.show

/** Do'kon tanlash (qidiruv bilan). Natija Fragment Result API orqali [requestKey] bo'yicha qaytadi. */
class StorePickerSheet : BottomSheetDialogFragment() {
    private var _binding: SheetStorePickerBinding? = null
    private val binding get() = _binding!!
    private var all: List<Store> = emptyList()
    private var recentIds: List<String> = emptyList()
    private var lastQuery = ""
    private lateinit var adapter: StoreAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        d.behavior.skipCollapsed = true
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetStorePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        all = ServiceLocator.catalog.state.value.stores
        adapter = StoreAdapter { store ->
            val result = Bundle().apply { putString(KEY_ID, store.id) }
            parentFragmentManager.setFragmentResult(requireArguments().getString(ARG_KEY).orEmpty(), result)
            dismiss()
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.edtSearch.onTextChanged { lastQuery = it; applyFilter(it) }
        binding.chipDebtors.setOnCheckedChangeListener { _, _ -> applyFilter(lastQuery) }
        applyFilter("")
        viewLifecycleOwner.lifecycleScope.launch {
            recentIds = ServiceLocator.history.recentStoreIds()
            applyFilter(lastQuery)
        }
    }

    private val index by lazy { uz.imsoft.shared.SearchIndex(all, { it.name }, { it.id }, { it.regionName }) }

    private fun applyFilter(q: String) {
        val blank = q.isBlank()
        val prefs = ServiceLocator.appPrefs
        val hits = if (blank) null else index.search(q, uz.imsoft.shared.SearchOptions(prefs.fFuzzySearch, prefs.fLayoutFix))
        adapter.highlights = hits?.associate { it.item.id to it.ranges }.orEmpty()
        var list = hits?.map { it.item } ?: all
        if (binding.chipDebtors.isChecked) list = list.filter { it.debtSom > 0.0 || it.debtUsd > 0.0 }
        if (blank) {
            // Qidiruv yo'q bo'lsa: tanlangan (sevimli) do'konlar, keyin so'nggi ishlanganlar yuqorida.
            val rank = HashMap<String, Int>()
            recentIds.forEachIndexed { i, id -> rank[id] = 100 + i }
            prefs.favoriteStoreIds.forEachIndexed { i, id -> rank[id] = i }
            list = list.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
        }
        // Ajratib ko'rsatish o'zgargan bo'lishi mumkin (ro'yxat bir xil bo'lsa ham) — ko'rinadigan qatorlar qayta chiziladi.
        adapter.submitList(list) { adapter.notifyItemRangeChanged(0, adapter.itemCount) }
        binding.tvEmpty.show(list.isEmpty())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class StoreAdapter(private val onPick: (Store) -> Unit) : ListAdapter<Store, StoreAdapter.VH>(Diff) {
        var highlights: Map<String, List<IntRange>> = emptyMap()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemStoreBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(s: Store) {
                val ctx = b.root.context
                b.tvName.text = Highlight.apply(s.name, highlights[s.id], ctx)
                b.tvRegion.text = s.regionName.ifEmpty { ctx.getString(R.string.store_no_region) }
                b.tvDebt.text = debtText(ctx, s)
                b.tvDebt.show(b.tvDebt.text.isNotEmpty())
                b.root.setOnClickListener { onPick(s) }
            }
        }

        private object Diff : DiffUtil.ItemCallback<Store>() {
            override fun areItemsTheSame(a: Store, b: Store) = a.id == b.id
            override fun areContentsTheSame(a: Store, b: Store) = a == b
        }
    }

    companion object {
        const val KEY_ID = "store_id"
        private const val ARG_KEY = "request_key"

        fun show(fm: FragmentManager, requestKey: String) {
            StorePickerSheet().apply { arguments = Bundle().apply { putString(ARG_KEY, requestKey) } }
                .show(fm, "store_picker")
        }

        /** Faqat summalar: "1 250 000 so'm · 12.50 $" (qarz yo'q bo'lsa bo'sh). */
        fun debtAmounts(ctx: android.content.Context, s: Store): String {
            val parts = ArrayList<String>()
            if (s.debtSom > 0.0) parts += ctx.money(s.debtSom, Currency.SOM)
            if (s.debtUsd > 0.0) parts += ctx.money(s.debtUsd, Currency.USD)
            return parts.joinToString(" · ")
        }

        fun debtText(ctx: android.content.Context, s: Store): String {
            val amounts = debtAmounts(ctx, s)
            return if (amounts.isEmpty()) "" else ctx.getString(R.string.store_debt, amounts)
        }
    }
}
