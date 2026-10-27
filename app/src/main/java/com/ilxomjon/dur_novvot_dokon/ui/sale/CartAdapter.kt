package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.databinding.ItemCartLineBinding
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.ui.money

class CartAdapter(private val onEdit: (CartLine) -> Unit) : ListAdapter<CartLine, CartAdapter.VH>(Diff) {
    private var currency: Currency = Currency.SOM

    fun submit(lines: List<CartLine>, currency: Currency) {
        val changed = this.currency != currency
        this.currency = currency
        submitList(lines) { if (changed) notifyDataSetChanged() }
    }

    fun lineAt(position: Int): CartLine = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCartLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemCartLineBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(l: CartLine) {
            val ctx = b.root.context
            b.tvName.text = l.name
            b.tvDetail.text = ctx.getString(R.string.cart_line, Money.qtyDisplay(l.qty), ctx.money(l.price, currency))
            b.tvSum.text = ctx.money(l.total, currency)
            b.root.setOnClickListener { onEdit(l) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CartLine>() {
        override fun areItemsTheSame(a: CartLine, b: CartLine) = a.productId == b.productId
        override fun areContentsTheSame(a: CartLine, b: CartLine) = a == b
    }
}
