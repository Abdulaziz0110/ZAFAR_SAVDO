package uz.imsoft.mijoz.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.ItemMyOrderBinding
import uz.imsoft.mijoz.databinding.SheetMyOrdersBinding
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.describe
import uz.imsoft.mijoz.ui.show
import uz.imsoft.shared.OrderRequest
import uz.imsoft.shared.receipt.NumFmt

/** Shu telefondan yuborilgan "qayta buyurtma"lar va 1-ilovadagi holati. */
class MyOrdersSheet : BottomSheetDialogFragment() {
    private var _b: SheetMyOrdersBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetMyOrdersBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val res = runCatching { Locator.data.myOrders() }
            val bb = _b ?: return@launch
            bb.progress.show(false)
            res.onSuccess { render(it) }.onFailure { e ->
                bb.tvEmpty.text = requireContext().describe(e)
                bb.tvEmpty.show(true)
            }
        }
    }

    private fun render(list: List<OrderRequest>) {
        val bb = _b ?: return
        bb.list.removeAllViews()
        bb.tvEmpty.show(list.isEmpty())
        val inf = LayoutInflater.from(requireContext())
        for (o in list) {
            val ib = ItemMyOrderBinding.inflate(inf, bb.list, false)
            ib.tvTitle.text = getString(R.string.mo_item_title, if (o.at > 0) Fmt.dateTime(o.at) else "—")
            ib.tvSub.text = listOfNotNull(
                o.lines.take(5).joinToString(", ") { it.name + " " + NumFmt.qty(it.qty) } + if (o.lines.size > 5) " …" else "",
                o.note.takeIf { it.isNotBlank() }?.let { getString(R.string.mo_note, it) },
                if (o.statusAt > 0 && o.status != OrderRequest.Status.SENT) getString(R.string.mo_status_at, Fmt.dateTime(o.statusAt)) else null,
            ).joinToString("\n")
            ib.tvStatus.text = getString(
                when (o.status) {
                    OrderRequest.Status.SENT -> R.string.mo_st_sent
                    OrderRequest.Status.SEEN -> R.string.mo_st_seen
                    OrderRequest.Status.TAKEN -> R.string.mo_st_taken
                    OrderRequest.Status.REJECTED -> R.string.mo_st_rejected
                },
            )
            ib.tvStatus.setTextColor(
                when (o.status) {
                    OrderRequest.Status.TAKEN -> ContextCompat.getColor(requireContext(), R.color.brand_success)
                    OrderRequest.Status.REJECTED -> MaterialColors.getColor(ib.root, com.google.android.material.R.attr.colorError)
                    else -> MaterialColors.getColor(ib.root, com.google.android.material.R.attr.colorTertiary)
                },
            )
            ib.root.isClickable = false
            bb.list.addView(ib.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag("my_orders") == null) MyOrdersSheet().show(fm, "my_orders")
        }
    }
}
