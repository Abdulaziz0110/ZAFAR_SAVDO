package uz.imsoft.mijoz.ui.ledger

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.ItemRepeatLineBinding
import uz.imsoft.mijoz.databinding.SheetLedgerRepeatBinding
import uz.imsoft.mijoz.ui.show
import kotlinx.coroutines.launch
import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.report.Num

/**
 * Takrorlash (1-ilova: savatchaga) yoki qayta buyurtma (2-ilova: 1-ilovaga so'rov).
 * Har qator: belgilash, miqdorni o'zgartirish; katalogda topilmagan yoki taxminiy bog'langan qator oldindan belgilanmaydi.
 */
class RepeatSheet : BottomSheetDialogFragment() {
    private var _b: SheetLedgerRepeatBinding? = null
    private val b get() = _b!!
    private var plan: RepeatPlan? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetLedgerRepeatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = ledgerHost() ?: run { dismissAllowingStateLoss(); return }
        val key = requireArguments().getString(ARG_KEY).orEmpty()
        val doc = (parentFragment as? LedgerFragment)?.viewModel()?.docByKey(key)
        val pre = pendingPlan
        pendingPlan = null
        b.progress.show(true)
        b.btnConfirm.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val p = pre ?: if (doc != null) runCatching { host.prepareRepeat(requireContext(), doc) }.getOrNull() else null
            if (p == null) { dismissAllowingStateLoss(); return@launch }
            plan = p
            b.progress.show(false)
            render(p)
        }
        b.btnConfirm.setOnClickListener { submit(host) }
    }

    private fun render(p: RepeatPlan) {
        b.tvTitle.text = p.title
        b.tvHint.text = p.hint
        b.tvHint.show(p.hint.isNotEmpty())
        b.togglePrice.show(p.showPriceType)
        b.togglePrice.check(if (p.defaultWholesale) R.id.btnWholesale else R.id.btnCash)
        b.tilNote.show(p.showNote)
        b.btnConfirm.setText(p.confirmLabel)
        b.lines.removeAllViews()
        val inf = LayoutInflater.from(requireContext())
        for (l in p.lines) {
            val lb = ItemRepeatLineBinding.inflate(inf, b.lines, false)
            bindLine(lb, l)
            b.lines.addView(lb.root)
        }
        // Ro'yxat uzun bo'lsa — o'zi aylanadi, tasdiqlash tugmasi doim ko'rinadi.
        val maxH = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        b.scroll.layoutParams = b.scroll.layoutParams.apply { height = if (p.lines.size > 6) maxH else ViewGroup.LayoutParams.WRAP_CONTENT }
        updateConfirm()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.apply {
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun bindLine(lb: ItemRepeatLineBinding, l: RepeatLine) {
        val ctx = requireContext()
        lb.tvName.text = l.name
        val status = when (l.status) {
            MatchStatus.EXACT -> ""
            MatchStatus.APPROX -> getString(R.string.lg_rp_approx, l.matchedName)
            MatchStatus.NONE -> getString(R.string.lg_rp_none)
            MatchStatus.UNCHECKED -> ""
        }
        lb.tvStatus.text = status
        lb.tvStatus.show(status.isNotEmpty())
        lb.tvStatus.setTextColor(
            com.google.android.material.color.MaterialColors.getColor(
                ctx, if (l.status == MatchStatus.NONE) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorTertiary, 0,
            ),
        )
        lb.check.isChecked = l.checked
        lb.check.isEnabled = l.status != MatchStatus.NONE
        lb.check.setOnCheckedChangeListener { _, on -> l.checked = on; updateConfirm() }
        lb.btnQty.text = NumFmt.qty(l.qty)
        lb.btnQty.isEnabled = l.status != MatchStatus.NONE
        lb.btnQty.setOnClickListener { editQty(l) { lb.btnQty.text = NumFmt.qty(l.qty); lb.check.isChecked = true } }
        lb.root.setOnClickListener { if (lb.check.isEnabled) lb.check.toggle() }
    }

    private fun editQty(l: RepeatLine, done: () -> Unit) {
        val til = TextInputLayout(requireContext()).apply { setPadding(48, 16, 48, 0) }
        val edt = TextInputEditText(til.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(NumFmt.qty(l.qty).replace(" ", ""))
            uz.imsoft.shared.android.NumInput.attach(this)
            selectAll()
        }
        til.addView(edt)
        MaterialAlertDialogBuilder(requireContext()).setTitle(l.name).setView(til)
            .setPositiveButton(R.string.lg_ok) { _, _ ->
                val v = uz.imsoft.shared.NumText.parseOrNull(edt.text?.toString())
                if (v != null && v > 0) { l.qty = v; done() }
            }
            .setNegativeButton(R.string.lg_cancel, null).show()
        edt.requestFocus()
    }

    private fun updateConfirm() {
        val p = plan ?: return
        val n = p.lines.count { it.checked && it.status != MatchStatus.NONE }
        b.btnConfirm.isEnabled = n > 0
        b.tvCount.text = getString(R.string.lg_rp_selected, n, p.lines.size)
    }

    private fun submit(host: LedgerHost) {
        val p = plan ?: return
        val chosen = p.lines.filter { it.checked && it.status != MatchStatus.NONE }
        if (chosen.isEmpty()) return
        if (p.options.isEmpty()) { send(host, p, chosen, -1); return }
        MaterialAlertDialogBuilder(requireContext()).setTitle(p.optionsTitle)
            .setItems(p.options.toTypedArray()) { _, i -> send(host, p, chosen, i) }
            .setNegativeButton(R.string.lg_cancel, null).show()
    }

    private fun send(host: LedgerHost, p: RepeatPlan, chosen: List<RepeatLine>, option: Int) {
        b.btnConfirm.isEnabled = false
        b.progress.show(true)
        val wholesale = b.togglePrice.checkedButtonId == R.id.btnWholesale
        val note = b.edtNote.text?.toString().orEmpty().trim()
        val act = requireActivity()
        val root = act.findViewById<View>(android.R.id.content)
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            var ok = true
            val msg = runCatching { host.submitRepeat(ctx, p, chosen, wholesale, note, option) }
                .getOrElse { e -> ok = false; act.getString(R.string.lg_error, e.message ?: e.javaClass.simpleName) }
            dismissAllowingStateLoss()
            if (ok) host.afterSubmit(act, p)
            if (msg.isNotEmpty()) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_KEY = "key"
        /** Tayyor reja bilan ochish (masalan 1-ilovada kelgan buyurtma). Faqat bitta, darhol ishlatiladi. */
        private var pendingPlan: RepeatPlan? = null

        fun show(fm: FragmentManager, docKey: String) {
            if (fm.findFragmentByTag("repeat") != null) return
            RepeatSheet().apply { arguments = bundleOf(ARG_KEY to docKey) }.show(fm, "repeat")
        }

        fun showPlan(fm: FragmentManager, plan: RepeatPlan) {
            if (fm.findFragmentByTag("repeat") != null) return
            pendingPlan = plan
            RepeatSheet().apply { arguments = bundleOf(ARG_KEY to "") }.show(fm, "repeat")
        }
    }
}
