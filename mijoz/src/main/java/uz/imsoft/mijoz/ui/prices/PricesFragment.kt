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
import uz.imsoft.mijoz.data.OrderCart
import uz.imsoft.mijoz.ui.ledger.RepeatSheet
import uz.imsoft.mijoz.ui.ledgerhost.MijozLedgerHost
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Narxlar: faqat shu mijoz olgan tovarlar; 1..4-narx, So'm/$; kelgan narx YO'Q. v2.6: "+" bilan buyurtma savatchasi. */
class PricesFragment : Fragment() {
    private var _b: FragmentPricesBinding? = null
    private val b get() = _b!!
    private lateinit var vm: PricesViewModel
    private val adapter = Adapter({ row -> HistorySheet.show(childFragmentManager, row.item.name) }, { row -> askQty(row) })
    private var rendering = false
    private var groupsShown: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentPricesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        uz.imsoft.mijoz.ui.tour.Tour.show(this, uz.imsoft.mijoz.ui.tour.Tour.PRICES)
        vm = ViewModelProvider(this)[PricesViewModel::class.java]
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.recycler.addItemDecoration(
            com.google.android.material.divider.MaterialDividerItemDecoration(requireContext(), androidx.recyclerview.widget.RecyclerView.VERTICAL).apply {
                dividerInsetStart = (16 * resources.displayMetrics.density).toInt()
                isLastItemDecorated = false
            },
        )
        b.edtSearch.onTextChanged { vm.query.value = it }
        val p = Locator.prefs
        rendering = true
        b.toggleType.check(when (p.priceLevel) { 1 -> R.id.btnP1; 2 -> R.id.btnP2; 3 -> R.id.btnCash; else -> R.id.btnWholesale })
        b.toggleCurrency.check(if (p.currency == "usd") R.id.btnUsd else R.id.btnSom)
        rendering = false
        b.toggleType.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !rendering) {
                p.priceLevel = when (id) { R.id.btnP1 -> 1; R.id.btnP2 -> 2; R.id.btnCash -> 3; else -> 4 }
                adapter.notifyDataSetChanged()
            }
        }
        b.toggleCurrency.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !rendering) { p.currency = if (id == R.id.btnUsd) "usd" else "som"; adapter.notifyDataSetChanged() }
        }
        launchOnStarted { vm.state.collect { render(it) } }
        launchOnStarted { Locator.cart.items.collect { renderCart(it); adapter.notifyDataSetChanged() } }
        launchOnStarted { Locator.data.state.collect { adapter.ordersOn = MijozLedgerHost.ordersEnabled(); renderCart(Locator.cart.items.value) } }
        b.btnCartSend.setOnClickListener { RepeatSheet.showPlan(childFragmentManager, MijozLedgerHost.planForCart(requireContext())) }
        b.btnCartClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.cart_clear).setMessage(R.string.cart_clear_msg)
                .setPositiveButton(R.string.ok) { _, _ -> Locator.cart.clear() }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun renderCart(items: List<OrderCart.Item>) {
        val bb = _b ?: return
        val on = MijozLedgerHost.ordersEnabled()
        bb.cartBar.show(on && items.isNotEmpty())
        if (items.isEmpty()) return
        val (som, usd) = Locator.cart.approxTotal()
        val sum = listOfNotNull(som.takeIf { it > 0 }?.let { requireContext().money(it, false) }, usd.takeIf { it > 0 }?.let { requireContext().money(it, true) }).joinToString(" + ")
        bb.tvCart.text = getString(R.string.cart_bar, items.size) + if (sum.isNotEmpty()) "\n≈ $sum" else ""
    }

    private fun priceOf(r: PriceRow): Pair<Double, Boolean> {
        val p = Locator.prefs
        val usd = p.currency == "usd"
        return r.item.price(p.priceLevel, usd) to usd
    }

    /** "+": miqdor so'raladi (oldingi olingan miqdor taklif qilinadi). 0 — savatchadan olib tashlaydi. */
    private fun askQty(r: PriceRow) {
        val ctx = requireContext()
        val cur = Locator.cart.qty(r.key)
        val suggest = if (cur > 0) cur else r.last?.qty?.takeIf { it > 0 } ?: 1.0
        val til = TextInputLayout(ctx).apply { setPadding(48, 16, 48, 0); hint = getString(R.string.cart_qty) }
        val edt = TextInputEditText(til.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(Fmt.qty(suggest).replace(" ", "").replace('\u00A0'.toString(), ""))
            uz.imsoft.shared.android.NumInput.attach(this)
            selectAll()
        }
        til.addView(edt)
        if (r.last != null) til.helperText = getString(R.string.cart_last, Stamp.formatDate(r.last.stamp), Fmt.qty(r.last.qty))
        // v5.2 (A7): INFO — mahsulot ma'lumoti. Tepada ochiladi; bosilsa klaviatura yopiladi.
        val box = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val bio = uz.imsoft.mijoz.ui.MijozBio.of(Locator.data.state.value.snapshot?.bio, r.item.id)
        if (bio != null) {
            val auto = Locator.prefs.bioAuto
            val tvBio = uz.imsoft.shared.android.Ui.text(ctx, bio.text).apply {
                setPadding(uz.imsoft.shared.android.Ui.dp(ctx, 12), uz.imsoft.shared.android.Ui.dp(ctx, 8), uz.imsoft.shared.android.Ui.dp(ctx, 12), uz.imsoft.shared.android.Ui.dp(ctx, 8))
                setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant))
                visibility = if (auto) View.VISIBLE else View.GONE
            }
            var open = auto
            val btn = uz.imsoft.shared.android.Ui.button(ctx, getString(R.string.bio_info), outlined = true, top = 0, icon = R.drawable.ic_info) { v ->
                open = !open
                tvBio.visibility = if (open) View.VISIBLE else View.GONE
                if (open) {
                    ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
                    edt.clearFocus()
                }
            }
            box.setPadding(48, 16, 48, 0)
            box.addView(btn)
            box.addView(tvBio)
        }
        box.addView(til)
        val d = MaterialAlertDialogBuilder(ctx).setTitle(r.item.name).setView(box)
            .setPositiveButton(R.string.cart_put) { _, _ ->
                val q = uz.imsoft.shared.NumText.parse(edt.text?.toString())
                val (price, usd) = priceOf(r)
                Locator.cart.set(OrderCart.Item(r.key, r.item.name, r.item.id, q, price, usd))
            }
            .setNegativeButton(R.string.cancel, null)
        if (cur > 0) d.setNeutralButton(R.string.cart_remove) { _, _ -> Locator.cart.remove(r.key) }
        val dlg = d.show()
        edt.requestFocus()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
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

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) { uz.imsoft.mijoz.ui.tour.Tour.show(this, uz.imsoft.mijoz.ui.tour.Tour.PRICES) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private class Adapter(private val onClick: (PriceRow) -> Unit, private val onAdd: (PriceRow) -> Unit) : RecyclerView.Adapter<Adapter.VH>() {
        private var rows: List<PriceRow> = emptyList()
        var ordersOn = false
            set(v) { if (field != v) { field = v; notifyDataSetChanged() } }

        fun submit(list: List<PriceRow>) { rows = list; notifyDataSetChanged() }

        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemPriceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position])

        inner class VH(private val b: ItemPriceBinding) : RecyclerView.ViewHolder(b.root) {
            init {
                b.root.setOnClickListener { rows.getOrNull(bindingAdapterPosition)?.let(onClick) }
                b.btnAdd.setOnClickListener { rows.getOrNull(bindingAdapterPosition)?.let(onAdd) }
                b.btnAdd.setOnLongClickListener { rows.getOrNull(bindingAdapterPosition)?.let { Locator.cart.remove(it.key) }; true }
            }

            fun bind(r: PriceRow) {
                val ctx = b.root.context
                b.tvName.text = bold(r.item.name, r.ranges)
                val p = Locator.prefs
                val usd = p.currency == "usd"
                val price = r.item.price(p.priceLevel, usd)
                b.tvPrice.text = if (r.item.id.isEmpty() || price == 0.0) ctx.getString(R.string.price_none) else ctx.money(price, usd)
                val last = r.last
                // v2.7: tovar kodi va tarozi PLU raqami (1-ilovadan)
                val code = when {
                    r.item.id.isEmpty() -> ""
                    r.item.plu.isNotEmpty() -> ctx.getString(R.string.price_code_plu, r.item.id, r.item.plu)
                    else -> ctx.getString(R.string.price_code, r.item.id)
                }
                val lastText = if (last == null) "" else {
                    val lp = if (last.price.som != 0.0) ctx.money(last.price.som, false) else if (last.price.usd != 0.0) ctx.money(last.price.usd, true) else "—"
                    ctx.getString(R.string.price_last, Stamp.formatDate(last.stamp), Fmt.qty(last.qty), lp)
                }
                val meta = listOf(code, lastText).filter { it.isNotEmpty() }.joinToString("\n")
                b.tvMeta.text = meta
                b.tvMeta.show(meta.isNotEmpty())
                b.btnAdd.show(ordersOn)
                val inCart = Locator.cart.qty(r.key)
                b.btnAdd.text = if (inCart > 0) Fmt.qty(inCart) else ""
                b.btnAdd.setIconResource(if (inCart > 0) R.drawable.ic_check else R.drawable.ic_add)
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
