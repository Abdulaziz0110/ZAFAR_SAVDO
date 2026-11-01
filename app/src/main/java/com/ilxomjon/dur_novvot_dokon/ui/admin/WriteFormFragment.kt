package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteLine
import uz.imsoft.shared.onec.WriteRequest

/**
 * v3.2 Admin: 1C'ga kiritish shakli — kirim, ta'minotchiga to'lov, ta'minotchiga qaytarish, mijoz qaytargan tovar.
 * "Tasdiqlash" — xulosa ko'rsatiladi, egasi rozi bo'lsa navbatga qo'yiladi; kompyuter zaxira olib 1C'ga kiritadi.
 */
class WriteFormFragment : CodeFragment() {
    private val kind: String get() = arguments?.getString(ARG_KIND).orEmpty()
    private var party = ""
    private var partyCode = ""
    private val lines = ArrayList<WriteLine>()
    private lateinit var tvParty: TextView
    private lateinit var linesBox: LinearLayout
    private lateinit var tvTotal: TextView
    private lateinit var eNote: TextInputEditText
    private var eCashSom: TextInputEditText? = null
    private var eCashUsd: TextInputEditText? = null
    private var eCardSom: TextInputEditText? = null
    private var eCardUsd: TextInputEditText? = null
    private var sending = false

    private val isGoods: Boolean get() = kind != WriteKind.SUPPLIER_PAY

    override fun build(inflater: LayoutInflater): View {
        if (party.isEmpty()) {
            party = arguments?.getString(ARG_PARTY).orEmpty()
            partyCode = arguments?.getString(ARG_CODE).orEmpty()
        }
        val title = when (kind) {
            WriteKind.RECEIPT -> R.string.wf_receipt
            WriteKind.SUPPLIER_PAY -> R.string.wf_pay
            WriteKind.SUPPLIER_RETURN -> R.string.wf_sreturn
            else -> R.string.wf_creturn
        }
        val p = page(title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.wf_hint), 4))
        c.addView(Ui.section(ctx, getString(if (kind == WriteKind.CLIENT_RETURN) R.string.wf_client else R.string.wf_supplier)))
        tvParty = Ui.title(ctx, "", 4).also { c.addView(it) }
        c.addView(Ui.button(ctx, getString(R.string.wf_pick), outlined = true) { pickParty() })
        if (isGoods) {
            c.addView(Ui.section(ctx, getString(R.string.wf_goods)))
            linesBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            c.addView(linesBox)
            c.addView(Ui.button(ctx, getString(R.string.wf_add)) { addLine() })
        } else {
            c.addView(Ui.section(ctx, getString(R.string.wf_amounts)))
            eCashSom = Ui.field(ctx, getString(R.string.wf_cash_som), Ui.NUMBER).also { c.addView(it.first) }.second
            eCashUsd = Ui.field(ctx, getString(R.string.wf_cash_usd), Ui.NUMBER).also { c.addView(it.first) }.second
            eCardSom = Ui.field(ctx, getString(R.string.wf_card_som), Ui.NUMBER).also { c.addView(it.first) }.second
            eCardUsd = Ui.field(ctx, getString(R.string.wf_card_usd), Ui.NUMBER).also { c.addView(it.first) }.second
        }
        eNote = Ui.field(ctx, getString(R.string.wf_note), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE).also { c.addView(it.first) }.second
        tvTotal = Ui.title(ctx, "", 12).also { c.addView(it) }
        c.addView(Ui.button(ctx, getString(R.string.wf_confirm), top = 12) { confirm() })
        c.addView(Ui.hint(ctx, getString(R.string.wf_safety), 8))
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, b ->
            val st = ServiceLocator.catalog.storeById(b.getString(StorePickerSheet.KEY_ID).orEmpty()) ?: return@setFragmentResultListener
            party = st.name
            partyCode = ""
            render()
        }
        launchUi { ServiceLocator.onec.load() }
        render()
    }

    private fun render() {
        tvParty.text = party.ifEmpty { getString(R.string.wf_not_chosen) }
        if (isGoods) {
            linesBox.removeAllViews()
            lines.forEachIndexed { i, l ->
                val price = if (l.priceSom > 0) som(l.priceSom) else usd(l.priceUsd)
                val sum = if (l.priceSom > 0) som(l.sumSom) else usd(l.sumUsd)
                linesBox.addView(Ui.item(ctx, "${i + 1}) ${l.name}", getString(R.string.oc_line, fmtQty(l.qty), price, sum)) { editLine(i) })
            }
            if (lines.isEmpty()) linesBox.addView(Ui.hint(ctx, getString(R.string.wf_no_goods)))
            val r = request()
            tvTotal.text = getString(R.string.wf_total, money2(r.totalSom, r.totalUsd))
        } else tvTotal.text = ""
    }

    private fun pickParty() {
        if (kind == WriteKind.CLIENT_RETURN) { StorePickerSheet.show(childFragmentManager, REQ_STORE); return }
        val list = ServiceLocator.onec.state.value.suppliers?.list.orEmpty()
        if (list.isEmpty()) {
            // Ro'yxat hali kelmagan — nom qo'lda (1C'dagidek aniq)
            val (til, e) = Ui.field(ctx, getString(R.string.wf_supplier_name), value = party)
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), 0, Ui.dp(ctx, 20), 0); addView(til) }
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_supplier).setMessage(R.string.wf_supplier_manual).setView(box)
                .setPositiveButton(R.string.ok) { _, _ -> party = e.text?.toString()?.trim().orEmpty(); partyCode = ""; render() }
                .setNegativeButton(R.string.cancel, null).show()
            return
        }
        val names = list.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_supplier).setItems(names) { _, i ->
            party = list[i].name
            partyCode = list[i].code
            render()
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun addLine() {
        Pickers.product(ctx) { p ->
            val rate = ServiceLocator.catalog.state.value.rate
            val usdMain = !p.baseIsSom
            val (ps, pu) = when (kind) {
                WriteKind.CLIENT_RETURN -> Pricing.unitPrice(p, PriceType.WHOLESALE, com.ilxomjon.dur_novvot_dokon.domain.Currency.SOM, rate) to
                    Pricing.unitPrice(p, PriceType.WHOLESALE, com.ilxomjon.dur_novvot_dokon.domain.Currency.USD, rate)
                else -> p.costSom to p.costUsd
            }
            Pickers.qtyPrice(ctx, p.name, 0.0, ps, pu, usdMain) { q, s, u ->
                lines += WriteLine(p.name, p.id, q, if (usdMain) 0.0 else s, if (usdMain) u else 0.0)
                render()
            }
        }
    }

    private fun editLine(i: Int) {
        val l = lines.getOrNull(i) ?: return
        MaterialAlertDialogBuilder(ctx).setTitle(l.name)
            .setItems(arrayOf(getString(R.string.wf_edit), getString(R.string.wf_remove))) { _, w ->
                if (w == 1) { lines.removeAt(i); render() }
                else Pickers.qtyPrice(ctx, l.name, l.qty, l.priceSom, l.priceUsd, l.priceSom == 0.0 && l.priceUsd > 0) { q, s, u ->
                    val usdMain = l.priceSom == 0.0 && l.priceUsd > 0
                    lines[i] = l.copy(qty = q, priceSom = if (usdMain) 0.0 else s, priceUsd = if (usdMain) u else 0.0)
                    render()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun num(e: TextInputEditText?): Double = Money.parseUser(e?.text?.toString()) ?: 0.0

    private fun request(id: String = "w"): WriteRequest = WriteRequest(
        id, kind, System.currentTimeMillis(), party, partyCode, if (::eNote.isInitialized) eNote.text?.toString()?.trim().orEmpty() else "",
        if (isGoods) lines.toList() else emptyList(), num(eCashSom), num(eCashUsd), num(eCardSom), num(eCardUsd),
    )

    private fun confirm() {
        if (sending) return
        val r = request(WriteCodec.newId())
        when (r.problem()) {
            null -> Unit
            "party" -> { snack(getString(R.string.wf_err_party)); return }
            "lines" -> { snack(getString(R.string.wf_err_lines)); return }
            "amount" -> { snack(getString(R.string.wf_err_amount)); return }
            else -> { snack(getString(R.string.wf_err_line)); return }
        }
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        val summary = buildString {
            append(tvParty.text).append('\n')
            if (isGoods) {
                append(getString(R.string.wf_sum_lines, r.lines.size)).append('\n')
                append(getString(R.string.wf_total, money2(r.totalSom, r.totalUsd)))
            } else append(getString(R.string.wf_total, money2(r.totalSom, r.totalUsd)))
            if (r.note.isNotEmpty()) append('\n').append(r.note)
            append("\n\n").append(getString(R.string.wf_confirm_text))
        }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_confirm_title).setMessage(summary)
            .setPositiveButton(R.string.wf_confirm_yes) { _, _ ->
                sending = true
                launchUi {
                    try {
                        val item = ServiceLocator.onec.submit(r, "Admin")
                        snack(getString(if (item.sendError.isEmpty()) R.string.wf_sent else R.string.wf_sent_later))
                        navigator().pop()
                        navigator().push(WritesFragment())
                    } finally {
                        sending = false
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    companion object {
        private const val ARG_KIND = "kind"
        private const val ARG_PARTY = "party"
        private const val ARG_CODE = "code"
        private const val REQ_STORE = "wf_store"

        fun of(kind: String, party: String, code: String) = WriteFormFragment().apply {
            arguments = Bundle().apply { putString(ARG_KIND, kind); putString(ARG_PARTY, party); putString(ARG_CODE, code) }
        }
    }
}
