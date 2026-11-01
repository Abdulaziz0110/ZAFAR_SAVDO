package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.content.Context
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.core.Translit
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.money
import uz.imsoft.shared.android.Ui

/** v3.2: tovar tanlash oynasi (qidiruv bilan) va miqdor/narx so'rash. */
object Pickers {
    fun product(ctx: Context, onPick: (Product) -> Unit) {
        val all = ServiceLocator.catalog.state.value.products
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 4), Ui.dp(ctx, 16), 0)
        }
        var shown: List<Product> = emptyList()
        var dialog: AlertDialog? = null
        val adapter = Ui.RowAdapter { i -> shown.getOrNull(i)?.let { p -> dialog?.dismiss(); onPick(p) } }
        fun filter(q: String) {
            val k = Translit.key(q.trim())
            shown = (if (k.isEmpty()) all else all.filter { it.searchKey.contains(k) || it.idLower == k }).take(150)
            adapter.submit(shown.map { p ->
                Ui.Row(p.name, ctx.getString(R.string.ad_product_code, p.id), if (p.baseIsSom) ctx.money(p.wholesaleSom, Currency.SOM) else ctx.money(p.wholesaleUsd, Currency.USD), icon = R.drawable.ic_shopping_bag)
            })
        }
        col.addView(Ui.search(ctx, ctx.getString(R.string.ad_product_search)) { filter(it) }.first)
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(ctx)
            this.adapter = adapter
        }
        col.addView(rv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 360)))
        filter("")
        dialog = MaterialAlertDialogBuilder(ctx).setTitle(R.string.ad_product_pick).setView(col).setNegativeButton(R.string.cancel, null).show()
    }

    /** Miqdor va narx (so'm yoki $ — tovar valyutasida). [onOk](miqdor, narx so'm, narx $). */
    fun qtyPrice(ctx: Context, title: String, qty: Double, priceSom: Double, priceUsd: Double, usdMain: Boolean, onOk: (Double, Double, Double) -> Unit) {
        val rate = ServiceLocator.catalog.state.value.rate
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0)
        }
        val (tq, eq) = Ui.field(ctx, ctx.getString(R.string.ad_qty), Ui.NUMBER, if (qty > 0) Money.plain(qty) else "")
        val (tp, ep) = Ui.field(ctx, ctx.getString(if (usdMain) R.string.ad_price_usd else R.string.ad_price_som), Ui.NUMBER,
            (if (usdMain) priceUsd else priceSom).takeIf { it > 0 }?.let { Money.plain(it) }.orEmpty())
        col.addView(tq)
        col.addView(tp)
        MaterialAlertDialogBuilder(ctx).setTitle(title).setView(col)
            .setPositiveButton(R.string.ok) { _, _ ->
                val q = Money.parseUser(eq.text?.toString()) ?: 0.0
                val p = Money.parseUser(ep.text?.toString()) ?: 0.0
                if (q <= 0.0 || p < 0.0) return@setPositiveButton
                if (usdMain) onOk(q, if (rate > 0) Money.round2(p * rate) else 0.0, p)
                else onOk(q, p, if (rate > 0) Math.round(p / rate * 10_000.0) / 10_000.0 else 0.0)
            }
            .setNegativeButton(R.string.cancel, null).show()
        eq.requestFocus()
    }
}
