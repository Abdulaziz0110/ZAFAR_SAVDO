package com.ilxomjon.dur_novvot_dokon.ui.labels

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.labels.LabelBatch
import com.ilxomjon.dur_novvot_dokon.data.labels.LabelPrinterClient
import com.ilxomjon.dur_novvot_dokon.databinding.SheetLabelPrintBinding
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.scan.ScanFragment
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.label.LabelKind

/** Chop etishdan oldin: nechta yorliq, qaysilari chiqmaydi (sababi va tuzatish), namunalar va "Chop etish". */
class LabelPrintSheet : BottomSheetDialogFragment() {
    private var _b: SheetLabelPrintBinding? = null
    private val b get() = _b!!
    private val repo get() = ServiceLocator.labels
    private val kind get() = LabelKind.valueOf(requireArguments().getString(ARG_KIND, LabelKind.PLU.name))
    private var batch: LabelBatch? = null
    private var busy = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        d.behavior.skipCollapsed = true
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetLabelPrintBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.tvTitle.setText(if (kind == LabelKind.PLU) R.string.lb_print_plu_title else R.string.lb_print_barcode_title)
        b.btnPrint.setOnClickListener { print() }
        b.tvPrinter.setOnClickListener { LabelPrinterSheet.show(parentFragmentManager) }
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        if (batch != null && !busy) rebuild()
    }

    private fun rebuild() {
        viewLifecycleOwner.lifecycleScope.launch {
            val bt = repo.build(kind)
            batch = bt
            val bb = _b ?: return@launch
            bb.tvSummary.text = getString(R.string.lb_summary, bt.total, bt.labels.size, mm(bt.wPx), mm(bt.hPx))
            showWarnings(bt)
            bb.tvPrinter.text = printerText()
            bb.btnPrint.isEnabled = bt.labels.isNotEmpty() && !busy
            bb.btnPrint.text = if (bt.labels.isEmpty()) getString(R.string.lb_nothing) else getString(R.string.lb_print_n, bt.total)
            renderPreview(bt)
        }
    }

    /** Nuqtadan mm (ko'rsatish uchun). */
    private fun mm(px: Int): Int {
        val dpi = repo.state.value.info?.dpi?.takeIf { it > 0 && !repo.state.value.lpd } ?: repo.state.value.dpi
        return Math.round(px * 25.4 / dpi).toInt()
    }

    private fun printerText(): String {
        val s = repo.state.value
        val cloudOk = s.cloud && ServiceLocator.shareStore.isCloudReady
        val pr = s.info?.printer?.ifBlank { null } ?: "—"
        if (com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.viaCloudAgent) return getString(R.string.lb_printer_admin)
        if (s.lpd) return if (s.host.isBlank()) getString(R.string.lb_route_none) else getString(R.string.lb_printer_lpd, s.queue, s.host)
        return when {
            s.host.isNotBlank() && cloudOk -> getString(R.string.lb_route_lan_cloud, pr, s.host)
            s.host.isNotBlank() -> getString(R.string.lb_route_lan, pr, s.host)
            cloudOk -> getString(R.string.lb_route_cloud, pr)
            else -> getString(R.string.lb_route_none)
        }
    }

    private fun showWarnings(bt: LabelBatch) {
        val bb = _b ?: return
        val byId = ServiceLocator.catalog.state.value.productsById
        if (bt.skipped.isEmpty()) { bb.boxWarn.show(false); return }
        bb.boxWarn.show(true)
        val groups = bt.skipped.groupBy({ it.second }, { it.first })
        val sb = StringBuilder()
        for ((why, ids) in groups) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            val title = when (why) {
                LabelBatch.Skip.NO_PLU -> R.string.lb_skip_plu
                LabelBatch.Skip.NO_BARCODE -> R.string.lb_skip_barcode
                LabelBatch.Skip.NO_PRICE -> R.string.lb_skip_price
                LabelBatch.Skip.NOT_IN_CATALOG -> R.string.lb_skip_catalog
            }
            sb.append(getString(title, ids.size))
            ids.take(5).forEach { sb.append("\n• ").append(byId[it]?.name ?: it) }
            if (ids.size > 5) sb.append("\n").append(getString(R.string.lb_and_more, ids.size - 5))
        }
        bb.tvWarn.text = sb
        val noPlu = groups[LabelBatch.Skip.NO_PLU].orEmpty()
        when {
            noPlu.isNotEmpty() -> {
                bb.btnFix.show(true); bb.btnFix.setText(R.string.lb_fix_plu)
                bb.btnFix.setOnClickListener { askPlu(noPlu, 0) }
            }
            groups.containsKey(LabelBatch.Skip.NO_BARCODE) -> {
                bb.btnFix.show(true); bb.btnFix.setText(R.string.lb_fix_barcode)
                bb.btnFix.setOnClickListener { dismiss(); navigator().push(ScanFragment.link()) }
            }
            else -> bb.btnFix.show(false)
        }
    }

    /** PLU raqami yo'q tovarlar uchun birin-ketin so'rash. */
    private fun askPlu(ids: List<String>, i: Int) {
        if (i >= ids.size || _b == null) { rebuild(); return }
        val p = ServiceLocator.catalog.state.value.productsById[ids[i]] ?: return askPlu(ids, i + 1)
        val ctx = requireContext()
        val til = TextInputLayout(ctx).apply { hint = getString(R.string.lb_plu); helperText = getString(R.string.lb_n_of, i + 1, ids.size) }
        val e = TextInputEditText(til.context).apply { inputType = InputType.TYPE_CLASS_NUMBER; isSingleLine = true }
        til.addView(e)
        val box = FrameLayout(ctx).apply { setPadding(48, 16, 48, 0); addView(til) }
        MaterialAlertDialogBuilder(ctx).setTitle(p.name).setView(box)
            .setPositiveButton(R.string.save) { _, _ -> repo.setPlu(p.id, e.text?.toString()); askPlu(ids, i + 1) }
            .setNeutralButton(R.string.lb_skip) { _, _ -> askPlu(ids, i + 1) }
            .setNegativeButton(R.string.cancel) { _, _ -> rebuild() }
            .show()
        e.requestFocus()
    }

    private fun renderPreview(bt: LabelBatch) {
        val ctx = requireContext()
        val shown = bt.labels.take(PREVIEW)
        viewLifecycleOwner.lifecycleScope.launch {
            val bmps: List<Bitmap> = withContext(Dispatchers.Default) { shown.map { repo.render(bt, it) } }
            val bb = _b ?: return@launch
            bb.previewList.removeAllViews()
            val dp = resources.displayMetrics.density
            val maxW = (resources.displayMetrics.widthPixels - 40 * dp).toInt()
            for ((i, bmp) in bmps.withIndex()) {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt()) }
                val w = minOf(maxW, (bmp.width * 1.1f * dp / 1.6f).toInt().coerceAtLeast((200 * dp).toInt()))
                val h = (w.toFloat() * bmp.height / bmp.width).toInt()
                val iv = ImageView(ctx).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_XY
                    setBackgroundResource(R.drawable.bg_line_match)
                    setPadding(2, 2, 2, 2)
                    contentDescription = shown[i].name
                }
                row.addView(iv, LinearLayout.LayoutParams(w, h))
                row.addView(TextView(ctx).apply {
                    text = getString(R.string.lb_copies_n, shown[i].copies)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    textSize = 12f
                })
                bb.previewList.addView(row)
            }
            bb.tvMore.show(bt.labels.size > PREVIEW)
            bb.tvMore.text = getString(R.string.lb_and_more, bt.labels.size - PREVIEW)
        }
    }

    private fun print() {
        val bt = batch ?: return
        if (bt.labels.isEmpty() || busy) return
        busy = true
        b.progress.showInvisible(true)
        b.btnPrint.isEnabled = false
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = repo.print(bt)
                val msg = when (r) {
                    is LabelPrinterClient.Sent.Printed -> app.getString(R.string.lb_printed, r.count, r.printer.ifBlank { "Xprinter" })
                    is LabelPrinterClient.Sent.Queued -> app.getString(R.string.lb_queued, r.count)
                }
                Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                // v5.2 (A1): chop etilgandan keyin tanlangan mahsulotlar ro'yxati bo'shaydi — keyingi to'plamga tayyor.
                repo.clear()
                dismiss()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                busy = false
                val bb = _b ?: return@launch
                bb.progress.showInvisible(false)
                bb.btnPrint.isEnabled = true
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lb_print_fail)
                    .setMessage(errorText(e))
                    .setPositiveButton(R.string.lb_printer_title) { _, _ -> LabelPrinterSheet.show(parentFragmentManager) }
                    .setNegativeButton(R.string.close, null).show()
            }
        }
    }

    private fun errorText(e: Exception): String = requireContext().labelErrorText(e)

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_KIND = "kind"
        private const val PREVIEW = 4
        fun show(fm: FragmentManager, kind: LabelKind) {
            if (fm.findFragmentByTag("label_print") != null) return
            LabelPrintSheet().apply { arguments = Bundle().apply { putString(ARG_KIND, kind.name) } }.show(fm, "label_print")
        }
    }
}
