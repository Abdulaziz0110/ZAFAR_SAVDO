package com.ilxomjon.dur_novvot_dokon.ui.sale

import uz.imsoft.shared.android.NumInput
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.SheetItemEditBinding
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.viewModel
import com.ilxomjon.dur_novvot_dokon.ui.formatDate
import com.ilxomjon.dur_novvot_dokon.domain.CartChecker
import com.ilxomjon.dur_novvot_dokon.domain.FeedbackReason
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ItemEditSheet : BottomSheetDialogFragment() {
    private var _binding: SheetItemEditBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: ItemEditViewModel
    private var updating = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        d.behavior.skipCollapsed = true
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetItemEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        for (e in listOf(binding.edtQty, binding.edtPrice)) NumInput.attach(e)
        val productId = args.getString(ARG_PRODUCT).orEmpty()
        vm = viewModel { ItemEditViewModel(productId, ServiceLocator.catalog, ServiceLocator.draft) }
        val product = vm.product
        if (product == null) { dismiss(); return }

        binding.tvName.text = product.name
        binding.tvInfo.text = if (product.hasBlock) getString(R.string.block_info, Money.qtyDisplay(product.blockSize)) else ""
        binding.tvInfo.show(product.hasBlock)
        val showCost = args.getBoolean(ARG_COST)
        binding.tvCost.show(showCost)
        if (showCost) binding.tvCost.text = getString(R.string.item_cost, requireContext().money(vm.costPrice, vm.currency))

        val unit = getString(if (vm.currency == Currency.SOM) R.string.unit_som else R.string.unit_usd)
        binding.tilPrice.hint = getString(R.string.item_price, unit)
        binding.tilBlocks.show(product.hasBlock)
        binding.chipPlusBlock.show(product.hasBlock)
        binding.btnRemove.setText(if (vm.exists) R.string.item_remove else R.string.item_not_needed)
        binding.btnSave.setText(if (vm.exists) R.string.item_save_update else R.string.item_save_add)

        if (savedInstanceState == null) {
            binding.edtPrice.setText(Money.plain(vm.initialPrice))
            setQty(vm.initialQty)
        }

        binding.edtQty.onTextChanged {
            if (updating) return@onTextChanged
            updating = true
            if (product.hasBlock) binding.edtBlocks.setText(Money.qtyDisplay(Money.parse(it) / product.blockSize))
            updating = false
            binding.tilQty.error = null
            refreshTotal()
        }
        binding.edtBlocks.onTextChanged {
            if (updating) return@onTextChanged
            updating = true
            binding.edtQty.setText(Money.qtyDisplay(Money.parse(it) * product.blockSize))
            updating = false
            binding.tilQty.error = null
            refreshTotal()
        }
        binding.edtPrice.onTextChanged {
            binding.tilPrice.error = null
            refreshTotal()
        }
        binding.chipPlus1.setOnClickListener { addQty(1.0) }
        binding.chipPlus5.setOnClickListener { addQty(5.0) }
        binding.chipPlus10.setOnClickListener { addQty(10.0) }
        binding.chipPlusBlock.setOnClickListener { addQty(product.blockSize) }
        binding.btnSave.setOnClickListener { save() }
        // Savatchada bo'lsa — chiqarib tashlaydi; bo'lmasa "kerak emas" deb belgilanadi (taklif bir hafta chiqmaydi)
        binding.btnRemove.setOnClickListener {
            if (vm.exists) { vm.remove(); offerUndo(R.string.cart_deleted); dismiss() } else notNeeded()
        }
        setupHints(product.id)

        val focusPrice = args.getBoolean(ARG_FOCUS_PRICE)
        val target = if (focusPrice) binding.edtPrice else binding.edtQty
        target.requestFocus()
        target.selectAll()
        refreshTotal()
    }

    private fun addQty(delta: Double) {
        setQty(Money.parse(binding.edtQty.text?.toString()) + delta)
        refreshTotal()
    }

    private fun setQty(qty: Double) {
        updating = true
        binding.edtQty.setText(Money.qtyDisplay(qty))
        vm.product?.let { if (it.hasBlock) binding.edtBlocks.setText(Money.qtyDisplay(qty / it.blockSize)) }
        updating = false
    }

    private fun refreshTotal() {
        val qty = Money.parse(binding.edtQty.text?.toString())
        val price = Money.parse(binding.edtPrice.text?.toString())
        binding.tvLineTotal.text = getString(R.string.item_total, requireContext().money(qty * price, vm.currency))
    }

    private fun save() {
        val qty = Money.parse(binding.edtQty.text?.toString())
        val priceText = binding.edtPrice.text?.toString().orEmpty().trim()
        var ok = true
        if (qty <= 0.0) { binding.tilQty.error = getString(R.string.item_error_qty); ok = false }
        if (priceText.isEmpty()) { binding.tilPrice.error = getString(R.string.item_error_price); ok = false }
        val price = Money.parseUser(priceText)
        if (priceText.isNotEmpty() && price == null) { binding.tilPrice.error = getString(R.string.amount_invalid); ok = false }
        if (Money.parseUser(binding.edtQty.text?.toString()) == null) { binding.tilQty.error = getString(R.string.amount_invalid); ok = false }
        if (!ok) return
        val changed = vm.exists
        vm.save(qty, price ?: 0.0)
        if (changed) offerUndo(R.string.item_changed)
        dismiss()
    }

    /** O'zgarish/o'chirishdan keyin ota ekranda "Bekor qilish" (Undo). */
    private fun offerUndo(msg: Int) {
        val root = parentFragment?.view ?: return
        val v = vm
        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).setAction(R.string.cart_undo) { v.undo() }.show()
    }

    private fun notNeeded() {
        val storeId = vm.storeId ?: run { dismiss(); return }
        val productId = vm.product?.id.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ServiceLocator.history.addFeedback(storeId, productId, FeedbackReason.NOT_NEEDED) }
            dismissAllowingStateLoss()
        }
    }

    /** "Shu do'konga oxirgi narx" va shu mijozning shu tovar bo'yicha kichik tarixi (v5.0). */
    private fun setupHints(productId: String) {
        val storeId = vm.storeId ?: return
        val name = vm.product?.name.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val orders = ServiceLocator.history.storeOrders(storeId)
            if (_binding == null) return@launch
            CartChecker.lastPrice(orders, productId, vm.currency)?.let { (p, at) ->
                binding.tvLastPrice.show(true)
                binding.tvLastPrice.text = getString(R.string.item_last_price, requireContext().money(p, vm.currency), formatDate(java.util.Date(at)))
            }
        }
        // 1C tarixi: qachon, qancha va qanchadan olgan
        viewLifecycleOwner.lifecycleScope.launch {
            val buys = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.ilxomjon.dur_novvot_dokon.domain.LedgerOrders.buys(
                    com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost.dbData.value.entries, storeId, name, 4,
                )
            }
            val b = _binding ?: return@launch
            if (buys.isEmpty()) return@launch
            val ctx = requireContext()
            val lines = buys.joinToString("\n") { x ->
                getString(
                    R.string.item_hist_row, uz.imsoft.shared.report.Stamp.formatDate(x.stamp),
                    Money.qtyDisplay(x.qty), ctx.money(x.price, vm.currency),
                )
            }
            b.tvHistory.show(true)
            b.tvHistory.text = getString(R.string.item_hist_title) + "\n" + lines
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PRODUCT = "product_id"
        private const val ARG_FOCUS_PRICE = "focus_price"
        private const val ARG_COST = "show_cost"

        fun show(fm: FragmentManager, productId: String, focusPrice: Boolean = false, showCost: Boolean = false) {
            ItemEditSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PRODUCT, productId); putBoolean(ARG_FOCUS_PRICE, focusPrice); putBoolean(ARG_COST, showCost)
                }
            }.show(fm, "item_edit")
        }
    }
}
