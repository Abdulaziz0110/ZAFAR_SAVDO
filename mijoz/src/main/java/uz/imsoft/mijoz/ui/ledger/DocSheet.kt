package uz.imsoft.mijoz.ui.ledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.ItemLedgerLineBinding
import uz.imsoft.mijoz.databinding.SheetLedgerDocBinding
import uz.imsoft.mijoz.ui.show
import kotlinx.coroutines.launch
import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.receipt.ReceiptBuilder
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.Stamp

/** Bitta hujjat: qatorlar, jami, qoldiq; Takrorlash · Chek · Ulashish. */
class DocSheet : BottomSheetDialogFragment() {
    private var _b: SheetLedgerDocBinding? = null
    private val b get() = _b!!

    private fun ledger(): LedgerFragment? = parentFragment as? LedgerFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetLedgerDocBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val key = requireArguments().getString(ARG_KEY).orEmpty()
        val doc = ledger()?.viewModel()?.docByKey(key)
        if (doc == null) { dismissAllowingStateLoss(); return }
        val host = ledgerHost()
        val ctx = requireContext()
        val client = ledger()?.currentState()?.clientName.orEmpty()
        b.tvTitle.text = LedgerUi.kindTitle(ctx, doc.kind) + " · " + LedgerUi.time(doc.stamp)
        b.tvSub.text = listOf(Stamp.formatDate(doc.stamp) + ", " + LedgerUi.weekday(ctx, doc.stamp), client).filter { it.isNotBlank() }.joinToString(" · ")
        // Mos kelgan qatorlar (qidiruvdan ochilsa) qalin
        val hit = ledger()?.currentState()?.rows?.asSequence()?.filterIsInstance<LedgerRow.Doc>()?.firstOrNull { it.hit.doc.key == key }?.hit
        b.lines.removeAllViews()
        val inf = LayoutInflater.from(ctx)
        val show = if (doc.isGoods) doc.goods else doc.lines
        for (e in show) {
            val i = doc.lines.indexOf(e)
            val lb = ItemLedgerLineBinding.inflate(inf, b.lines, false)
            lb.tvName.text = LedgerUi.highlight(ctx, e.product.ifBlank { e.kindText }, hit?.ranges?.get(i))
            lb.tvDetail.text = LedgerUi.lineDetail(e)
            lb.tvDetail.show(lb.tvDetail.text.isNotEmpty())
            lb.tvAmount.text = LedgerUi.money(ctx, LedgerDoc.amountOf(e))
            if (hit != null && i in hit.lines) lb.root.setBackgroundResource(R.drawable.bg_line_match)
            if (doc.isGoods) lb.root.setOnClickListener { productHistory(e.product) } else lb.root.isClickable = false
            b.lines.addView(lb.root)
        }
        b.totals.removeAllViews()
        if (doc.isGoods) addTotal(getString(R.string.lg_items_qty), getString(R.string.lg_items_n, doc.goods.size) + " · " + NumFmt.qty(doc.qty), false)
        if (!doc.discount.isZero) {
            addTotal(getString(R.string.lg_total), LedgerUi.money(ctx, doc.total), false)
            addTotal(getString(R.string.lg_discount), "−" + LedgerUi.money(ctx, doc.discount), false)
            addTotal(getString(R.string.lg_payable), LedgerUi.money(ctx, doc.total - doc.discount), true)
        } else addTotal(getString(R.string.lg_total), LedgerUi.money(ctx, doc.total), true)
        doc.balanceAfter?.let { addTotal(getString(R.string.lg_balance_after), LedgerUi.money(ctx, it), false) }

        val repeatLabel = host?.repeatLabel(doc) ?: 0
        b.btnRepeat.show(repeatLabel != 0)
        if (repeatLabel != 0) b.btnRepeat.setText(repeatLabel)
        b.btnRepeat.setOnClickListener {
            val parent = parentFragmentManager
            dismiss()
            RepeatSheet.show(parent, doc.key)
        }
        b.btnReceipt.setOnClickListener { ReceiptActions.preview(this, doc, host, client) }
        b.btnShare.setOnClickListener { share(doc, host, client) }
    }

    private fun addTotal(label: String, value: String, bold: Boolean) {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(3), 0, dp(3)) }
        val ap = if (bold) com.google.android.material.R.style.TextAppearance_Material3_TitleMedium else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
        val l = TextView(ctx).apply { text = label; setTextAppearance(ap) }
        val v = TextView(ctx).apply { text = value; setTextAppearance(ap); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
        row.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        b.totals.addView(row)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun share(doc: LedgerDoc, host: LedgerHost?, client: String) {
        val r0 = LedgerUi.receipt(requireContext(), doc, host?.receiptHeader.orEmpty(), client, host?.receiptFooter.orEmpty(), host?.receiptStaff.orEmpty())
        val r = (host?.receiptStyle ?: uz.imsoft.shared.receipt.ReceiptStyle.STANDARD).apply(r0)
        val items = arrayOf(getString(R.string.lg_share_image), getString(R.string.lg_share_text), getString(R.string.lg_copy_text))
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lg_share)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> viewLifecycleOwner.lifecycleScope.launch { ReceiptActions.shareImage(requireContext(), r, host = host) }
                    1 -> ReceiptActions.shareText(requireContext(), ReceiptBuilder.toText(r))
                    else -> {
                        ReceiptActions.copyText(requireContext(), ReceiptBuilder.toText(r))
                        _b?.let { Snackbar.make(it.root, R.string.lg_copied, Snackbar.LENGTH_SHORT).show() }
                    }
                }
            }
            .setNegativeButton(R.string.lg_cancel, null).show()
    }

    /** Tovar tarixi: qachon, qancha, necha puldan. */
    private fun productHistory(name: String) {
        val ctx = requireContext()
        val list = ClientStats.productHistory(ledger()?.viewModel()?.entries().orEmpty(), LedgerMerge.nameKey(name))
        if (list.isEmpty()) return
        val bought = list.filter { !it.returned }
        val qty = bought.sumOf { it.qty }
        var sum = Money2.ZERO
        bought.forEach { sum += it.sum }
        val sb = StringBuilder()
        sb.append(getString(R.string.lg_ph_summary, bought.size, NumFmt.qty(qty), LedgerUi.money(ctx, sum))).append("\n\n")
        for (p in list.take(60)) {
            sb.append(Stamp.formatDate(p.stamp)).append("  ")
            if (p.returned) sb.append(getString(R.string.lg_kind_return)).append(" ")
            sb.append(NumFmt.qty(p.qty)).append(" × ").append(NumFmt.price(p.price)).append(" = ").append(LedgerUi.money(ctx, p.sum)).append('\n')
        }
        if (list.size > 60) sb.append("…")
        MaterialAlertDialogBuilder(ctx).setTitle(name).setMessage(sb.toString()).setPositiveButton(R.string.lg_close, null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_KEY = "key"
        fun show(fm: FragmentManager, key: String) {
            if (fm.findFragmentByTag("doc") != null) return
            DocSheet().apply { arguments = bundleOf(ARG_KEY to key) }.show(fm, "doc")
        }
    }
}
