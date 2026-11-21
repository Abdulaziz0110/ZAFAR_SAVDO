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
import com.ilxomjon.dur_novvot_dokon.ui.show
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
    private lateinit var tvDebt: TextView
    private lateinit var linesBox: LinearLayout
    private lateinit var tvTotal: TextView
    private lateinit var eNote: TextInputEditText
    private var eCashSom: TextInputEditText? = null
    private var eCashUsd: TextInputEditText? = null
    private var eCardSom: TextInputEditText? = null
    private var eCardUsd: TextInputEditText? = null
    private var sending = false
    private var btnHistory: com.google.android.material.button.MaterialButton? = null

    private val isGoods: Boolean get() = kind != WriteKind.SUPPLIER_PAY
    /** v4.1: hisobdan chiqarish — kontragentsiz, faqat tovar va miqdor (tannarx 1C qoldig'idan). */
    private val isWriteOff: Boolean get() = kind == WriteKind.WRITE_OFF

    override fun build(inflater: LayoutInflater): View {
        if (party.isEmpty()) {
            party = arguments?.getString(ARG_PARTY).orEmpty()
            partyCode = arguments?.getString(ARG_CODE).orEmpty()
        }
        val title = when (kind) {
            WriteKind.RECEIPT -> R.string.wf_receipt
            WriteKind.SUPPLIER_PAY -> R.string.wf_pay
            WriteKind.SUPPLIER_RETURN -> R.string.wf_sreturn
            WriteKind.WRITE_OFF -> R.string.wo_title
            else -> R.string.wf_creturn
        }
        val p = page(title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(if (isWriteOff) R.string.wo_hint else R.string.wf_hint), 4))
        tvParty = Ui.title(ctx, "", 4)
        tvDebt = Ui.hint(ctx, "", 2)
        if (!isWriteOff) {
            c.addView(Ui.section(ctx, getString(if (kind == WriteKind.CLIENT_RETURN) R.string.wf_client else R.string.wf_supplier)))
            c.addView(tvParty)
            c.addView(tvDebt)
            val hist = Ui.button(ctx, getString(R.string.wf_history), outlined = true, icon = R.drawable.ic_history) { showHistory() }
            btnHistory = hist
            c.addView(
                Ui.buttonRow(
                    ctx,
                    Ui.button(ctx, getString(R.string.wf_pick), outlined = true, icon = R.drawable.ic_person) { pickParty() },
                    hist,
                ),
            )
        }
        if (isGoods) {
            c.addView(Ui.section(ctx, getString(R.string.wf_goods)))
            linesBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            c.addView(linesBox)
            c.addView(Ui.button(ctx, getString(R.string.wf_add), icon = R.drawable.ic_add) { addLine() })
            // v5.3.8: rasm orqali kirim (yuk xati, chek, qo'lda yozilgan ro'yxat)
            if (kind == WriteKind.RECEIPT) {
                c.addView(Ui.button(ctx, getString(R.string.pr_open), outlined = true, icon = R.drawable.ic_camera) { navigator().push(PhotoReceiptFragment()) })
            }
        } else {
            c.addView(Ui.section(ctx, getString(R.string.wf_amounts)))
            // v5.0.5: faqat so'm
            eCashSom = Ui.field(ctx, getString(R.string.wf_cash_som), Ui.NUMBER).also { c.addView(it.first) }.second
            eCardSom = Ui.field(ctx, getString(R.string.wf_card_som), Ui.NUMBER).also { c.addView(it.first) }.second
            // v5.2 (A10b): summa yozilayotganda to'lovdan keyingi qoldiq darhol ko'rinadi
            val live = object : android.text.TextWatcher {
                override fun afterTextChanged(sx: android.text.Editable?) = render()
                override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, cc: Int) = Unit
                override fun onTextChanged(t: CharSequence?, a: Int, b: Int, cc: Int) = Unit
            }
            eCashSom?.addTextChangedListener(live)
            eCardSom?.addTextChangedListener(live)
            // "Qarzni to'liq yopish" — bir bosishda
            c.addView(
                Ui.button(ctx, getString(R.string.wf_pay_all), outlined = true, top = 4, icon = R.drawable.ic_check) {
                    val sup = ServiceLocator.onec.state.value.suppliers?.list?.firstOrNull { it.name == party }
                    val owe = sup?.oweSom ?: 0.0
                    if (owe > 0.5) { eCashSom?.setText(com.ilxomjon.dur_novvot_dokon.core.Money.plain(owe)); render() } else snack(getString(R.string.wf_no_debt))
                },
            )
        }
        eNote = Ui.field(ctx, getString(R.string.wf_note), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE).also { c.addView(it.first) }.second
        tvTotal = Ui.title(ctx, "", 12).also { c.addView(it) }
        c.addView(Ui.button(ctx, getString(R.string.wf_confirm), top = 12, icon = R.drawable.ic_check) { confirm() })
        c.addView(Ui.hint(ctx, getString(R.string.wf_safety), 8))
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // v5.3.8: rasmdan o'qilgan qatorlar shu kirimga qo'shiladi
        parentFragmentManager.setFragmentResultListener(PhotoReceiptFragment.REQ, viewLifecycleOwner) { _, b ->
            val ids = b.getStringArray(PhotoReceiptFragment.RES_IDS).orEmpty()
            val names = b.getStringArray(PhotoReceiptFragment.RES_NAMES).orEmpty()
            val qty = b.getDoubleArray(PhotoReceiptFragment.RES_QTY) ?: DoubleArray(0)
            val ps = b.getDoubleArray(PhotoReceiptFragment.RES_SOM) ?: DoubleArray(0)
            val pu = b.getDoubleArray(PhotoReceiptFragment.RES_USD) ?: DoubleArray(0)
            for (i in ids.indices) {
                if (i < names.size && i < qty.size && i < ps.size && i < pu.size) lines += WriteLine(names[i], ids[i], qty[i], ps[i], pu[i])
            }
            val pn = b.getString(PhotoReceiptFragment.RES_PARTY).orEmpty()
            if (pn.isNotEmpty() && party.isEmpty()) { party = pn; partyCode = b.getString(PhotoReceiptFragment.RES_CODE).orEmpty() }
            val note = b.getString(PhotoReceiptFragment.RES_NOTE).orEmpty()
            if (note.isNotEmpty() && eNote.text.isNullOrBlank()) eNote.setText(note)
            render()
            snack(getString(R.string.pr_added, ids.size))
        }
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
        // v5.0.5: yuk beruvchiga to'lovda bizning qarzimiz ko'rinib tursin (1C'dagidek)
        val sup = if (party.isEmpty()) null else ServiceLocator.onec.state.value.suppliers?.list?.firstOrNull { it.name == party }
        // v5.2 (A10b): qarz, to'lovdan keyingi qoldiq va bu kishi bilan oxirgi ishlar — hammasi ko'z oldida
        val parts = ArrayList<String>(3)
        if (sup != null) {
            parts += when {
                sup.oweSom > 0.5 -> getString(R.string.wf_we_owe, som(sup.oweSom))
                sup.oweSom < -0.5 -> getString(R.string.wf_they_owe, som(-sup.oweSom))
                else -> getString(R.string.wf_no_debt)
            }
            if (kind == WriteKind.SUPPLIER_PAY) {
                val pay = num(eCashSom) + num(eCardSom)
                if (pay > 0.0) parts += getString(R.string.wf_after_pay, som(sup.oweSom - pay))
            }
            if (isGoods && lines.isNotEmpty() && sup.oweSom > 0.5) {
                parts += getString(R.string.wf_after_doc, som(sup.oweSom + lines.sumOf { it.sumSom }))
            }
        }
        tvDebt.text = parts.joinToString("\n")
        tvDebt.show(tvDebt.text.isNotEmpty())
        btnHistory?.show(sup != null && sup.docs.isNotEmpty())
        if (isGoods) {
            linesBox.removeAllViews()
            lines.forEachIndexed { i, l ->
                if (isWriteOff) {
                    linesBox.addView(Ui.item(ctx, "${i + 1}) ${l.name}", getString(R.string.wo_line, fmtQty(l.qty))) { editLine(i) })
                    return@forEachIndexed
                }
                val price = if (l.priceSom > 0) som(l.priceSom) else usd(l.priceUsd)
                val sum = if (l.priceSom > 0) som(l.sumSom) else usd(l.sumUsd)
                linesBox.addView(Ui.item(ctx, "${i + 1}) ${l.name}", getString(R.string.oc_line, fmtQty(l.qty), price, sum)) { editLine(i) })
            }
            if (lines.isEmpty()) linesBox.addView(Ui.hint(ctx, getString(R.string.wf_no_goods)))
            val r = request()
            tvTotal.text = if (isWriteOff) getString(R.string.wo_total, lines.size) else getString(R.string.wf_total, money2(r.totalSom, r.totalUsd))
        } else tvTotal.text = ""
    }

    /** v5.2 (A10b): shu kishi bilan oxirgi ishlar — qachon yuk bergan, qachon to'lov qilingan. */
    private fun showHistory() {
        val sup = ServiceLocator.onec.state.value.suppliers?.list?.firstOrNull { it.name == party } ?: return
        val docs = sup.docs.sortedByDescending { it.stamp }.take(40)
        if (docs.isEmpty()) { snack(getString(R.string.wf_no_history)); return }
        val rows = docs.map { d ->
            Ui.Row(
                docName(d.kind) + "  \u2116" + d.number,
                uz.imsoft.shared.report.Stamp.formatDateTime(d.stamp) + (if (d.note.isBlank()) "" else " \u00B7 " + d.note),
                money2(d.som, d.usd),
                icon = if (d.kind == uz.imsoft.shared.onec.OneCDoc.SUPPLIER_PAY) R.drawable.ic_payment else R.drawable.ic_receipt,
            )
        }
        Ui.pickSearch(ctx, sup.name, rows, getString(R.string.wf_history_search), getString(R.string.close), keys = docs.map { it.haystack }) { }
    }

    private fun docName(kind: String): String = getString(
        when (kind) {
            uz.imsoft.shared.onec.OneCDoc.RECEIPT -> R.string.wf_d_receipt
            uz.imsoft.shared.onec.OneCDoc.SUPPLIER_PAY -> R.string.wf_d_pay
            uz.imsoft.shared.onec.OneCDoc.SUPPLIER_RETURN -> R.string.wf_d_return
            else -> R.string.wf_d_other
        },
    )

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
        // v5.2 (A10a): qidiruvli ro'yxat — har qatorda kod, qarz va oxirgi hujjat sanasi.
        // Tartib: oxirgi ish qilinganlar tepada (shu bilan ko'p ishlatiladigani birinchi chiqadi).
        val sorted = list.sortedByDescending { s -> s.docs.maxOfOrNull { it.stamp } ?: 0L }
        val rows = sorted.map { s ->
            val owe = money2(s.oweSom, s.oweUsd)
            val last = s.docs.maxOfOrNull { it.stamp }?.takeIf { it > 0 }?.let { uz.imsoft.shared.report.Stamp.formatDate(it) }.orEmpty()
            Ui.Row(
                s.name,
                listOfNotNull(
                    getString(R.string.ad_product_code, s.code).takeIf { s.code.isNotBlank() },
                    last.takeIf { it.isNotEmpty() }?.let { getString(R.string.wf_last_doc, it) },
                ).joinToString(" \u00B7 "),
                if (s.oweSom != 0.0 || s.oweUsd != 0.0) getString(R.string.wf_we_owe_short, owe) else "",
                icon = R.drawable.ic_person,
            )
        }
        Ui.pickSearch(
            ctx, getString(R.string.wf_supplier), rows, getString(R.string.wf_supplier_search), getString(R.string.cancel),
            keys = sorted.map { it.name + " " + it.code + " " + it.phone },
        ) { i ->
            party = sorted[i].name
            partyCode = sorted[i].code
            render()
        }
    }

    private fun addLine() {
        if (isWriteOff) {
            Pickers.product(ctx) { p -> Pickers.qty(ctx, p.name, 0.0) { q -> lines += WriteLine(p.name, p.id, q, 0.0, 0.0); render() } }
            return
        }
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
                else if (isWriteOff) Pickers.qty(ctx, l.name, l.qty) { q -> lines[i] = l.copy(qty = q); render() }
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
            if (!isWriteOff) append(tvParty.text).append('\n')
            if (isWriteOff) {
                r.lines.forEach { l -> append("• ").append(l.name).append(" — ").append(fmtQty(l.qty)).append('\n') }
                append(getString(R.string.wo_cost_note))
            } else if (isGoods) {
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
