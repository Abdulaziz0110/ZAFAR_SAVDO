package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.delay
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.Supplier
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.report.Stamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** v3.2 Admin: ta'minotchilar (1C'dan): qarzimiz, kirimlar, to'lovlar, qaytarishlar. */
class SuppliersFragment : CodeFragment() {
    private lateinit var lp: Ui.ListPage
    private lateinit var tvInfo: TextView
    private var query = ""
    private var shown: List<Supplier> = emptyList()

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.sp_title) { i -> shown.getOrNull(i)?.let { navigator().push(SupplierFragment.of(it.name)) } }
        lp.header.addView(Ui.search(ctx, getString(R.string.sp_search)) { query = it.trim().lowercase(); render() }.first)
        val bNew = Ui.button(ctx, getString(R.string.sp_new_receipt), top = 6, icon = R.drawable.ic_inventory) { navigator().push(WriteFormFragment.of(WriteKind.RECEIPT, "", "")) }
        val bRefresh = Ui.button(ctx, getString(R.string.sp_refresh), outlined = true, top = 6, icon = R.drawable.ic_sync) { refresh() }
        lp.header.addView(Ui.buttonRow(ctx, bNew, bRefresh, top = 0))
        tvInfo = Ui.hint(ctx, "", 4).also { lp.header.addView(it) }
        lp.empty.text = getString(R.string.sp_empty)
        return lp.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        launchOnStarted { ServiceLocator.onec.state.collect { render() } }
    }

    private fun render() {
        if (!::lp.isInitialized) return
        val st = ServiceLocator.onec.state.value
        val s = st.suppliers
        shown = s?.list.orEmpty().filter { query.isEmpty() || it.name.lowercase().contains(query) || it.phone.contains(query) }
        lp.empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        lp.adapter.submit(shown.map { p ->
            Ui.Row(
                p.name,
                listOf(p.phone, getString(R.string.sp_docs, p.docs.size)).filter { it.isNotEmpty() }.joinToString(" · "),
                if (p.oweSom == 0.0 && p.oweUsd == 0.0) getString(R.string.sp_settled) else money2(p.oweSom, p.oweUsd),
                when {
                    p.oweSom > 0 || p.oweUsd > 0 -> getString(R.string.sp_we_owe)
                    p.oweSom < 0 || p.oweUsd < 0 -> getString(R.string.sp_they_owe)
                    else -> ""
                },
                icon = R.drawable.ic_truck,
            )
        })
        tvInfo.text = if (s == null) getString(if (st.requestedKind == "supp") R.string.oc_requested else R.string.oc_none)
        else {
            val owe = s.list.sumOf { it.oweSom.coerceAtLeast(0.0) } to s.list.sumOf { it.oweUsd.coerceAtLeast(0.0) }
            getString(R.string.sp_info, s.list.size, money2(owe.first, owe.second), formatDateTime(s.at)) +
                if (s.errors.isNotEmpty()) "\n" + getString(R.string.oc_errors, s.errors.joinToString("; ")) else ""
        }
    }

    private fun refresh() {
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        launchUi {
            val f = SimpleDateFormat("yyyyMMdd", Locale.US)
            val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -89) }.time
            ServiceLocator.onec.requestData("supp", f.format(from), f.format(java.util.Date()))
            snack(getString(R.string.oc_request_sent))
            repeat(12) {
                delay(15_000)
                runCatching { ServiceLocator.share.processInbox() }
                if ((ServiceLocator.onec.state.value.suppliers?.at ?: 0L) > ServiceLocator.onec.state.value.requestedAt) return@launchUi
            }
        }
    }
}

/** Bitta ta'minotchi: qoldiq, amallar (kirim, to'lov, qaytarish) va hujjatlar. */
class SupplierFragment : CodeFragment() {
    private lateinit var lp: Ui.ListPage
    private var docs: List<OneCDoc> = emptyList()
    private val name: String get() = arguments?.getString(ARG).orEmpty()

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.sp_title) { i -> docs.getOrNull(i)?.let { showDoc(it) } }
        lp.toolbar.title = name
        lp.empty.text = getString(R.string.sp_no_docs)
        return lp.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        launchOnStarted { ServiceLocator.onec.state.collect { render() } }
    }

    private fun render() {
        val p = ServiceLocator.onec.state.value.suppliers?.list?.firstOrNull { it.name == name }
        val h = lp.header
        h.removeAllViews()
        val code = p?.code.orEmpty()
        h.addView(Ui.title(ctx, if (p == null) getString(R.string.oc_none) else when {
            p.oweSom > 0 || p.oweUsd > 0 -> getString(R.string.sp_balance_we, money2(p.oweSom.coerceAtLeast(0.0), p.oweUsd.coerceAtLeast(0.0)))
            p.oweSom < 0 || p.oweUsd < 0 -> getString(R.string.sp_balance_they, money2((-p.oweSom).coerceAtLeast(0.0), (-p.oweUsd).coerceAtLeast(0.0)))
            else -> getString(R.string.sp_settled)
        }, 4))
        if (p != null && p.phone.isNotEmpty()) h.addView(Ui.hint(ctx, p.phone))
        val b1 = Ui.button(ctx, getString(R.string.sp_receipt), top = 6, icon = R.drawable.ic_inventory) { navigator().push(WriteFormFragment.of(WriteKind.RECEIPT, name, code)) }
        val b2 = Ui.button(ctx, getString(R.string.sp_pay), top = 6, icon = R.drawable.ic_payments) { navigator().push(WriteFormFragment.of(WriteKind.SUPPLIER_PAY, name, code)) }
        val b3 = Ui.button(ctx, getString(R.string.sp_return), outlined = true, top = 6, icon = R.drawable.ic_undo) { navigator().push(WriteFormFragment.of(WriteKind.SUPPLIER_RETURN, name, code)) }
        h.addView(Ui.buttonRow(ctx, b1, b2, top = 0))
        h.addView(b3)
        docs = p?.docs.orEmpty()
        lp.empty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
        lp.adapter.submit(docs.map { d ->
            Ui.Row(kindName(d.kind), getString(R.string.sp_doc_sub, d.number, Stamp.formatDateTime(d.stamp), d.lines.size), money2(d.som, d.usd), d.note.take(40), icon = docIcon(d.kind))
        })
    }

    companion object {
        private const val ARG = "name"
        fun of(name: String) = SupplierFragment().apply { arguments = Bundle().apply { putString(ARG, name) } }
    }
}
