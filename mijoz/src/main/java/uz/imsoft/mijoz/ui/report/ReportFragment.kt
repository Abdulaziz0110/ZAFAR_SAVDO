package uz.imsoft.mijoz.ui.report

import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.util.Pair as UPair
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.FragmentReportBinding
import uz.imsoft.mijoz.databinding.ItemDayBinding
import uz.imsoft.mijoz.databinding.ItemRowBinding
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.mijoz.ui.money2
import uz.imsoft.mijoz.ui.show
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.Stamp
import java.util.Calendar
import java.util.TimeZone

/** Hisobot: Xaridlar / To'lovlar / Hammasi; davr tanlash; kunlar bo'yicha; PDF/chop etish. */
class ReportFragment : Fragment() {
    private var _b: FragmentReportBinding? = null
    private val b get() = _b!!
    private lateinit var vm: ReportViewModel
    private val adapter = Adapter()
    private var last = ReportState()
    /** Chop etish tugaguncha WebView yig'ilib ketmasligi uchun. */
    private var printView: WebView? = null

    private val periods = listOf(
        Period.TODAY to R.string.p_today, Period.WEEK to R.string.p_week, Period.MONTH to R.string.p_month,
        Period.LAST_MONTH to R.string.p_last_month, Period.DAYS90 to R.string.p_90, Period.ALL to R.string.p_all, Period.CUSTOM to R.string.p_custom,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentReportBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(this)[ReportViewModel::class.java]
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        for (t in listOf(R.string.tab_purchases, R.string.tab_payments, R.string.tab_all)) b.tabs.addTab(b.tabs.newTab().setText(t))
        b.tabs.getTabAt(vm.tab.value)?.select()
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { vm.tab.value = tab.position }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        for ((p, label) in periods) {
            val chip = layoutInflater.inflate(R.layout.item_group_chip, b.chipPeriods, false) as Chip
            chip.setText(label)
            chip.tag = p
            chip.setOnClickListener { if (p == Period.CUSTOM) pickRange() else vm.period.value = p }
            b.chipPeriods.addView(chip)
        }
        b.toolbar.setOnMenuItemClickListener { if (it.itemId == R.id.action_pdf) { printPdf(); true } else false }
        launchOnStarted { vm.state.collect { last = it; render(it) } }
    }

    private fun render(s: ReportState) {
        val bb = _b ?: return
        val ctx = requireContext()
        for (i in 0 until bb.chipPeriods.childCount) {
            val c = bb.chipPeriods.getChildAt(i) as Chip
            c.isChecked = c.tag == s.period
        }
        adapter.submit(s.items)
        val t = s.totals
        bb.tvSummary.text = if (t == null) "" else buildString {
            append(periodText(s)).append('\n')
            append(getString(R.string.sum_bought, ctx.money2(t.purchased), t.purchaseCount)).append('\n')
            if (!t.returned.isZero) append(getString(R.string.sum_returned, ctx.money2(t.returned))).append('\n')
            if (!t.discount.isZero) append(getString(R.string.sum_discount, ctx.money2(t.discount))).append('\n')
            append(getString(R.string.sum_paid, ctx.money2(t.paid), t.paymentCount))
        }
        bb.tvSummary.show(s.hasData)
        bb.tvEmpty.show(s.items.isEmpty())
        bb.tvEmpty.setText(if (!s.hasData) R.string.prices_no_data else R.string.report_empty)
    }

    private fun periodText(s: ReportState): String =
        if (s.period == Period.ALL) getString(R.string.p_all) else Stamp.formatDate(s.from) + " – " + Stamp.formatDate(minOf(s.to, Stamp.dayEnd(s.to)))

    private fun pickRange() {
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText(R.string.p_custom).build()
        picker.addOnPositiveButtonClickListener { sel: UPair<Long, Long> ->
            val a = sel.first ?: return@addOnPositiveButtonClickListener
            val z = sel.second ?: a
            vm.custom.value = Stamp.dayStart(utcStamp(a)) to Stamp.dayEnd(utcStamp(z))
            vm.period.value = Period.CUSTOM
        }
        picker.show(childFragmentManager, "range")
    }

    private fun utcStamp(ms: Long): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
        return Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    // ---------------- PDF / chop etish ----------------

    private fun printPdf() {
        val ctx = requireContext()
        val snap = Locator.data.state.value.snapshot ?: return
        val s = last
        val html = ReportPdf.html(ctx, snap.clientName, periodText(s), s, snap.debt?.let { Money2(it.som, it.usd) }, snap.debt?.at ?: 0L, snap.reportBalance)
        val wv = WebView(ctx)
        printView = wv
        wv.settings.javaScriptEnabled = false
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val name = "Hisobot_" + snap.clientName.filter { it.isLetterOrDigit() }.take(20)
                pm.print(name, view.createPrintDocumentAdapter(name), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    // ---------------- ro'yxat ----------------

    private class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<ReportItem> = emptyList()
        fun submit(list: List<ReportItem>) { items = list; notifyDataSetChanged() }

        override fun getItemCount() = items.size
        override fun getItemViewType(position: Int) = if (items[position] is ReportItem.DayHeader) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == 0) object : RecyclerView.ViewHolder(ItemDayBinding.inflate(inf, parent, false).root) {}
            else object : RecyclerView.ViewHolder(ItemRowBinding.inflate(inf, parent, false).root) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val ctx = holder.itemView.context
            when (val it = items[position]) {
                is ReportItem.DayHeader -> {
                    val b = ItemDayBinding.bind(holder.itemView)
                    b.tvDay.text = Stamp.formatDate(it.day.day)
                    val t = it.day.totals
                    val parts = ArrayList<String>()
                    if (!t.purchased.isZero) parts += ctx.getString(R.string.day_bought, ctx.money2(t.purchased))
                    if (!t.paid.isZero) parts += ctx.getString(R.string.day_paid, ctx.money2(t.paid))
                    b.tvDayTotal.text = parts.joinToString(" · ")
                }
                is ReportItem.Row -> bindRow(ItemRowBinding.bind(holder.itemView), it.e, ctx)
            }
        }

        private fun bindRow(b: ItemRowBinding, e: LedgerEntry, ctx: Context) {
            val time = "%02d:%02d".format(Stamp.hour(e.stamp), Stamp.minute(e.stamp))
            when (e.kind) {
                EntryKind.PAYMENT, EntryKind.PAYMENT_RETURN -> {
                    b.tvTitle.text = e.kindText.ifEmpty { ctx.getString(R.string.kind_payment) }
                    b.tvSub.text = listOf(time, e.product).filter { it.isNotBlank() }.joinToString(" · ")
                    b.tvAmount.text = ctx.money2(ClientStats.paymentOf(e))
                }
                else -> {
                    b.tvTitle.text = e.product.ifEmpty { e.kindText }
                    val price = e.v(Col.PRICE)
                    val p = if (price.som != 0.0) Fmt.som(price.som) else if (price.usd != 0.0) Fmt.usd(price.usd) + " $" else ""
                    val qp = if (e.qty != 0.0 && p.isNotEmpty()) ctx.getString(R.string.qty_x_price, Fmt.qty(e.qty), p) else if (e.qty != 0.0) Fmt.qty(e.qty) else ""
                    b.tvSub.text = listOf(time, e.kindText, qp).filter { it.isNotBlank() }.joinToString(" · ")
                    b.tvAmount.text = when (e.kind) {
                        EntryKind.RETURN -> "−" + ctx.money2(ClientStats.returnOf(e))
                        EntryKind.DISCOUNT -> "−" + ctx.money2(e.v(Col.DISCOUNT))
                        else -> ctx.money2(ClientStats.purchaseOf(e).let { if (it.isZero) e.v(Col.SUM) else it })
                    }
                }
            }
        }
    }
}
