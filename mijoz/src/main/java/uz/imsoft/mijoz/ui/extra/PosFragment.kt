package uz.imsoft.mijoz.ui.extra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.data.PosData
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.shared.SearchIndex
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.pos.Pos
import uz.imsoft.shared.pos.PosCustomer
import uz.imsoft.shared.pos.PosLine
import uz.imsoft.shared.pos.PosSale
import uz.imsoft.shared.report.PriceItem
import uz.imsoft.shared.report.Stamp

/**
 * v3.2 Mijoz "Kassa": 2-filialning o'z mijozlariga savdosi — faqat shu telefonda (1-ilovaga va serverga bormaydi).
 * Savdo, mijozlar (qarz, to'lov), tarix, hisobot. Chek — USB printerga yoki rasm qilib.
 */
class PosFragment : MFragment() {
    companion object {
        private const val ARG_TAB = "tab"
        const val TAB_CLIENTS = 1
        const val TAB_HISTORY = 2
        const val TAB_REPORT = 3

        /** v3.9: bosh sahifa tugmalaridan kerakli bo'limni ochish. */
        fun open(tab: Int) = PosFragment().apply { arguments = android.os.Bundle().apply { putInt(ARG_TAB, tab) } }
    }

    private lateinit var body: LinearLayout
    private var tabs: com.google.android.material.button.MaterialButtonToggleGroup? = null
    private var tab = 0
    private var customerId = 0L
    private val lines = ArrayList<PosLine>()
    private var discount = 0.0
    private var paidText = ""
    private var reportDays = 1

    private val data: PosData get() = Locator.pos.state.value

    override fun build(inflater: LayoutInflater): View {
        arguments?.getInt(ARG_TAB, -1)?.takeIf { it in 0..3 }?.let { tab = it; arguments?.remove(ARG_TAB) }
        val p = page(R.string.pos_title)
        tabs = Ui.toggle(ctx, listOf(getString(R.string.pos_tab_sale), getString(R.string.pos_tab_clients), getString(R.string.pos_tab_history), getString(R.string.pos_tab_report)), tab, 4) { i ->
            tab = i
            render()
        }.also { p.content.addView(it) }
        body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        p.content.addView(body)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { Locator.pos.load() }
        launchOnStarted { Locator.pos.state.collect { render() } }
    }

    private fun render() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        when (tab) {
            0 -> renderSale()
            1 -> renderClients()
            2 -> renderHistory()
            else -> renderReport()
        }
    }

    private fun now(): Long = Pos.stampOf(System.currentTimeMillis())

    // ---------------- savdo ----------------

    private fun total(): Double = Math.round((lines.sumOf { it.sum } - discount) * 100.0) / 100.0

    private fun renderSale() {
        val c = data.customer(customerId)
        body.addView(Ui.section(ctx, getString(R.string.pos_client)))
        body.addView(Ui.item(ctx, c?.name ?: getString(R.string.pos_cash_client),
            if (c != null) getString(R.string.pos_balance, som(data.balance(c.id))) else getString(R.string.pos_cash_hint)) { pickCustomer() })
        body.addView(Ui.section(ctx, getString(R.string.pos_goods)))
        if (lines.isEmpty()) body.addView(Ui.hint(ctx, getString(R.string.pos_no_goods)))
        lines.forEachIndexed { i, l ->
            body.addView(Ui.item(ctx, "${i + 1}) ${l.name}", "${fmtQty(l.qty)} × ${som(l.price)}", som(l.sum)) { editLine(i) })
        }
        val bAdd = Ui.button(ctx, getString(R.string.pos_add)) { pickProduct() }
        val bFree = Ui.button(ctx, getString(R.string.pos_add_free), outlined = true) { freeLine() }
        body.addView(Ui.buttonRow(ctx, bAdd, bFree))
        val (tDisc, eDisc) = Ui.field(ctx, getString(R.string.pos_discount), Ui.NUMBER, if (discount > 0) plain(discount) else "")
        val (tPaid, ePaid) = Ui.field(ctx, getString(R.string.pos_paid_field), Ui.NUMBER, paidText)
        val (tNote, eNote) = Ui.field(ctx, getString(R.string.pos_note))
        body.addView(tDisc)
        body.addView(tPaid)
        body.addView(tNote)
        val tvTotal = Ui.title(ctx, getString(R.string.pos_total_line, som(total())), 8)
        body.addView(tvTotal)
        val watcher = { _: String ->
            discount = num(eDisc).coerceAtLeast(0.0)
            paidText = ePaid.text?.toString().orEmpty()
            tvTotal.text = getString(R.string.pos_total_line, som(total()))
        }
        listOf(eDisc, ePaid).forEach { e ->
            e.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, cnt: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, cnt: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) { watcher(s?.toString().orEmpty()) }
            })
        }
        val bSave = Ui.button(ctx, getString(R.string.pos_save_print)) { save(eNote, print = true) }
        val bOnly = Ui.button(ctx, getString(R.string.pos_save), outlined = true) { save(eNote, print = false) }
        body.addView(Ui.buttonRow(ctx, bSave, bOnly))
        body.addView(Ui.hint(ctx, getString(R.string.pos_local_hint), 8))
    }

    private fun num(e: TextInputEditText): Double = e.text?.toString().orEmpty().replace(" ", "").replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    private fun save(eNote: TextInputEditText, print: Boolean) {
        if (lines.isEmpty()) { snack(getString(R.string.pos_no_goods)); return }
        val t = total()
        if (t < 0) { snack(getString(R.string.pos_err_discount)); return }
        val paid = paidText.replace(" ", "").replace(',', '.').toDoubleOrNull() ?: if (customerId == 0L) t else 0.0
        if (customerId == 0L && paid + 0.009 < t) { snack(getString(R.string.pos_err_cash)); return }
        val stamp = now()
        val ls = lines.toList()
        val disc = discount
        val note = eNote.text?.toString().orEmpty()
        val cid = customerId
        launchUi {
            val id = Locator.pos.saveSale(stamp, cid, ls, disc, paid.coerceAtMost(t), note)
            lines.clear(); discount = 0.0; paidText = ""
            snack(getString(R.string.pos_saved))
            render()
            if (print) printSale(id)
        }
    }

    private suspend fun printSale(id: Long) {
        val d = Locator.pos.state.value
        val s = d.sales.firstOrNull { it.id == id } ?: return
        val c = d.customer(s.customerId)
        val shop = Locator.data.state.value.snapshot?.shopName.orEmpty().ifBlank { getString(R.string.app_name) }
        printReceipt(Pos.receipt(shop, s, c, c?.let { d.balance(it.id) }, posLabels()))
    }

    private fun pickCustomer() {
        val list = data.customers
        val names = (listOf(getString(R.string.pos_cash_client), getString(R.string.pos_new_client)) + list.map { it.name + "  ·  " + som(data.balance(it.id)) }).toTypedArray()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.pos_client).setItems(names) { _, i ->
            when (i) {
                0 -> { customerId = 0; render() }
                1 -> editCustomer(null) { id -> customerId = id; render() }
                else -> { customerId = list[i - 2].id; render() }
            }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun editCustomer(c: PosCustomer?, done: (Long) -> Unit = {}) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
        val (tn, en) = Ui.field(ctx, getString(R.string.pos_client_name), value = c?.name.orEmpty())
        val (tp, ep) = Ui.field(ctx, getString(R.string.pos_client_phone), Ui.PHONE, c?.phone.orEmpty())
        col.addView(tn); col.addView(tp)
        MaterialAlertDialogBuilder(ctx).setTitle(if (c == null) R.string.pos_new_client else R.string.pos_edit_client).setView(col)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = en.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                launchUi {
                    if (c == null) done(Locator.pos.addCustomer(name, ep.text?.toString().orEmpty()))
                    else { Locator.pos.updateCustomer(c.id, name, ep.text?.toString().orEmpty()); done(c.id) }
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun products(): List<PriceItem> = Locator.data.state.value.snapshot?.products.orEmpty().filter { it.id.isNotEmpty() }

    private fun pickProduct() {
        val all = products()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 4), Ui.dp(ctx, 16), 0) }
        var shown: List<PriceItem> = emptyList()
        var dialog: AlertDialog? = null
        val adapter = Ui.RowAdapter { i -> shown.getOrNull(i)?.let { p -> dialog?.dismiss(); askQty(p.name, p.id, defaultPrice(p)) } }
        val index = SearchIndex(all, { it.name }, { it.id }, { it.group + " " + it.plu })
        fun filter(q: String) {
            shown = (if (q.isBlank()) all else index.search(q, uz.imsoft.shared.SearchOptions(fuzzy = Locator.prefs.fuzzy, layoutFix = true), limit = 150).map { it.item }).take(150)
            adapter.submit(shown.map { Ui.Row(it.name, listOf(it.id, it.group).filter { s -> s.isNotEmpty() }.joinToString(" · "), som(defaultPrice(it))) })
        }
        col.addView(Ui.search(ctx, getString(R.string.pos_search)) { filter(it) }.first)
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply { layoutManager = androidx.recyclerview.widget.LinearLayoutManager(ctx); this.adapter = adapter }
        col.addView(rv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 360)))
        filter("")
        dialog = MaterialAlertDialogBuilder(ctx).setTitle(R.string.pos_add).setView(col).setNegativeButton(R.string.cancel, null).show()
    }

    /** O'z narxi (oxirgi sotilgan) yoki tanlangan narx turidagi so'm narxi. */
    private fun defaultPrice(p: PriceItem): Double = data.prices[p.name] ?: p.price(Locator.prefs.priceLevel, false)

    private fun askQty(name: String, code: String, price: Double, index: Int = -1, qty: Double = 1.0) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
        val (tq, eq) = Ui.field(ctx, getString(R.string.ed_qty), Ui.NUMBER, plain(qty))
        val (tp, ep) = Ui.field(ctx, getString(R.string.ed_price), Ui.NUMBER, if (price > 0) plain(price) else "")
        col.addView(tq); col.addView(tp)
        MaterialAlertDialogBuilder(ctx).setTitle(name).setView(col)
            .setPositiveButton(R.string.ok) { _, _ ->
                val q = num(eq)
                val pr = num(ep)
                if (q <= 0 || pr < 0) return@setPositiveButton
                val l = PosLine(name, code, q, pr)
                if (index in lines.indices) lines[index] = l else lines += l
                render()
            }.setNegativeButton(R.string.cancel, null).show()
        eq.requestFocus()
        eq.selectAll()
    }

    private fun freeLine() {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
        val (tn, en) = Ui.field(ctx, getString(R.string.pos_free_name))
        col.addView(tn)
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.pos_add_free).setView(col)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = en.text?.toString()?.trim().orEmpty()
                if (n.isNotEmpty()) askQty(n, "", data.prices[n] ?: 0.0)
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun editLine(i: Int) {
        val l = lines.getOrNull(i) ?: return
        MaterialAlertDialogBuilder(ctx).setTitle(l.name).setItems(arrayOf(getString(R.string.pos_change), getString(R.string.pos_remove))) { _, w ->
            if (w == 0) askQty(l.name, l.code, l.price, i, l.qty) else { lines.removeAt(i); render() }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    // ---------------- mijozlar ----------------

    private fun renderClients() {
        body.addView(Ui.button(ctx, getString(R.string.pos_new_client)) { editCustomer(null) })
        val list = data.customers.sortedByDescending { data.balance(it.id) }
        if (list.isEmpty()) body.addView(Ui.hint(ctx, getString(R.string.pos_no_clients)))
        val owed = list.sumOf { data.balance(it.id).coerceAtLeast(0.0) }
        if (list.isNotEmpty()) body.addView(Ui.hint(ctx, getString(R.string.pos_clients_info, list.size, som(owed))))
        for (c in list) {
            val bal = data.balance(c.id)
            body.addView(Ui.item(ctx, c.name, c.phone, if (bal != 0.0) som(bal) else "") { clientMenu(c) })
            body.addView(Ui.divider(ctx))
        }
    }

    private fun clientMenu(c: PosCustomer) {
        val items = arrayOf(getString(R.string.pos_take_payment), getString(R.string.pos_new_sale_for), getString(R.string.pos_client_history), getString(R.string.pos_edit_client))
        MaterialAlertDialogBuilder(ctx).setTitle(c.name + " · " + som(data.balance(c.id))).setItems(items) { _, w ->
            when (w) {
                0 -> takePayment(c)
                1 -> { customerId = c.id; tab = 0; tabs?.let { g -> g.check(g.getChildAt(0).id) }; render() }
                2 -> clientHistory(c)
                else -> editCustomer(c)
            }
        }.setNegativeButton(R.string.close, null).show()
    }

    private fun takePayment(c: PosCustomer) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
        val (ta, ea) = Ui.field(ctx, getString(R.string.rq_sum), Ui.NUMBER, data.balance(c.id).takeIf { it > 0 }?.let { plain(it) }.orEmpty())
        val (tn, en) = Ui.field(ctx, getString(R.string.pos_note))
        col.addView(ta); col.addView(tn)
        MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.pos_take_payment) + ": " + c.name).setView(col)
            .setPositiveButton(R.string.pos_save_print) { _, _ ->
                val a = num(ea)
                if (a <= 0) return@setPositiveButton
                val stamp = now()
                val note = en.text?.toString().orEmpty()
                launchUi {
                    val id = Locator.pos.addPayment(stamp, c.id, a, note)
                    snack(getString(R.string.pos_saved))
                    val shop = Locator.data.state.value.snapshot?.shopName.orEmpty().ifBlank { getString(R.string.app_name) }
                    printReceipt(Pos.paymentReceipt(shop, getString(R.string.pos_pay_title), c.name, stamp, a, note, "K-$id", posLabels(), Locator.pos.state.value.balance(c.id)))
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun clientHistory(c: PosCustomer) {
        val sb = StringBuilder()
        val ev = (data.sales.filter { it.customerId == c.id }.map { it.stamp to getString(R.string.pos_h_sale, som(it.total), som(it.paid)) } +
            data.payments.filter { it.customerId == c.id }.map { it.stamp to getString(R.string.pos_h_pay, som(it.amount)) }).sortedByDescending { it.first }.take(100)
        for ((st, t) in ev) sb.append(Stamp.formatDateTime(st)).append("  ").append(t).append('\n')
        if (ev.isEmpty()) sb.append(getString(R.string.pos_empty))
        MaterialAlertDialogBuilder(ctx).setTitle(c.name + " · " + som(data.balance(c.id))).setMessage(sb.toString().trim()).setPositiveButton(R.string.close, null).show()
    }

    // ---------------- tarix ----------------

    private fun renderHistory() {
        val d = data
        val ev = (d.sales.map { s -> Triple(s.stamp, 0, s.id) } + d.payments.map { p -> Triple(p.stamp, 1, p.id) }).sortedByDescending { it.first }.take(300)
        if (ev.isEmpty()) body.addView(Ui.hint(ctx, getString(R.string.pos_empty)))
        for ((st, kind, id) in ev) {
            if (kind == 0) {
                val s = d.sales.firstOrNull { it.id == id } ?: continue
                val who = d.customer(s.customerId)?.name ?: getString(R.string.pos_cash_client)
                body.addView(Ui.item(ctx, getString(R.string.pos_h_sale_row, s.number, who), Stamp.formatDateTime(st) + if (s.debt > 0) " · " + getString(R.string.pos_debt) + " " + som(s.debt) else "", som(s.total)) { saleMenu(s) })
            } else {
                val p = d.payments.firstOrNull { it.id == id } ?: continue
                body.addView(Ui.item(ctx, getString(R.string.pos_h_pay_row, d.customer(p.customerId)?.name.orEmpty()), Stamp.formatDateTime(st), som(p.amount)) {
                    MaterialAlertDialogBuilder(ctx).setTitle(R.string.pos_pay_title).setMessage(som(p.amount) + "\n" + p.note)
                        .setPositiveButton(R.string.pos_delete) { _, _ -> confirmDelete { launchUi { Locator.pos.deletePayment(p.id) } } }
                        .setNegativeButton(R.string.close, null).show()
                })
            }
            body.addView(Ui.divider(ctx))
        }
    }

    private fun saleMenu(s: PosSale) {
        val text = s.lines.joinToString("\n") { "${it.name} — ${fmtQty(it.qty)} × ${som(it.price)} = ${som(it.sum)}" } +
            "\n\n" + getString(R.string.pos_total_line, som(s.total)) + "\n" + getString(R.string.pos_paid) + ": " + som(s.paid)
        MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.pos_h_sale_row, s.number, data.customer(s.customerId)?.name ?: getString(R.string.pos_cash_client)))
            .setMessage(text)
            .setPositiveButton(R.string.pos_print) { _, _ -> launchUi { printSale(s.id) } }
            .setNeutralButton(R.string.pos_delete) { _, _ -> confirmDelete { launchUi { Locator.pos.deleteSale(s.id) } } }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun confirmDelete(ok: () -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.pos_delete).setMessage(R.string.pos_delete_text)
            .setPositiveButton(R.string.pos_delete) { _, _ -> ok() }.setNegativeButton(R.string.cancel, null).show()
    }

    // ---------------- hisobot ----------------

    private fun renderReport() {
        val days = listOf(1, 7, 30)
        body.addView(Ui.toggle(ctx, listOf(getString(R.string.pos_today), getString(R.string.pos_days, 7), getString(R.string.pos_days, 30)), days.indexOf(reportDays).coerceAtLeast(0), 8) { i ->
            reportDays = days[i]
            render()
        })
        val (from, to) = Pos.range(now(), reportDays)
        val r = Pos.report(from, to, data.sales, data.payments)
        val (card, col) = Ui.card(ctx)
        col.addView(Ui.item(ctx, getString(R.string.pos_r_sales), getString(R.string.pos_r_count, r.sales), som(r.total)))
        col.addView(Ui.item(ctx, getString(R.string.pos_r_paid), "", som(r.paidAtSale)))
        col.addView(Ui.item(ctx, getString(R.string.pos_r_credit), "", som(r.onCredit)))
        col.addView(Ui.item(ctx, getString(R.string.pos_r_payments), "", som(r.payments)))
        col.addView(Ui.divider(ctx))
        col.addView(Ui.item(ctx, getString(R.string.pos_r_cash), "", som(r.cashIn)))
        body.addView(card)
        if (r.top.isNotEmpty()) {
            body.addView(Ui.section(ctx, getString(R.string.pos_r_top)))
            r.top.forEach { (n, v) -> body.addView(Ui.item(ctx, n, "", som(v))) }
        }
        val debtors = data.customers.map { it to data.balance(it.id) }.filter { it.second > 0 }.sortedByDescending { it.second }
        if (debtors.isNotEmpty()) {
            body.addView(Ui.section(ctx, getString(R.string.pos_r_debtors)))
            debtors.take(30).forEach { (c, b) -> body.addView(Ui.item(ctx, c.name, c.phone, som(b)) { clientMenu(c) }) }
        }
    }
}
