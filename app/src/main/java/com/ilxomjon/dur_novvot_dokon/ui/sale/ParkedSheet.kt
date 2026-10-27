package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.repo.ParkedCart
import com.ilxomjon.dur_novvot_dokon.databinding.ItemParkedBinding
import com.ilxomjon.dur_novvot_dokon.databinding.SheetParkedBinding
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.launch

/** Kutishdagi savatchalar. Do'kon nomi katta va rangli — adashib boshqa do'konga yubormaslik uchun. */
class ParkedSheet : BottomSheetDialogFragment() {
    private var _binding: SheetParkedBinding? = null
    private val binding get() = _binding!!
    private val repo get() = ServiceLocator.parked

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetParkedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = Adapter(onOpen = { open(it) }, onDelete = { delete(it) })
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        launchOnStarted { repo.items.collect { adapter.submitList(it); binding.tvEmpty.show(it.isEmpty()) } }
        viewLifecycleOwner.lifecycleScope.launch { repo.refresh() }
    }

    private fun open(p: ParkedCart) {
        val active = ServiceLocator.draft.state.value
        if (active.lines.isEmpty()) { resume(p); return }
        MaterialAlertDialogBuilder(requireContext()).setTitle(p.storeName)
            .setMessage(getString(R.string.parked_swap_message, active.store?.name.orEmpty()))
            .setPositiveButton(R.string.parked_swap) { _, _ ->
                lifecycleScope.launch { repo.parkActive(); resume(p) }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun resume(p: ParkedCart) {
        val fm = parentFragmentManager
        lifecycleScope.launch {
            val missing = repo.resume(p.id)
            dismissAllowingStateLoss()
            val root = fm.fragments.firstOrNull { it.isVisible && it.view != null }?.view ?: return@launch
            if (missing != null && missing > 0) Snackbar.make(root, getString(R.string.parked_missing, missing), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun delete(p: ParkedCart) {
        lifecycleScope.launch {
            val removed = repo.delete(p.id) ?: return@launch
            _binding?.let { b -> Snackbar.make(b.root, R.string.parked_deleted, Snackbar.LENGTH_LONG).setAction(R.string.cart_undo) { ServiceLocator.appScope.launch { repo.undoDelete(removed) } }.show() }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private class Adapter(private val onOpen: (ParkedCart) -> Unit, private val onDelete: (ParkedCart) -> Unit) : ListAdapter<ParkedCart, Adapter.VH>(Diff) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemParkedBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemParkedBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(p: ParkedCart) {
                val ctx = b.root.context
                b.tvStore.text = p.storeName.ifEmpty { ctx.getString(R.string.sale_select_store) }
                val mode = if (p.mode == DealMode.RETURN) " · " + ctx.getString(R.string.mode_return) else ""
                b.tvInfo.text = ctx.getString(R.string.home_resume_info, p.lines.size, ctx.money(p.total, p.currency)) + mode + " · " + formatDateTime(p.created)
                b.root.setOnClickListener { onOpen(p) }
                b.btnDelete.setOnClickListener { onDelete(p) }
            }
        }

        private object Diff : DiffUtil.ItemCallback<ParkedCart>() {
            override fun areItemsTheSame(a: ParkedCart, b: ParkedCart) = a.id == b.id
            override fun areContentsTheSame(a: ParkedCart, b: ParkedCart) = a == b
        }
    }

    companion object {
        fun show(fm: FragmentManager) { if (fm.findFragmentByTag("parked") == null) ParkedSheet().show(fm, "parked") }
    }
}
