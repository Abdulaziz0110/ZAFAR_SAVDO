package uz.imsoft.mijoz.ui.sale

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.extra.MFragment
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.pos.Pos
import uz.imsoft.shared.pos.PosLine

/** v3.9 Mijoz: savatcha — tovarlar, mijoz, chegirma, to'langan summa; "Saqlash va chek". Faqat shu telefonda saqlanadi. */
class MCartFragment : MFragment() {
    private lateinit var body: LinearLayout
    private var discountText = ""
    private var paidText = ""
    private var noteText = ""

    override fun build(inflater: LayoutInflater): View {
        val pg = page(R.string.ms_cart)
        body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        pg.content.addView(body)
        return pg.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        launchOnStarted { Locator.sale.state.collect { render() } }
        launchOnStarted { Locator.pos.state.collect { render() } }
    }

    private fun num(s: String): Double = uz.imsoft.shared.NumText.parse(s)
    private fun plain(v: Double): String = Fmt.qty(v).filter { !it.isWhitespace() }

    private fun total(): Double {
        val t = Locator.sale.state.value.total - num(discountText).coerceAtLeast(0.0)
        return Math.round(t * 100.0) / 100.0
    }

    private fun render() {
        if (!::body.isInitialized) return
        body.removeAllViews()
        val s = Locator.sale.state.value
        val d = Locator.pos.state.value
        // v4.0: sodda — mijoz tanlash va nasiya yo'q (naqd savdo)
        if (d.customers.isEmpty() && s.customerId != 0L) Locator.sale.setCustomer(0L)
        body.addView(Ui.section(ctx, getString(R.string.pos_goods)))
        if (s.lines.isEmpty()) body.addView(Ui.hint(ctx, getString(R.string.pos_no_goods)))
        s.lines.forEachIndexed { i, l ->
            body.addView(Ui.item(ctx, "${i + 1}) ${l.name}", "${fmtQty(l.qty)} × ${som(l.price)}", som(l.sum)) { editLine(i, l) })
            body.addView(Ui.divider(ctx))
        }
        val (tDisc, eDisc) = Ui.field(ctx, getString(R.string.pos_discount), Ui.NUMBER, discountText)
        val (tNote, eNote) = Ui.field(ctx, getString(R.string.pos_note), value = noteText)
        body.addView(tDisc); body.addView(tNote)
        val tvTotal: TextView = Ui.title(ctx, getString(R.string.pos_total_line, som(total())), 8)
        body.addView(tvTotal)
        fun watch(e: TextInputEditText, set: (String) -> Unit) = e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(x: CharSequence?, a: Int, b: Int, n: Int) = Unit
            override fun onTextChanged(x: CharSequence?, a: Int, b: Int, n: Int) = Unit
            override fun afterTextChanged(x: android.text.Editable?) { set(x?.toString().orEmpty()); tvTotal.text = getString(R.string.pos_total_line, som(total())) }
        })
        watch(eDisc) { discountText = it }
        watch(eNote) { noteText = it }
        val bSave = Ui.button(ctx, getString(R.string.pos_save_print), icon = R.drawable.ic_print) { save(print = true) }
        val bOnly = Ui.button(ctx, getString(R.string.pos_save), outlined = true, icon = R.drawable.ic_check) { save(print = false) }
        body.addView(Ui.buttonRow(ctx, bSave, bOnly))
        body.addView(Ui.hint(ctx, getString(R.string.pos_local_hint), 8))
    }

    private fun pickCustomer() {
        val d = Locator.pos.state.value
        val list = d.customers
        val names = (listOf(getString(R.string.pos_cash_client)) + list.map { it.name + "  ·  " + som(d.balance(it.id)) }).toTypedArray()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.pos_client).setItems(names) { _, i ->
            Locator.sale.setCustomer(if (i == 0) 0L else list[i - 1].id)
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun editLine(i: Int, l: PosLine) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
        val (tq, eq) = Ui.field(ctx, getString(R.string.ed_qty), Ui.NUMBER, plain(l.qty))
        val (tp, ep) = Ui.field(ctx, getString(R.string.ed_price), Ui.NUMBER, plain(l.price))
        col.addView(tq); col.addView(tp)
        MaterialAlertDialogBuilder(ctx).setTitle(l.name).setView(col)
            .setPositiveButton(R.string.ok) { _, _ ->
                val q = num(eq.text?.toString().orEmpty())
                val p = num(ep.text?.toString().orEmpty())
                if (p >= 0) Locator.sale.setLine(i, l.copy(qty = q, price = p))
            }
            .setNeutralButton(R.string.pos_remove) { _, _ -> Locator.sale.setLine(i, null) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun save(print: Boolean) {
        val s = Locator.sale.state.value
        if (s.lines.isEmpty()) { snack(getString(R.string.pos_no_goods)); return }
        val t = total()
        if (t < 0) { snack(getString(R.string.pos_err_discount)); return }
        // v4.0: naqd savdo — to'liq to'langan
        val paid = t
        val disc = num(discountText).coerceAtLeast(0.0)
        launchUi {
            val id = Locator.pos.saveSale(Pos.stampOf(System.currentTimeMillis()), 0L, s.lines, disc, paid, noteText)
            Locator.sale.clear()
            discountText = ""; paidText = ""; noteText = ""
            snack(getString(R.string.pos_saved))
            if (print) {
                val d = Locator.pos.state.value
                d.sales.firstOrNull { it.id == id }?.let { sale ->
                    val c = d.customer(sale.customerId)
                    val shop = Locator.data.state.value.snapshot?.shopName.orEmpty().ifBlank { getString(R.string.app_name) }
                    printReceipt(Pos.receipt(shop, sale, c, c?.let { d.balance(it.id) }, posLabels()))
                }
            }
            back()
        }
    }
}
