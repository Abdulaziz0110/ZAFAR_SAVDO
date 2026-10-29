package uz.imsoft.mijoz.ui.prices

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.FragmentPricesBinding
import uz.imsoft.mijoz.databinding.ItemPriceBinding
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.mijoz.ui.money
import uz.imsoft.mijoz.ui.onTextChanged
import uz.imsoft.mijoz.ui.show
import uz.imsoft.shared.report.Stamp

/** Narxlar: faqat shu mijoz olgan tovarlar; Naqd/Optom, So'm/$; kelgan narx YO'Q. */
class PricesFragment : Fragment() {
    private var _b: FragmentPricesBinding? = null
    private val b get() = _b!!
    private lateinit var vm: PricesViewModel
    private val adapter = Adapter { row -> HistorySheet.show(childFragmentManager, row.item.name) }
    private var rendering = false
    private var groupsShown: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentPricesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(this)[PricesViewModel::class.java]
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.edtSearch.onTextChanged { vm.query.value = it }
        val p = Locator.prefs
        rendering = true
        b.toggleType.check(if (p.priceType == "cash") R.id.btnCash else R.id.btnWholesale)
        b.toggleCurrency.check(if (p.currency == "usd") R.id.btnUsd else R.id.btnSom)
        rendering = false
        b.toggleType.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !rendering) { p.priceType = if (id == R.id.btnCash) "cash" else "wholesale"; adapter.notifyDataSetChanged() }
        }
        b.toggleCurrency.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !rendering) { p.currency = if (id == R.id.btnUsd) "usd" else "som"; adapter.notifyDataSetChanged() }
        }
        launchOnStarted { vm.state.collect { render(it) } }
    }

    private fun render(s: PricesState) {
        val bb = _b ?: return
        adapter.submit(s.rows)
        renderGroups(s.groups, s.group)
        bb.tvInfo.text = buildString {
            if (s.pricesAt > 0) append(getString(R.string.prices_at, Fmt.dateTime(s.pricesAt)))
            if (s.approximate) { if (isNotEmpty()) append(" · "); append(getString(R.string.search_approx)) }
        }
        bb.tvInfo.show(bb.tvInfo.text.isNotEmpty())
        bb.tvEmpty.show(s.rows.isEmpty())
        bb.tvEmpty.setText(if (!s.hasData) R.string.prices_no_data else R.string.prices_empty)
    }

    private fun renderGroups(groups: List<String>, selected: String) {
        val bb = _b ?: return
        if (groups != groupsShown) {
            groupsShown = groups
            bb.chipGroups.removeAllViews()
            for (g in listOf("") + groups) {
                val chip = layoutInflater.inflate(R.layout.item_group_chip, bb.chipGroups, false) as Chip
                chip.text = g.ifEmpty { getString(R.string.group_all) }
                chip.tag = g
                chip.setOnClickListener { vm.group.value = g }
                bb.chipGroups.addView(chip)
            }
        }
        for (i in 0 until bb.chipGroups.childCount) {
            val c = bb.chipGroups.getChildAt(i) as Chip
            c.isChecked = c.tag == selected
        }
        bb.chipGroups.show(groups.isNotEmpty())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private class Adapter(private val onClick: (PriceRow) -> Unit) : RecyclerView.Adapter<Adapter.VH>() {
        private var rows: List<PriceRow> = emptyList()

        fun submit(list: List<PriceRow>) { rows = list; notifyDataSetChanged() }

        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemPriceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position])

        inner class VH(private val b: ItemPriceBinding) : RecyclerView.ViewHolder(b.root) {
            init { b.root.setOnClickListener { rows.getOrNull(bindingAdapterPosition)?.let(onClick) } }

            fun bind(r: PriceRow) {
                val ctx = b.root.context
                b.tvName.text = bold(r.item.name, r.ranges)
                val p = Locator.prefs
                val usd = p.currency == "usd"
                val cash = p.priceType == "cash"
                val price = when {
                    cash && usd -> r.item.cashUsd
                    cash -> r.item.cashSom
                    usd -> r.item.wholesaleUsd
                    else -> r.item.wholesaleSom
                }
                b.tvPrice.text = if (r.item.id.isEmpty() || price == 0.0) ctx.getString(R.string.price_none) else ctx.money(price, usd)
                val last = r.last
                b.tvMeta.text = if (last == null) "" else {
                    val lp = if (last.price.som != 0.0) ctx.money(last.price.som, false) else if (last.price.usd != 0.0) ctx.money(last.price.usd, true) else "—"
                    ctx.getString(R.string.price_last, Stamp.formatDate(last.stamp), Fmt.qty(last.qty), lp)
                }
                b.tvMeta.show(last != null)
            }

            private fun bold(text: String, ranges: List<IntRange>): CharSequence {
                if (ranges.isEmpty()) return text
                val s = SpannableString(text)
                for (r in ranges) {
                    val a = r.first.coerceIn(0, text.length); val e = (r.last + 1).coerceIn(a, text.length)
                    if (a < e) s.setSpan(StyleSpan(Typeface.BOLD), a, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                return s
            }
        }
    }
}
