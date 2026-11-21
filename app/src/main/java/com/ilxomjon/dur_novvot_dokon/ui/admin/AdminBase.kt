package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.report.Stamp

/** v3.2 kod bilan yig'iladigan ekranlar uchun asos: sarlavha + orqaga, xabar, xavfsiz ishga tushirish. */
abstract class CodeFragment : Fragment() {
    companion object {
        const val ARG_ROOT = "root_tab"
    }

    protected val ctx: Context get() = requireContext()

    abstract fun build(inflater: LayoutInflater): View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = build(inflater)

    /** v5.0: pastki menyudan ochilgan ekranda orqaga tugmasi bo'lmaydi. */
    protected val isRootTab: Boolean get() = arguments?.getBoolean(ARG_ROOT) == true

    protected fun page(title: Int): Ui.Page =
        Ui.page(requireContext(), getString(title), if (isRootTab) 0 else R.drawable.ic_arrow_back) { navigator().pop() }

    /** Shu ekranni pastki menyu bo'limi sifatida ochish uchun. */
    fun asRootTab(): CodeFragment = apply { arguments = (arguments ?: Bundle()).apply { putBoolean(ARG_ROOT, true) } }

    protected fun listPage(title: Int, onRow: (Int) -> Unit): Ui.ListPage =
        Ui.listPage(requireContext(), getString(title), R.drawable.ic_arrow_back, { navigator().pop() }, onRow)

    protected fun snack(text: CharSequence) {
        val v = view ?: return
        Snackbar.make(v, text, Snackbar.LENGTH_LONG).show()
    }

    /** Xatoni foydalanuvchiga matn qilib ko'rsatadi; Fragment yopilgan bo'lsa jim. */
    protected fun launchUi(block: suspend CoroutineScope.() -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) snack(getString(R.string.ad_error, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    protected fun som(v: Double): String = ctx.money(v, Currency.SOM)
    protected fun usd(v: Double): String = ctx.money(v, Currency.USD)

    /** "1 500 000 so'm + 12 $" — nol qismlar ko'rsatilmaydi. */
    protected fun money2(s: Double, u: Double): String {
        val parts = ArrayList<String>()
        if (s != 0.0) parts += som(s)
        if (u != 0.0) parts += usd(u)
        return if (parts.isEmpty()) som(0.0) else parts.joinToString(" + ")
    }

    protected fun kindName(k: String): String = getString(
        when (k) {
            OneCDoc.SALE -> R.string.oc_k_sale
            OneCDoc.SALE_MOBILE -> R.string.oc_k_mobile
            OneCDoc.RETAIL -> R.string.oc_k_retail
            OneCDoc.CLIENT_RETURN -> R.string.oc_k_return
            OneCDoc.RECEIPT -> R.string.oc_k_receipt
            OneCDoc.SUPPLIER_PAY -> R.string.oc_k_pay
            OneCDoc.SUPPLIER_RETURN -> R.string.oc_k_sret
            else -> R.string.oc_k_other
        },
    )

    /** v3.12: hujjat turi ikonkasi. */
    protected fun docIcon(k: String): Int = when (k) {
        OneCDoc.SALE, OneCDoc.SALE_MOBILE, OneCDoc.RETAIL -> R.drawable.ic_receipt
        OneCDoc.CLIENT_RETURN, OneCDoc.SUPPLIER_RETURN -> R.drawable.ic_assignment_return
        OneCDoc.RECEIPT -> R.drawable.ic_truck
        OneCDoc.SUPPLIER_PAY -> R.drawable.ic_payments
        else -> R.drawable.ic_list
    }

    /** v5.3.13: yuk beruvchi hujjati cheki; to'lovda oldingi qarz shu yuk beruvchining hozirgi qarzi va hujjatlaridan. */
    protected fun supplierChek(d: OneCDoc) {
        val sups = com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.onec.state.value.suppliers?.list.orEmpty()
        val sup = sups.firstOrNull { s -> s.docs.any { it.id == d.id } } ?: sups.firstOrNull { it.name == d.party }
        val before = if (d.kind == OneCDoc.SUPPLIER_PAY && sup != null) {
            uz.imsoft.shared.receipt.SupplierChek.oweBefore(sup.docs, uz.imsoft.shared.report.Money2(sup.oweSom, sup.oweUsd), d)
        } else null
        val r = com.ilxomjon.dur_novvot_dokon.ui.history.OrderReceipt.ofSupplierDoc(ctx, d, before)
        com.ilxomjon.dur_novvot_dokon.ui.history.OrderReceipt.show(this, r, d.stamp)
    }

    /** Hujjat tafsiloti (qatorlar) oynasi. */
    protected fun showDoc(d: OneCDoc) {
        val sb = StringBuilder()
        sb.append(getString(R.string.oc_doc_head, kindName(d.kind), d.number, Stamp.formatDateTime(d.stamp))).append('\n')
        if (d.party.isNotEmpty()) sb.append(d.party).append('\n')
        sb.append('\n')
        d.lines.forEachIndexed { i, l ->
            val price = if (l.priceUsd != 0.0 && l.priceSom == 0.0) usd(l.priceUsd) else som(l.priceSom)
            val sum = if (l.sumUsd != 0.0 && l.sumSom == 0.0) usd(l.sumUsd) else som(l.sumSom)
            sb.append("${i + 1}) ").append(l.name).append('\n')
            sb.append("    ").append(getString(R.string.oc_line, fmtQty(l.qty), price, sum)).append('\n')
        }
        sb.append('\n').append(getString(R.string.oc_doc_total, money2(d.som, d.usd)))
        if (d.note.isNotBlank()) sb.append('\n').append(d.note)
        val b = MaterialAlertDialogBuilder(ctx).setTitle(d.party.ifEmpty { kindName(d.kind) }).setMessage(sb.toString())
        // v5.2 (A5): tovarli hujjatni shu yerdan ham o'zgartirsa bo'ladi
        // v5.3.13: kirim / yuk beruvchiga to'lov / qaytarish — chek (savdo chekimiz ko'rinishida)
        if (uz.imsoft.shared.receipt.SupplierChek.supports(d.kind)) b.setNeutralButton(R.string.ck_button) { _, _ -> supplierChek(d) }
        if (d.kind in uz.imsoft.shared.onec.DocField.EDITABLE && d.lines.isNotEmpty()) {
            b.setPositiveButton(R.string.de_edit) { _, _ -> navigator().push(DocEditFragment.of(d.id)) }
            b.setNegativeButton(R.string.close, null)
        } else {
            b.setPositiveButton(R.string.close, null)
        }
        b.show()
    }

    protected fun fmtQty(q: Double): String = if (q == Math.floor(q)) q.toLong().toString() else "%.3f".format(java.util.Locale.US, q).trimEnd('0').trimEnd('.')
}
