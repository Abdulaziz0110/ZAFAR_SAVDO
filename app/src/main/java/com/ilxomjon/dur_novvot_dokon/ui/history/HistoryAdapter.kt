package com.ilxomjon.dur_novvot_dokon.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ilxomjon.dur_novvot_dokon.databinding.ItemHistoryBinding

data class HistoryRow(
    val key: String,
    val title: String,
    val amount: String,
    val whenText: String,
    val info: String,
    val detailTitle: String,
    val detail: String,
    val shareText: String = "",
)

class HistoryAdapter(private val onClick: (HistoryRow) -> Unit) : ListAdapter<HistoryRow, HistoryAdapter.VH>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: HistoryRow) {
            b.tvStore.text = r.title
            b.tvAmount.text = r.amount
            b.tvWhen.text = r.whenText
            b.tvInfo.text = r.info
            b.root.setOnClickListener { onClick(r) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HistoryRow>() {
        override fun areItemsTheSame(a: HistoryRow, b: HistoryRow) = a.key == b.key
        override fun areContentsTheSame(a: HistoryRow, b: HistoryRow) = a == b
    }
}
