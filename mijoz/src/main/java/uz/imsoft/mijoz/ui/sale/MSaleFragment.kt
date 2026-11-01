package uz.imsoft.mijoz.ui.sale

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.FragmentMsaleBinding
import uz.imsoft.mijoz.databinding.ItemMproductBinding
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.mijoz.ui.money
import uz.imsoft.mijoz.ui.onTextChanged
import uz.imsoft.mijoz.ui.show
import uz.imsoft.shared.SearchIndex
import uz.imsoft.shared.SearchOptions
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.report.PriceItem

/**
 * v3.9 Mijoz: o'z xaridorlariga savdo — agentdagi savdo ekrani bilan bir xil: mijoz tanlash, 4 narx (1, 2, Naqd, Optom),
 * qidiruv, "−  miqdor  +", pastda savatcha. Internet kerak emas: tovarlar va narxlar oxirgi yangilangan ma'lumotdan,
 * savdo shu telefondagi Kassa bazasiga yoziladi.
 */
class MSaleFragment : Fragment() {
    private var _b: FragmentMsaleBinding? = null
    private val b get() = _b!!
    private val adapter = Adapter()
    private var all: List<PriceItem> = emptyList()
    private var index: SearchIndex<PriceItem>? = null
    private var query = ""
    private var searchJob: Job? = null
    private var rendering = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentMsaleBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.recycler.itemAnimator = null
        val p = Locator.prefs
        rendering = true
        b.toggleType.check(when (p.priceLevel) { 1 -> R.id.btnP1; 2 -> R.id.btnP2; 3 -> R.id.btnCash; else -> R.id.btnWholesale })
        rendering = false
        b.toggleType.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !rendering) {
                p.priceLevel = when (id) { R.id.btnP1 -> 1; R.id.btnP2 -> 2; R.id.btnCash -> 3; else -> 4 }
                adapter.notifyDataSetChanged()
            }
        }
        b.edtSearch.onTextChanged { q ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch { delay(120); query = q; filter() }
        }
        b.rowClient.setOnClickListener { pickCustomer() }
        b.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ms_clear).setMessage(R.string.ms_clear_text)
                .setPositiveButton(R.string.ok) { _, _ -> Locator.sale.clear() }.setNegativeButton(R.string.cancel, null).show()
        }
        b.btnCart.setOnClickListener { (activity as? MainActivity)?.push(MCartFragment()) }
        launchOnStarted { Locator.data.state.collect { s -> setProducts(s.snapshot?.products.orEmpty()) } }
        launchOnStarted { Locator.sale.state.collect { renderCart(); adapter.notifyDataSetChanged() } }
        launchOnStarted { Locator.pos.state.collect { renderCart() } }
        viewLifecycleOwner.lifecycleScope.launch { runCatching { Locator.pos.load() } }
    }

    private fun setProducts(list: List<PriceItem>) {
        if (list === all) return
        all = list.sortedBy { it.name.lowercase() }
        index = SearchIndex(all, { it.name }, { it.id }, { it.group + " " + it.plu })
        filter()
    }

    private fun filter() {
        val idx = index
        val shown = if (query.isBlank() || idx == null) all
        else idx.search(query, SearchOptions(fuzzy = Locator.prefs.fuzzy, layoutFix = true), limit = 300).map { it.item }
        adapter.submit(shown)
        val bb = _b ?: return
        bb.tvEmpty.show(shown.isEmpty())
        bb.tvEmpty.setText(if (all.isEmpty()) R.string.ms_no_products else R.string.ms_nothing_found)
    }

    private fun price(p: PriceItem): Double = p.price(Locator.prefs.priceLevel, false)

    private fun renderCart() {
        val bb = _b ?: return
        val ctx = requireContext()
        val s = Locator.sale.state.value
        val d = Locator.pos.state.value
        val c = d.customer(s.customerId)
        bb.tvClient.text = c?.name ?: getString(R.string.pos_cash_client)
        bb.tvClientInfo.text = if (c != null) getString(R.string.pos_balance, ctx.money(d.balance(c.id), false)) else getString(R.string.pos_cash_hint)
        bb.tvCount.text = getString(R.string.ms_count, s.lines.size, Fmt.qty(s.qty))
        bb.tvTotal.text = ctx.money(s.total, false)
        bb.btnCart.isEnabled = s.lines.isNotEmpty()
        bb.btnClear.show(s.lines.isNotEmpty())
    }

    private fun pickCustomer() {
        val d = Locator.pos.state.value
        val list = d.customers
        val names = (listOf(getString(R.string.pos_cash_client), getString(R.string.pos_new_client)) +
            list.map { it.name + "  ·  " + requireContext().money(d.balance(it.id), false) }).toTypedArray()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.pos_client).setItems(names) { _, i ->
            when (i) {
                0 -> Locator.sale.setCustomer(0)
                1 -> newCustomer()
                else -> Locator.sale.setCustomer(list[i - 2].id)
            }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun newCustomer() {
        val ctx = requireContext()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
        val (tn, en) = Ui.field(ctx, getString(R.string.pos_client_name))
        val (tp, ep) = Ui.field(ctx, getString(R.string.pos_client_phone), Ui.PHONE)
        col.addView(tn); col.addView(tp)
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.pos_new_client).setView(col)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = en.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                val phone = ep.text?.toString().orEmpty()
                viewLifecycleOwner.lifecycleScope.launch { Locator.sale.setCustomer(Locator.pos.addCustomer(name, phone)) }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Miqdor va narx (narx — tanlangan narx turidan, o'zgartirish mumkin). */
    private fun askQty(p: PriceItem) {
        val ctx = requireContext()
        val cur = Locator.sale.state.value.lines.firstOrNull { it.code.ifEmpty { it.name } == p.id.ifEmpty { p.name } }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
        val (tq, eq) = Ui.field(ctx, getString(R.string.ed_qty), Ui.NUMBER, Fmt.qty(cur?.qty ?: 1.0).replace(" ", ""))
        val pr0 = cur?.price ?: price(p)
        val (tp, ep) = Ui.field(ctx, getString(R.string.ed_price), Ui.NUMBER, if (pr0 > 0) Fmt.qty(pr0).replace(" ", "") else "")
        eq.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        col.addView(tq); col.addView(tp)
        MaterialAlertDialogBuilder(ctx).setTitle(p.name).setView(col)
            .setPositiveButton(R.string.ok) { _, _ ->
                val q = num(eq.text?.toString())
                val pr = num(ep.text?.toString())
                if (q < 0 || pr < 0) return@setPositiveButton
                Locator.sale.set(p.name, p.id, q, pr)
            }.setNegativeButton(R.string.cancel, null).show()
        eq.requestFocus()
        eq.selectAll()
    }

    private fun num(s: String?): Double = s.orEmpty().filter { !it.isWhitespace() }.replace(',', '.').toDoubleOrNull() ?: 0.0

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<PriceItem> = emptyList()

        fun submit(list: List<PriceItem>) { items = list; notifyDataSetChanged() }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemMproductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(h: VH, position: Int) = h.bind(items[position])

        inner class VH(val v: ItemMproductBinding) : RecyclerView.ViewHolder(v.root) {
            fun bind(p: PriceItem) {
                val ctx = v.root.context
                val pr = price(p)
                val q = Locator.sale.qtyOf(p.name, p.id)
                v.tvName.text = p.name
                v.tvPrice.text = if (pr > 0) ctx.money(pr, false) else ctx.getString(R.string.ms_no_price)
                v.tvMeta.text = listOf(p.group, p.plu.takeIf { it.isNotEmpty() }?.let { "PLU $it" }.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
                v.tvQty.text = if (q > 0) Fmt.qty(q) else ""
                v.btnMinus.visibility = if (q > 0) View.VISIBLE else View.INVISIBLE
                v.root.setBackgroundColor(
                    if (q > 0) com.google.android.material.color.MaterialColors.getColor(v.root, com.google.android.material.R.attr.colorSecondaryContainer)
                    else android.graphics.Color.TRANSPARENT,
                )
                v.btnPlus.setOnClickListener { if (pr > 0) Locator.sale.add(p.name, p.id, 1.0, pr) else askQty(p) }
                v.btnMinus.setOnClickListener { Locator.sale.add(p.name, p.id, -1.0, pr) }
                v.tvQty.setOnClickListener { askQty(p) }
                v.tvPrice.setOnClickListener { askQty(p) }
                v.root.setOnClickListener { askQty(p) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
