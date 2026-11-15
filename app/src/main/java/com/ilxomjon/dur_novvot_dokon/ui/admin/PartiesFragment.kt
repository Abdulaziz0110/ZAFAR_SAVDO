package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.core.Translit
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.Store
import com.ilxomjon.dur_novvot_dokon.ui.UiBus
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.PartyField
import uz.imsoft.shared.onec.Supplier
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.report.Stamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * v5.3: MIJOZLAR va YUK BERUVCHILAR — bitta ko'rinishdagi ro'yxat, hamma ish shu yerda.
 *
 * Har qatorda: nomi, telefoni, qarzi va o'ngda tez amal ikonkalari:
 *   mijoz —  to'lov qabul qilish · tovar qaytarish · ma'lumotini o'zgartirish
 *   yuk beruvchi — to'lov · tovar kirimi · qaytarish
 * Qatorning o'zi bosilsa — tarixi (hujjatlar) va qolgan amallar ochiladi.
 * Avval bu ishlar 8 ta alohida sozlamaga sochilgan edi (qarzdorlar, mijozdan to'lov, mijoz qaytargan tovar,
 * yuk beruvchilar, yuk beruvchiga to'lov, qaytarish, yangi kontragent, kontragentni tahrirlash).
 */
class PartiesFragment : CodeFragment() {
    private lateinit var lp: Ui.ListPage
    private lateinit var tvInfo: TextView
    private val suppliers: Boolean get() = arguments?.getString(ARG) == SUPPLIERS
    private var query = ""
    private var onlyDebt = false
    private var clients: List<Store> = emptyList()
    private var supp: List<Supplier> = emptyList()

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(if (suppliers) R.string.sp_title else R.string.hub_clients) { i -> open(i) }
        lp.adapter.onAction = { row, act -> action(row, act) }
        lp.header.addView(Ui.search(ctx, getString(R.string.pt_search)) { query = it.trim(); render() }.first)
        lp.header.addView(
            Ui.toggle(ctx, listOf(getString(R.string.pt_all), getString(if (suppliers) R.string.pt_we_owe else R.string.pt_debtors)), 0, 4) { i ->
                onlyDebt = i == 1
                render()
            },
        )
        val bRefresh = Ui.button(ctx, getString(R.string.sp_refresh), outlined = true, top = 6, icon = R.drawable.ic_sync) { refresh() }
        if (AppRole.isAdmin) {
            val bNew = Ui.button(ctx, getString(if (suppliers) R.string.pt_new_supplier else R.string.pt_new_client), top = 6, icon = R.drawable.ic_person_add) {
                navigator().push(PartyFormFragment.new(if (suppliers) PartyField.SUPPLIERS else PartyField.CLIENTS))
            }
            lp.header.addView(Ui.buttonRow(ctx, bNew, bRefresh, top = 0))
        } else lp.header.addView(bRefresh)
        tvInfo = Ui.hint(ctx, "", 4).also { lp.header.addView(it) }
        return lp.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        launchOnStarted { ServiceLocator.onec.state.collect { render() } }
        launchOnStarted { ServiceLocator.catalog.state.collect { render() } }
    }

    private fun render() {
        if (!::lp.isInitialized) return
        if (suppliers) renderSuppliers() else renderClients()
    }

    private fun renderClients() {
        val cat = ServiceLocator.catalog.state.value
        val rate = cat.rate.takeIf { it > 0 } ?: 1.0
        val phones = ServiceLocator.onec.state.value.suppliers
        val k = Translit.key(query)
        val digits = query.filter { it.isDigit() }
        val all = cat.stores
        clients = all.asSequence()
            .filter { !onlyDebt || it.debtSom > 0.0 || it.debtUsd > 0.0 }
            .filter {
                k.isEmpty() || it.searchKey.contains(k) || it.regionKey.contains(k) ||
                    (digits.length >= 3 && phones?.phoneOf(it.id, it.name).orEmpty().filter { c -> c.isDigit() }.contains(digits))
            }
            // qarzi kattasi tepada; qarzi yo'qlar nomi bo'yicha
            .sortedWith(compareByDescending<Store> { it.debtSom + it.debtUsd * rate }.thenBy { it.searchKey })
            .take(600).toList()
        val admin = AppRole.isAdmin
        val acts = if (admin) listOf(R.drawable.ic_wallet, R.drawable.ic_assignment_return, R.drawable.ic_edit) else listOf(R.drawable.ic_wallet)
        lp.adapter.submit(clients.map { s ->
            val phone = phones?.phoneOf(s.id, s.name).orEmpty()
            val debt = s.debtSom > 0.0 || s.debtUsd > 0.0
            Ui.Row(
                s.name, listOf(phone, s.regionName).filter { it.isNotBlank() }.joinToString(" · "),
                if (debt) money2(s.debtSom, s.debtUsd) else "", if (debt) getString(R.string.pt_debt) else "",
                icon = R.drawable.ic_person, tone = if (debt) 3 else 0, actions = acts,
            )
        })
        lp.empty.text = getString(if (all.isEmpty()) R.string.pt_no_catalog else R.string.pt_nothing)
        lp.empty.visibility = if (clients.isEmpty()) View.VISIBLE else View.GONE
        val debtors = all.filter { it.debtSom > 0.0 || it.debtUsd > 0.0 }
        tvInfo.text = getString(R.string.pt_info_clients, all.size, debtors.size, money2(debtors.sumOf { it.debtSom }, debtors.sumOf { it.debtUsd }),
            if (cat.lastSyncAt > 0) formatDateTime(cat.lastSyncAt) else "—")
    }

    private fun renderSuppliers() {
        val st = ServiceLocator.onec.state.value
        val s = st.suppliers
        val q = query.lowercase()
        supp = s?.list.orEmpty().filter { !onlyDebt || it.oweSom > 0 || it.oweUsd > 0 }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.contains(q) || it.code.contains(q) }
        val acts = listOf(R.drawable.ic_payments, R.drawable.ic_inventory, R.drawable.ic_undo)
        lp.adapter.submit(supp.map { p ->
            Ui.Row(
                p.name, listOf(p.phone, getString(R.string.sp_docs, p.docs.size)).filter { it.isNotEmpty() }.joinToString(" · "),
                if (p.oweSom == 0.0 && p.oweUsd == 0.0) "" else money2(kotlin.math.abs(p.oweSom), kotlin.math.abs(p.oweUsd)),
                when {
                    p.oweSom > 0 || p.oweUsd > 0 -> getString(R.string.sp_we_owe)
                    p.oweSom < 0 || p.oweUsd < 0 -> getString(R.string.sp_they_owe)
                    else -> ""
                },
                icon = R.drawable.ic_truck, tone = if (p.oweSom > 0 || p.oweUsd > 0) 3 else 0, actions = acts,
            )
        })
        lp.empty.text = getString(if (s == null) R.string.oc_none else R.string.pt_nothing)
        lp.empty.visibility = if (supp.isEmpty()) View.VISIBLE else View.GONE
        tvInfo.text = if (s == null) getString(if (st.requestedKind == "supp") R.string.oc_requested else R.string.oc_none)
        else {
            val owe = s.list.sumOf { it.oweSom.coerceAtLeast(0.0) } to s.list.sumOf { it.oweUsd.coerceAtLeast(0.0) }
            getString(R.string.sp_info, s.list.size, money2(owe.first, owe.second), formatDateTime(s.at)) +
                if (s.errors.isNotEmpty()) "\n" + getString(R.string.oc_errors, s.errors.joinToString("; ")) else ""
        }
    }

    private fun open(i: Int) {
        if (suppliers) supp.getOrNull(i)?.let { navigator().push(SupplierFragment.of(it.name)) }
        else clients.getOrNull(i)?.let { navigator().push(ClientFragment.of(it.id)) }
    }

    private fun action(row: Int, act: Int) {
        if (suppliers) {
            val p = supp.getOrNull(row) ?: return
            val kind = when (act) { 0 -> WriteKind.SUPPLIER_PAY; 1 -> WriteKind.RECEIPT; else -> WriteKind.SUPPLIER_RETURN }
            navigator().push(WriteFormFragment.of(kind, p.name, p.code))
        } else {
            val s = clients.getOrNull(row) ?: return
            when (act) {
                0 -> ClientActions.pay(this, s)
                1 -> navigator().push(WriteFormFragment.of(WriteKind.CLIENT_RETURN, s.name, s.id))
                else -> navigator().push(PartyFormFragment.edit(s.name, s.id, s.regionName, ServiceLocator.onec.state.value.suppliers?.phoneOf(s.id, s.name).orEmpty()))
            }
        }
    }

    private fun refresh() {
        launchUi {
            if (!suppliers) { ServiceLocator.catalog.sync(); snack(getString(R.string.sync_ok)) }
            if (!AppRole.isAdmin) return@launchUi
            if (!ServiceLocator.onec.ready) { if (suppliers) snack(getString(R.string.oc_no_cloud)); return@launchUi }
            // telefonlar va yuk beruvchilar 1C'dan (kompyuter orqali)
            val f = SimpleDateFormat("yyyyMMdd", Locale.US)
            val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -89) }.time
            ServiceLocator.onec.requestData("supp", f.format(from), f.format(java.util.Date()))
            if (suppliers) snack(getString(R.string.oc_request_sent))
            repeat(12) {
                delay(15_000)
                runCatching { ServiceLocator.share.processInbox() }
                if ((ServiceLocator.onec.state.value.suppliers?.at ?: 0L) > ServiceLocator.onec.state.value.requestedAt) return@launchUi
            }
        }
    }

    companion object {
        private const val ARG = "kind"
        const val CLIENTS = "c"
        const val SUPPLIERS = "s"
        fun of(kind: String) = PartiesFragment().apply { arguments = Bundle().apply { putString(ARG, kind) } }
    }
}

/** Mijoz bilan bog'liq umumiy amallar (ro'yxatdan ham, mijoz sahifasidan ham). */
object ClientActions {
    /** To'lov ekrani shu mijoz bilan ochiladi. */
    fun pay(f: CodeFragment, s: Store) {
        UiBus.paymentStore.value = s.id
        f.navigator().openTab(R.id.nav_payment)
    }
}

/**
 * Bitta mijoz: qarzi, telefoni, "Qarzni to'liq yopish", to'lov / qaytarish / savdo va tarixi.
 * "Qarzni to'liq yopish": bir marta tasdiq so'raladi, to'lov darhol navbatga qo'yiladi va ro'yxatga qaytiladi.
 */
class ClientFragment : CodeFragment() {
    private lateinit var lp: Ui.ListPage
    private val storeId: String get() = arguments?.getString(ARG).orEmpty()
    private var docs: List<OneCDoc> = emptyList()
    private var local: List<Pair<Long, Ui.Row>> = emptyList()
    private var fresh: com.ilxomjon.dur_novvot_dokon.data.local.DebtCache? = null
    private var closing = false

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.hub_clients) { i -> docs.getOrNull(i)?.let { showDoc(it) } }
        lp.empty.text = getString(R.string.pt_no_history)
        return lp.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        launchOnStarted { ServiceLocator.onec.state.collect { render() } }
        launchOnStarted { ServiceLocator.catalog.state.collect { render() } }
        // Qarzni serverdan yangilab olish (ko'rinmasa — oxirgi ma'lum qarz qoladi)
        launchUi {
            fresh = withTimeoutOrNull(25_000) { runCatching { ServiceLocator.catalog.refreshDebt(storeId) }.getOrNull() }
            render()
        }
        launchUi { loadLocal(); render() }
    }

    private suspend fun loadLocal() {
        val h = ServiceLocator.historyDb
        val rows = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val out = ArrayList<Pair<Long, Ui.Row>>()
            for (o in h.ordersBetween(0L, Long.MAX_VALUE, storeId)) {
                out += o.sentAt to Ui.Row(
                    getString(if (o.mode == DealMode.RETURN) R.string.pt_h_return else R.string.pt_h_sale),
                    formatDateTime(o.sentAt) + " · " + getString(R.string.pt_h_lines, o.lines.size),
                    som(o.total - o.discount), icon = if (o.mode == DealMode.RETURN) R.drawable.ic_assignment_return else R.drawable.ic_receipt,
                )
            }
            for (p in h.paymentsBetween(0L, Long.MAX_VALUE, storeId)) {
                out += p.sentAt to Ui.Row(getString(R.string.pt_h_pay), formatDateTime(p.sentAt) + (if (p.note.isNotBlank()) " · " + p.note else ""), som(p.total), icon = R.drawable.ic_wallet, tone = 1)
            }
            out.sortedByDescending { it.first }
        }
        local = rows
    }

    private fun store(): Store? = ServiceLocator.catalog.storeById(storeId)

    private fun debt(): Pair<Double, Double> {
        val f = fresh
        val s = store()
        return if (f != null) f.som to f.usd else (s?.debtSom ?: 0.0) to (s?.debtUsd ?: 0.0)
    }

    private fun render() {
        if (!::lp.isInitialized) return
        val s = store()
        val h = lp.header
        h.removeAllViews()
        if (s == null) { lp.toolbar.title = getString(R.string.hub_clients); lp.empty.visibility = View.VISIBLE; return }
        lp.toolbar.title = s.name
        val (dSom, dUsd) = debt()
        val has = dSom > 0.0 || dUsd > 0.0
        h.addView(Ui.title(ctx, if (has) getString(R.string.pt_debt_is, money2(dSom, dUsd)) else getString(R.string.pt_no_debt), 4))
        val phone = ServiceLocator.onec.state.value.suppliers?.phoneOf(s.id, s.name).orEmpty()
        if (phone.isNotEmpty()) {
            h.addView(Ui.item(ctx, phone, getString(R.string.pt_call), icon = R.drawable.ic_person, tone = 1) {
                runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
            })
        } else if (s.regionName.isNotBlank()) h.addView(Ui.hint(ctx, s.regionName))
        if (dSom > 0.0) {
            h.addView(Ui.button(ctx, getString(R.string.pt_close_debt, som(dSom)), top = 8, icon = R.drawable.ic_check) { confirmClose(s, dSom, dUsd) }.apply { isEnabled = !closing })
        }
        val bPay = Ui.button(ctx, getString(R.string.pt_pay), outlined = true, top = 6, icon = R.drawable.ic_wallet) { ClientActions.pay(this, s) }
        val bSale = Ui.button(ctx, getString(R.string.pt_sale), outlined = true, top = 6, icon = R.drawable.ic_cart) { startSale(s) }
        h.addView(Ui.buttonRow(ctx, bPay, bSale, top = 0))
        if (AppRole.isAdmin) {
            val bRet = Ui.button(ctx, getString(R.string.pt_return), outlined = true, top = 6, icon = R.drawable.ic_assignment_return) {
                navigator().push(WriteFormFragment.of(WriteKind.CLIENT_RETURN, s.name, s.id))
            }
            val bEdit = Ui.button(ctx, getString(R.string.pt_edit), outlined = true, top = 6, icon = R.drawable.ic_edit) {
                navigator().push(PartyFormFragment.edit(s.name, s.id, s.regionName, phone))
            }
            h.addView(Ui.buttonRow(ctx, bRet, bEdit, top = 0))
        }
        h.addView(Ui.section(ctx, getString(R.string.pt_history)))
        // Admin: 1C hujjatlari (bosilsa ochiladi); bo'lmasa — shu telefondan qilingan savdo va to'lovlar
        val name = s.name.trim()
        docs = if (AppRole.isAdmin) ServiceLocator.onec.state.value.sales?.docs.orEmpty().filter { it.partyCode == s.id || it.party.trim() == name }.sortedByDescending { it.stamp }.take(200) else emptyList()
        if (docs.isNotEmpty()) {
            lp.adapter.submit(docs.map { d ->
                Ui.Row(kindName(d.kind), getString(R.string.sp_doc_sub, d.number, Stamp.formatDateTime(d.stamp), d.lines.size), money2(d.som, d.usd), d.note.take(40), icon = docIcon(d.kind))
            })
        } else lp.adapter.submit(local.map { it.second })
        lp.empty.visibility = if (docs.isEmpty() && local.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun startSale(s: Store) {
        launchUi {
            runCatching { if (ServiceLocator.draft.state.value.lines.isNotEmpty()) ServiceLocator.parked.parkActive() }
            ServiceLocator.draft.setStore(s)
            navigator().openTab(R.id.nav_sale)
        }
    }

    private fun confirmClose(s: Store, dSom: Double, dUsd: Double) {
        val msg = getString(R.string.pt_close_q, s.name, som(dSom)) + if (dUsd > 0.0) "\n\n" + getString(R.string.pt_close_usd, usd(dUsd)) else ""
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.pt_close_title).setMessage(msg)
            .setPositiveButton(R.string.pt_close_yes) { _, _ -> close(s, dSom) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun close(s: Store, amount: Double) {
        if (closing) return
        closing = true
        render()
        launchUi {
            try {
                ServiceLocator.orders.sendPayment(s.id, s.name, amount, 0.0, 0.0, getString(R.string.pt_close_note), ServiceLocator.appPrefs.printer)
                // Ro'yxatda qarz darhol nol ko'rinsin (server tasdiqlagach haqiqiy qiymat keladi)
                runCatching { ServiceLocator.catalog.noteDebtPaid(s.id, amount) }
                android.widget.Toast.makeText(ctx, getString(R.string.pt_closed, s.name), android.widget.Toast.LENGTH_LONG).show()
                navigator().pop()
            } finally {
                closing = false
            }
        }
    }

    companion object {
        private const val ARG = "store"
        fun of(storeId: String) = ClientFragment().apply { arguments = Bundle().apply { putString(ARG, storeId) } }
    }
}

/** Bitta yuk beruvchi: qoldiq, amallar (kirim, to'lov, qaytarish) va hujjatlar. */
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
        if (p != null && p.phone.isNotEmpty()) {
            h.addView(Ui.item(ctx, p.phone, getString(R.string.pt_call), icon = R.drawable.ic_person, tone = 1) {
                runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.phone.filter { it.isDigit() || it == '+' }))) }
            })
        }
        val b1 = Ui.button(ctx, getString(R.string.sp_receipt), top = 6, icon = R.drawable.ic_inventory) { navigator().push(WriteFormFragment.of(WriteKind.RECEIPT, name, code)) }
        val b2 = Ui.button(ctx, getString(R.string.sp_pay), top = 6, icon = R.drawable.ic_payments) { navigator().push(WriteFormFragment.of(WriteKind.SUPPLIER_PAY, name, code)) }
        val b3 = Ui.button(ctx, getString(R.string.sp_return), outlined = true, top = 6, icon = R.drawable.ic_undo) { navigator().push(WriteFormFragment.of(WriteKind.SUPPLIER_RETURN, name, code)) }
        val b4 = Ui.button(ctx, getString(R.string.pt_edit), outlined = true, top = 6, icon = R.drawable.ic_edit) { navigator().push(PartyFormFragment.edit(name, code, "", p?.phone.orEmpty())) }
        h.addView(Ui.buttonRow(ctx, b1, b2, top = 0))
        h.addView(Ui.buttonRow(ctx, b3, b4, top = 0))
        h.addView(Ui.section(ctx, getString(R.string.pt_history)))
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
