package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentCartBinding
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.viewModel

class CartFragment : Fragment() {
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: CartViewModel
    private lateinit var adapter: CartAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { CartViewModel(ServiceLocator.draft) }
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        binding.toolbar.setOnMenuItemClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_cart_title)
                .setMessage(R.string.clear_cart_message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    val before = vm.clear()
                    Snackbar.make(binding.root, R.string.cart_cleared, Snackbar.LENGTH_LONG).setAction(R.string.cart_undo) { vm.undo(before) }.show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }

        adapter = CartAdapter { line -> ItemEditSheet.show(childFragmentManager, line.productId) }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val line = adapter.lineAt(pos)
                vm.remove(line)
                Snackbar.make(binding.root, R.string.cart_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.cart_undo) { vm.restore(line) }.show()
            }
        }).attachToRecyclerView(binding.recycler)

        binding.btnCheckout.setOnClickListener { ServiceLocator.kpi.tap(); FinishSheet.show(childFragmentManager) }
        binding.btnKeepAsIs.setOnClickListener { vm.keepAsIs() }
        launchOnStarted { vm.state.collect { render(it) } }
        launchOnStarted {
            vm.warnings.collect { w ->
                binding.warnBox.show(w.isNotEmpty())
                binding.tvWarnings.text = CheckerSupport.text(requireContext(), vm.state.value, w)
            }
        }
    }

    private fun render(d: DraftState) {
        binding.tvStore.text = d.store?.name.orEmpty()
        binding.tvStore.show(d.store != null)
        adapter.submit(d.lines, d.currency)
        binding.tvEmpty.show(d.lines.isEmpty())
        binding.recycler.show(d.lines.isNotEmpty())
        binding.tvCount.text = getString(R.string.sale_items_count, d.itemCount)
        binding.tvTotal.text = requireContext().money(d.total, d.currency)
        binding.btnCheckout.isEnabled = d.lines.isNotEmpty() && d.store != null
        binding.toolbar.menu.findItem(R.id.action_clear)?.isVisible = d.lines.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
