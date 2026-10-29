package uz.imsoft.mijoz.ui.prices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.ItemRowBinding
import uz.imsoft.mijoz.databinding.SheetHistoryBinding
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.money2
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.Stamp

/** Tovar bo'yicha: qachon, qancha, necha puldan olingan. */
class HistorySheet : BottomSheetDialogFragment() {
    private var _b: SheetHistoryBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetHistoryBinding.inflate(inflater, container, false)
        return _b!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = _b!!
        val ctx = requireContext()
        val name = requireArguments().getString(ARG_NAME).orEmpty()
        val entries = Locator.data.state.value.snapshot?.ledger.orEmpty()
        val list = ClientStats.productHistory(entries, LedgerMerge.nameKey(name))
        b.tvTitle.text = name
        val bought = list.filter { !it.returned }
        val totalQty = bought.sumOf { it.qty }
        val totalSum = bought.fold(Money2.ZERO) { a, x -> a + x.sum }
        val prices = bought.mapNotNull { p -> p.price.som.takeIf { it > 0 } }
        val sb = StringBuilder(getString(R.string.hist_summary, bought.size, Fmt.qty(totalQty), ctx.money2(totalSum)))
        if (prices.isNotEmpty()) sb.append('\n').append(getString(R.string.hist_prices, Fmt.som(prices.min()), Fmt.som(prices.max())))
        b.tvSummary.text = sb
        b.recycler.layoutManager = LinearLayoutManager(ctx)
        b.recycler.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = list.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object : RecyclerView.ViewHolder(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false).root) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val p = list[position]
                val rb = ItemRowBinding.bind(holder.itemView)
                rb.tvTitle.text = Stamp.formatDateTime(p.stamp) + if (p.returned) " · " + ctx.getString(R.string.kind_return) else ""
                val price = if (p.price.som != 0.0) Fmt.som(p.price.som) else if (p.price.usd != 0.0) Fmt.usd(p.price.usd) + " $" else "—"
                rb.tvSub.text = ctx.getString(R.string.qty_x_price, Fmt.qty(p.qty), price)
                rb.tvAmount.text = (if (p.returned) "−" else "") + ctx.money2(p.sum)
            }
        }
        if (list.isEmpty()) b.tvSummary.setText(R.string.hist_empty)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_NAME = "name"
        fun show(fm: FragmentManager, name: String) {
            HistorySheet().apply { arguments = Bundle().apply { putString(ARG_NAME, name) } }.show(fm, "hist")
        }
    }
}
