package com.ilxomjon.dur_novvot_dokon.ui.outbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.repo.OutboxRepository
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentOutboxBinding
import com.ilxomjon.dur_novvot_dokon.databinding.ItemOutboxBinding
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEntry
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxKind
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import com.ilxomjon.dur_novvot_dokon.ui.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OutboxEvent {
    class Checked(val found: Boolean) : OutboxEvent
    class Failed(val cause: Throwable) : OutboxEvent
}

class OutboxViewModel(private val outbox: OutboxRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<OutboxEntry>>(emptyList())
    val items: StateFlow<List<OutboxEntry>> = _items.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _events = MutableSharedFlow<OutboxEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<OutboxEvent> = _events.asSharedFlow()
    val sending: StateFlow<Boolean> = outbox.sending

    fun refresh() { viewModelScope.launch { _items.value = withContext(Dispatchers.IO) { outbox.list() } } }

    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block() } catch (e: Exception) { _events.tryEmit(OutboxEvent.Failed(e)) } finally { _busy.value = false; refresh() }
        }
    }

    fun sendNow() = run { outbox.drainNow() }
    fun check(id: Long) = run { _events.tryEmit(OutboxEvent.Checked(outbox.checkOnServer(id))) }
    fun forceResend(id: Long) = run { outbox.forceResend(id) }
    fun markSent(id: Long) = run { outbox.markSentManually(id) }
}

/** Navbat: har bir buyurtma/to'lov kvitansiya kodi va aniq holati bilan. "Tekshirish kerak" lar yuqorida. */
class OutboxFragment : Fragment() {
    private var _binding: FragmentOutboxBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: OutboxViewModel
    private lateinit var adapter: Adapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOutboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { OutboxViewModel(ServiceLocator.outbox) }
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        adapter = Adapter { e -> showActions(e) }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.btnSendNow.setOnClickListener { vm.sendNow() }
        launchOnStarted { vm.items.collect { adapter.submitList(it); binding.tvEmpty.show(it.isEmpty()); binding.recycler.show(it.isNotEmpty()) } }
        launchOnStarted { vm.busy.collect { binding.progress.showInvisible(it); binding.btnSendNow.isEnabled = !it } }
        launchOnStarted { ServiceLocator.outbox.counts.collect { vm.refresh() } }
        launchOnStarted {
            vm.events.collect { e ->
                val msg = when (e) {
                    is OutboxEvent.Checked -> getString(if (e.found) R.string.queue_found else R.string.queue_not_found)
                    is OutboxEvent.Failed -> requireContext().describeError(e.cause)
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() { super.onResume(); vm.refresh() }

    private fun showActions(e: OutboxEntry) {
        val ctx = requireContext()
        val detail = StringBuilder()
        detail.append(OutboxUi.label(ctx, e.state))
        OutboxUi.reason(ctx, e.checkReason)?.let { detail.append("\n\n").append(it) }
        e.lastError?.takeIf { it.isNotBlank() }?.let {
            detail.append("\n\n").append(if (it == "admin_wait") getString(R.string.err_admin_wait) else getString(R.string.err_details, it.take(160)))
        }
        e.serverRowId?.let { detail.append("\n\n").append(getString(R.string.queue_server_id, it)) }
        val b = MaterialAlertDialogBuilder(ctx).setTitle(e.receipt + " · " + e.storeName).setMessage(detail.toString())
            .setNegativeButton(R.string.close, null)
        if (OutboxUi.canAct(e.state)) {
            b.setPositiveButton(R.string.queue_check) { _, _ -> vm.check(e.id) }
            b.setNeutralButton(R.string.queue_more) { _, _ -> showDangerous(e) }
        }
        b.show()
    }

    /** Xavfli amallar alohida va aniq ogohlantirish bilan. */
    private fun showDangerous(e: OutboxEntry) {
        val items = arrayOf(getString(R.string.queue_force_resend), getString(R.string.queue_mark_sent))
        MaterialAlertDialogBuilder(requireContext()).setTitle(e.receipt).setItems(items) { _, which ->
            if (which == 0) {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.queue_force_resend).setMessage(R.string.queue_force_warning)
                    .setPositiveButton(R.string.queue_force_confirm) { _, _ -> vm.forceResend(e.id) }.setNegativeButton(R.string.cancel, null).show()
            } else {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.queue_mark_sent).setMessage(R.string.queue_mark_sent_warning)
                    .setPositiveButton(R.string.queue_mark_sent) { _, _ -> vm.markSent(e.id) }.setNegativeButton(R.string.cancel, null).show()
            }
        }.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private class Adapter(private val onClick: (OutboxEntry) -> Unit) : ListAdapter<OutboxEntry, Adapter.VH>(Diff) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemOutboxBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemOutboxBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(e: OutboxEntry) {
                val ctx = b.root.context
                b.tvReceipt.text = e.receipt
                b.tvStore.text = e.storeName.ifEmpty { "—" }
                b.tvAmount.text = Money.display(e.amount)
                b.tvState.text = OutboxUi.label(ctx, e.state)
                b.tvState.setTextColor(
                    ContextCompat.getColor(ctx, if (OutboxUi.needsAttention(e.state)) R.color.brand_error else if (e.state.isDone) R.color.brand_primary else R.color.brand_tertiary),
                )
                val kind = ctx.getString(if (e.kind == OutboxKind.ORDER) R.string.queue_kind_order else R.string.queue_kind_payment)
                val parts = arrayListOf(kind, formatDateTime(e.createdAt))
                if (e.state == OutboxState.SENT && e.sentAt > 0) parts += ctx.getString(R.string.queue_sent_at, formatDateTime(e.sentAt))
                OutboxUi.reason(ctx, e.checkReason)?.takeIf { e.state == OutboxState.NEEDS_CHECK }?.let { parts += it }
                b.tvInfo.text = parts.joinToString(" · ")
                b.root.setOnClickListener { onClick(e) }
            }
        }

        private object Diff : DiffUtil.ItemCallback<OutboxEntry>() {
            override fun areItemsTheSame(a: OutboxEntry, b: OutboxEntry) = a.id == b.id
            override fun areContentsTheSame(a: OutboxEntry, b: OutboxEntry) = a == b
        }
    }
}
