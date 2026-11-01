package com.ilxomjon.dur_novvot_dokon.ui.ledger

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.databinding.ItemLedgerDayBinding
import com.ilxomjon.dur_novvot_dokon.databinding.ItemLedgerDocBinding
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerDoc

class LedgerAdapter(private val onClick: (LedgerDoc) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: List<LedgerRow> = emptyList()
    private var searching = false

    fun submit(list: List<LedgerRow>, searching: Boolean) {
        rows = list
        this.searching = searching
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = if (rows[position] is LedgerRow.Day) 0 else 1
    override fun getItemId(position: Int): Long = when (val r = rows[position]) {
        is LedgerRow.Day -> r.day
        is LedgerRow.Doc -> r.hit.doc.key.hashCode().toLong() shl 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) DayVH(ItemLedgerDayBinding.inflate(inf, parent, false)) else DocVH(ItemLedgerDocBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val r = rows[position]) {
            is LedgerRow.Day -> (holder as DayVH).bind(r)
            is LedgerRow.Doc -> (holder as DocVH).bind(r)
        }
    }

    private class DayVH(val b: ItemLedgerDayBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: LedgerRow.Day) {
            val ctx = b.root.context
            b.tvDay.text = LedgerUi.dayLabel(ctx, r.day)
            b.tvCount.text = ctx.getString(R.string.lg_docs_n, r.count)
        }
    }

    private inner class DocVH(val b: ItemLedgerDocBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: LedgerRow.Doc) {
            val ctx = b.root.context
            val h = r.hit
            val d = h.doc
            b.ivKind.setImageResource(LedgerUi.icon(d.kind))
            val (bg, fg) = when (d.kind) {
                EntryKind.SALE -> com.google.android.material.R.attr.colorPrimaryContainer to com.google.android.material.R.attr.colorOnPrimaryContainer
                EntryKind.RETURN -> com.google.android.material.R.attr.colorTertiaryContainer to com.google.android.material.R.attr.colorOnTertiaryContainer
                else -> com.google.android.material.R.attr.colorSecondaryContainer to com.google.android.material.R.attr.colorOnSecondaryContainer
            }
            b.kindBg.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(ctx, bg, 0))
            b.ivKind.imageTintList = ColorStateList.valueOf(MaterialColors.getColor(ctx, fg, 0))
            b.tvTitle.text = LedgerUi.docTitle(ctx, d)
            b.tvAmount.text = LedgerUi.money(ctx, d.total)
            b.tvAmount.setTextColor(LedgerUi.amountColor(ctx, d.kind))
            val names = if (d.isGoods) d.goods.joinToString(", ") { it.product } else d.comment
            val owner = uz.imsoft.shared.report.LedgerOwner.of(d.kindText)?.second.orEmpty()
            b.tvSub.text = listOf(LedgerUi.time(d.stamp), owner, names).filter { it.isNotBlank() }.joinToString(" · ")
            // Qidiruvda mos kelgan qatorlar
            if (searching && h.lines.isNotEmpty() && d.isGoods) {
                val sb = android.text.SpannableStringBuilder()
                val shown = h.lines.sorted().take(3)
                for ((n, i) in shown.withIndex()) {
                    val e = d.lines[i]
                    if (n > 0) sb.append('\n')
                    sb.append(LedgerUi.highlight(ctx, e.product, h.ranges[i]))
                    val det = LedgerUi.lineDetail(e)
                    if (det.isNotEmpty()) sb.append("  ").append(det)
                }
                if (h.lines.size > shown.size) sb.append('\n').append(ctx.getString(R.string.lg_more_lines, h.lines.size - shown.size))
                b.tvMatches.text = sb
                b.tvMatches.visibility = View.VISIBLE
            } else {
                b.tvMatches.visibility = View.GONE
            }
            b.tvApprox.visibility = if (h.approximate) View.VISIBLE else View.GONE
            b.root.setOnClickListener { onClick(d) }
        }
    }
}
