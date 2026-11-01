package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.GoodsField
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest

/**
 * v3.12 Admin: 1C'ga yangi yozuvlar — mijozdan to'lov, kassa kirim/chiqim (xarajat), tovar kartochkasi (narx 1-4,
 * kirim narxi, PLU, nomi) va yangi tovar. Hammasi "Tasdiqlash"dan keyin kompyuter navbatiga; kompyuter darhol oladi.
 */
class OneCFormFragment : CodeFragment() {
    private val kind: String get() = arguments?.getString(ARG_KIND).orEmpty()
    private var party = ""
    private var partyCode = ""
    private var op = ""
    private var opCode = ""
    private var product: Product? = null
    private val prefill = HashMap<String, String>()
    private val goodsInputs = LinkedHashMap<String, TextInputEditText>()
    private val money = LinkedHashMap<String, TextInputEditText>()
    private lateinit var tvWho: TextView
    private lateinit var tvInfo: TextView
    private lateinit var goodsBox: LinearLayout
    private var eNote: TextInputEditText? = null
    private var eName: TextInputEditText? = null
    private var eBarcode: TextInputEditText? = null
    private var sending = false

    private val isGoods get() = kind in WriteKind.GOODS
    private val isCash get() = kind == WriteKind.CASH_IN || kind == WriteKind.CASH_OUT

    override fun build(inflater: LayoutInflater): View {
        val title = when (kind) {
            WriteKind.CLIENT_PAY -> R.string.of_client_pay
            WriteKind.CASH_IN -> R.string.of_cash_in
            WriteKind.CASH_OUT -> R.string.of_cash_out
            WriteKind.GOODS_EDIT -> R.string.of_goods_edit
            else -> R.string.of_new_goods
        }
        val p = page(title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(
            when (kind) {
                WriteKind.CLIENT_PAY -> R.string.of_client_pay_hint
                WriteKind.CASH_IN -> R.string.of_cash_in_hint
                WriteKind.CASH_OUT -> R.string.of_cash_out_hint
                WriteKind.GOODS_EDIT -> R.string.of_goods_edit_hint
                else -> R.string.of_new_goods_hint
            },
        ), 4))
        c.addView(Ui.section(ctx, getString(
            when {
                kind == WriteKind.CLIENT_PAY -> R.string.wf_client
                isCash -> R.string.of_op
                kind == WriteKind.NEW_GOODS -> R.string.of_sample
                else -> R.string.of_product
            },
        )))
        tvWho = Ui.title(ctx, "", 4).also { c.addView(it) }
        tvInfo = Ui.hint(ctx, "", 2).also { c.addView(it) }
        c.addView(Ui.button(ctx, getString(R.string.wf_pick), outlined = true, icon = if (isGoods) R.drawable.ic_search else R.drawable.ic_person) { pick() })
        when {
            isGoods -> {
                if (kind == WriteKind.NEW_GOODS) {
                    c.addView(Ui.section(ctx, getString(R.string.of_new_name_sec)))
                    eName = Ui.field(ctx, getString(R.string.of_new_name)).also { c.addView(it.first) }.second
                    eBarcode = Ui.field(ctx, getString(R.string.of_barcode), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS).also { c.addView(it.first) }.second
                }
                goodsBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                c.addView(goodsBox)
            }
            else -> {
                c.addView(Ui.section(ctx, getString(R.string.wf_amounts)))
                val keys = if (kind == WriteKind.CLIENT_PAY) listOf("ns", "nu", "ps", "pu", "ks", "ku", "ds") else listOf("ns", "nu", "ps", "pu", "ks")
                for (k in keys) money[k] = Ui.field(ctx, getString(moneyLabel(k)), Ui.NUMBER).also { c.addView(it.first) }.second
            }
        }
        eNote = Ui.field(ctx, getString(R.string.wf_note), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).also { c.addView(it.first) }.second
        c.addView(Ui.button(ctx, getString(R.string.wf_confirm), top = 12, icon = R.drawable.ic_check) { confirm() })
        c.addView(Ui.hint(ctx, getString(R.string.wf_safety), 8))
        return p.root
    }

    private fun moneyLabel(k: String) = when (k) {
        "ns" -> R.string.wf_cash_som
        "nu" -> R.string.wf_cash_usd
        "ps" -> R.string.wf_card_som
        "pu" -> R.string.wf_card_usd
        "ks" -> R.string.of_click_som
        "ku" -> R.string.of_click_usd
        else -> R.string.of_discount_som
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, b ->
            val st = ServiceLocator.catalog.storeById(b.getString(StorePickerSheet.KEY_ID).orEmpty()) ?: return@setFragmentResultListener
            party = st.name
            partyCode = ""
            render()
        }
        launchUi { ServiceLocator.onec.load() }
        arguments?.getString(ARG_PRODUCT)?.takeIf { it.isNotEmpty() }?.let { id -> ServiceLocator.catalog.state.value.productsById[id]?.let { setProduct(it) } }
        render()
    }

    private fun render() {
        tvWho.text = when {
            isCash -> op.ifEmpty { getString(R.string.wf_not_chosen) }
            isGoods -> product?.name ?: getString(R.string.wf_not_chosen)
            else -> party.ifEmpty { getString(R.string.wf_not_chosen) }
        }
        tvInfo.text = when {
            isGoods && product != null -> getString(R.string.of_product_info, product?.id.orEmpty(), getString(if (product?.baseIsSom == true) R.string.of_cur_som else R.string.of_cur_usd))
            isCash && ServiceLocator.onec.state.value.suppliers?.ops.isNullOrEmpty() -> getString(R.string.of_ops_none)
            else -> ""
        }
        tvInfo.visibility = if (tvInfo.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun pick() {
        when {
            kind == WriteKind.CLIENT_PAY -> StorePickerSheet.show(childFragmentManager, REQ_STORE)
            isCash -> pickOp()
            else -> Pickers.product(ctx) { setProduct(it) }
        }
    }

    private fun pickOp() {
        val ops = ServiceLocator.onec.state.value.suppliers?.ops.orEmpty()
        if (ops.isEmpty()) {
            val (til, e) = Ui.field(ctx, getString(R.string.of_op_name), value = op)
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), 0, Ui.dp(ctx, 20), 0); addView(til) }
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.of_op).setMessage(R.string.of_ops_none).setView(box)
                .setPositiveButton(R.string.ok) { _, _ -> op = e.text?.toString()?.trim().orEmpty(); opCode = ""; render() }
                .setNegativeButton(R.string.cancel, null).show()
            return
        }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.of_op).setItems(ops.map { it.name }.toTypedArray()) { _, i ->
            op = ops[i].name
            opCode = ops[i].code
            render()
        }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Tovar tanlandi: hozirgi narxlar (1C'dan kelgan, bo'lmasa serverdagi) va PLU maydonlarga yoziladi. */
    private fun setProduct(p: Product) {
        product = p
        party = p.name
        partyCode = p.id
        prefill.clear()
        val som = p.baseIsSom
        val labels = ServiceLocator.labels
        fun put(k: String, v: Double?) { if (v != null && v > 0) prefill[k] = Money.plain(v) }
        if (som) {
            for (l in 1..4) put("p$l", labels.priceForLevel(p.id, l))
            if (!prefill.containsKey(GoodsField.P3)) put(GoodsField.P3, p.cashSom)
            if (!prefill.containsKey(GoodsField.P4)) put(GoodsField.P4, p.wholesaleSom)
        } else {
            put(GoodsField.P3, p.cashUsd)
            put(GoodsField.P4, p.wholesaleUsd)
        }
        if (kind == WriteKind.GOODS_EDIT) labels.pluFor(p.id)?.let { prefill[GoodsField.PLU] = it }
        if (kind == WriteKind.GOODS_EDIT) prefill[GoodsField.NAME] = p.name
        buildGoodsFields()
        render()
    }

    private fun buildGoodsFields() {
        if (!::goodsBox.isInitialized) return
        goodsBox.removeAllViews()
        goodsInputs.clear()
        val p = product ?: return
        val cur = getString(if (p.baseIsSom) R.string.of_cur_som else R.string.of_cur_usd)
        goodsBox.addView(Ui.section(ctx, getString(R.string.of_prices_sec, cur)))
        val labels = listOf(GoodsField.P1 to R.string.of_p1, GoodsField.P2 to R.string.of_p2, GoodsField.P3 to R.string.of_p3, GoodsField.P4 to R.string.of_p4, GoodsField.COST to R.string.of_cost)
        for ((k, res) in labels) goodsInputs[k] = Ui.field(ctx, getString(res), Ui.NUMBER, prefill[k].orEmpty()).also { goodsBox.addView(it.first) }.second
        goodsBox.addView(Ui.section(ctx, getString(R.string.of_other_sec)))
        goodsInputs[GoodsField.PLU] = Ui.field(ctx, getString(R.string.of_plu), InputType.TYPE_CLASS_NUMBER, prefill[GoodsField.PLU].orEmpty()).also { goodsBox.addView(it.first) }.second
        if (kind == WriteKind.GOODS_EDIT) goodsInputs[GoodsField.NAME] = Ui.field(ctx, getString(R.string.of_name), value = p.name).also { goodsBox.addView(it.first) }.second
        goodsBox.addView(Ui.hint(ctx, getString(R.string.of_only_changed), 4))
    }

    private fun num(e: TextInputEditText?): Double = Money.parseUser(e?.text?.toString()) ?: 0.0

    /** Tovar maydonlari: faqat o'zgarganlari (yangi tovarda — to'ldirilganlari). */
    private fun goodsFields(): Map<String, String> {
        val p = product ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((k, e) in goodsInputs) {
            val raw = e.text?.toString()?.trim().orEmpty()
            if (raw.isEmpty()) continue
            val v = when (k) {
                GoodsField.NAME -> raw.replace(Regex("\\s+"), " ")
                GoodsField.PLU -> raw.filter { it.isDigit() }.trimStart('0').ifEmpty { "0" }
                else -> Money.parseUser(raw)?.let { Money.plain(it) } ?: raw
            }
            val before = prefill[k]
            if (kind == WriteKind.GOODS_EDIT && before != null && (if (k == GoodsField.PLU || k == GoodsField.NAME) before == v else Money.parseUser(before) == Money.parseUser(v))) continue
            out[k] = v
        }
        if (kind == WriteKind.NEW_GOODS) {
            eName?.text?.toString()?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }?.let { out[GoodsField.NAME] = it }
            eBarcode?.text?.toString()?.trim()?.filter { it.isLetterOrDigit() && it.code < 128 }?.takeIf { it.isNotEmpty() }?.let { out[GoodsField.BARCODE] = it }
            if (out[GoodsField.PLU] == "0") out.remove(GoodsField.PLU)
        }
        if (out.keys.any { it in GoodsField.PRICES }) out[GoodsField.CUR] = if (p.baseIsSom) "s" else "u"
        return out
    }

    private fun request(id: String): WriteRequest {
        val m = money.mapValues { num(it.value) }
        return WriteRequest(
            id, kind, System.currentTimeMillis(), party, partyCode, eNote?.text?.toString()?.trim().orEmpty(),
            cashSom = m["ns"] ?: 0.0, cashUsd = m["nu"] ?: 0.0, cardSom = m["ps"] ?: 0.0, cardUsd = m["pu"] ?: 0.0,
            clickSom = m["ks"] ?: 0.0, clickUsd = m["ku"] ?: 0.0, discountSom = m["ds"] ?: 0.0,
            op = op, opCode = opCode, fields = if (isGoods) goodsFields() else emptyMap(),
        )
    }

    private fun summary(r: WriteRequest): String = buildString {
        append(tvWho.text).append('\n')
        if (isGoods) {
            val names = mapOf(
                GoodsField.P1 to R.string.of_p1, GoodsField.P2 to R.string.of_p2, GoodsField.P3 to R.string.of_p3, GoodsField.P4 to R.string.of_p4,
                GoodsField.COST to R.string.of_cost, GoodsField.PLU to R.string.of_plu, GoodsField.NAME to R.string.of_name, GoodsField.BARCODE to R.string.of_barcode,
            )
            for ((k, v) in r.fields) {
                val res = names[k] ?: continue
                val old = prefill[k]?.takeIf { kind == WriteKind.GOODS_EDIT }
                append("• ").append(getString(res)).append(": ").append(if (old != null) "$old → $v" else v).append('\n')
            }
        } else append(getString(R.string.wf_total, money2(r.totalSom, r.totalUsd))).append('\n')
        if (r.discountSom > 0) append(getString(R.string.of_discount_som)).append(": ").append(som(r.discountSom)).append('\n')
        if (r.note.isNotEmpty()) append(r.note).append('\n')
        append('\n').append(getString(R.string.wf_confirm_text))
    }

    private fun confirm() {
        if (sending) return
        val r = request(WriteCodec.newId())
        val err = when (r.problem()) {
            null -> null
            "party" -> getString(if (isGoods) R.string.of_err_product else R.string.wf_err_party)
            "op" -> getString(R.string.of_err_op)
            "amount" -> getString(R.string.wf_err_amount)
            "empty" -> getString(R.string.of_err_empty)
            "price" -> getString(R.string.of_err_price)
            "plu" -> getString(R.string.of_err_plu)
            "name" -> getString(R.string.of_err_name)
            "barcode" -> getString(R.string.bb_bad_code)
            else -> getString(R.string.wf_err_line)
        }
        if (err != null) { snack(err); return }
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_confirm_title).setMessage(summary(r))
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
        private const val ARG_PRODUCT = "product"
        private const val REQ_STORE = "of_store"

        fun of(kind: String, productId: String = "") = OneCFormFragment().apply {
            arguments = Bundle().apply { putString(ARG_KIND, kind); putString(ARG_PRODUCT, productId) }
        }
    }
}
