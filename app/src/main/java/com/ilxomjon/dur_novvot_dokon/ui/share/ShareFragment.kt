package com.ilxomjon.dur_novvot_dokon.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.share.ClientSummary
import com.ilxomjon.dur_novvot_dokon.data.share.ShareState
import com.ilxomjon.dur_novvot_dokon.data.share.ShareWorker
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentShareBinding
import com.ilxomjon.dur_novvot_dokon.databinding.ItemClientBinding
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import kotlinx.coroutines.launch
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.report.Stamp

/**
 * "Mijozlar va 2-ilova" (v2.6, ko'p mijozli): mijozlar ro'yxati, Excel import (mijoz fayldagi nomdan aniqlanadi),
 * kompyuterdan avtomatik eksport, umumiy holat, buyurtmalar, avto-yangilash, chek sarlavhasi, Firebase.
 */
class ShareFragment : Fragment(), com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerHost by com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost {
    private var _binding: FragmentShareBinding? = null
    private val binding get() = _binding!!
    private val repo get() = ServiceLocator.share
    private var rendering = false

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) ImportUi.import(this, uri, null) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        binding.edtUrl.setText(repo.state.value.dbUrl)
        binding.btnSaveFirebase.setOnClickListener { saveFirebase() }
        binding.btnFbGuide.setOnClickListener { Guide.firebase(requireContext()) }
        binding.btnAddClient.setOnClickListener { addClient() }
        childFragmentManager.setFragmentResultListener(REQ_CLIENT, viewLifecycleOwner) { _, b ->
            val id = b.getString(StorePickerSheet.KEY_ID).orEmpty()
            val st = ServiceLocator.catalog.storeById(id) ?: return@setFragmentResultListener
            viewLifecycleOwner.lifecycleScope.launch {
                repo.addClient(st)
                navigator().push(ClientFragment.of(repo.state.value.clientId))
            }
        }
        binding.btnImportFile.setOnClickListener { openFile.launch(arrayOf("*/*")) }
        binding.btnHowExport.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_how_export).setMessage(R.string.share_how_export_text)
                .setPositiveButton(R.string.close, null).show()
        }
        binding.btnPublish.setOnClickListener { publish() }
        binding.btnPcConfig.setOnClickListener { showPcConfig() }
        binding.btnPcHelp.setOnClickListener { Guide.pc(requireContext()) }
        binding.tgPc.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            val h = when (id) { R.id.pc1 -> 1; R.id.pc2 -> 2; R.id.pc3 -> 3; else -> 0 }
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { repo.setPcSchedule(h, repo.state.value.pcDays) }.onFailure { snackText(getString(R.string.share_err_net)) }
            }
        }
        binding.swAuto.setOnCheckedChangeListener { _, on -> if (!rendering) repo.setAuto(on) }
        binding.swAnswer.setOnCheckedChangeListener { _, on -> if (!rendering) repo.setAnswer(on) }
        binding.swOrders.setOnCheckedChangeListener { _, on -> if (!rendering) { repo.setAcceptOrders(on); ShareWorker.kick(requireContext().applicationContext) } }
        binding.btnOrders.setOnClickListener { OrdersSheet.show(childFragmentManager) }
        binding.tgSync.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            val m = when (id) { R.id.sync30 -> 30; R.id.sync60 -> 60; R.id.sync120 -> 120; else -> 0 }
            repo.setSyncMinutes(m)
            if (repo.state.value.cloudReady) ShareWorker.schedule(requireContext().applicationContext, m)
        }
        binding.edtHeader.setText(ServiceLocator.shareStore.receiptHeader)
        binding.edtHeader.setOnFocusChangeListener { _, focus -> if (!focus) repo.setReceiptHeader(binding.edtHeader.text?.toString().orEmpty()) }
        binding.edtHeader.setOnEditorActionListener { v, _, _ -> repo.setReceiptHeader(v.text?.toString().orEmpty()); v.clearFocus(); false }
        binding.tvShopCode.setOnClickListener { copy(repo.state.value.shopCode) }

        launchOnStarted { repo.state.collect { render(it) } }
        viewLifecycleOwner.lifecycleScope.launch { repo.load(); runCatching { repo.refreshPcStatus() } }
        Tour.show(this, Tour.SHARE)
    }

    private fun addClient() {
        val items = arrayOf(getString(R.string.cl_add_from_stores), getString(R.string.cl_add_by_name))
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.cl_add)
            .setItems(items) { _, i -> if (i == 0) StorePickerSheet.show(childFragmentManager, REQ_CLIENT) else addByName() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun addByName() {
        val ctx = requireContext()
        val til = TextInputLayout(ctx).apply { setPadding(48, 16, 48, 0); hint = getString(R.string.cl_name_hint) }
        val edt = TextInputEditText(til.context).apply { inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS or android.text.InputType.TYPE_CLASS_TEXT }
        til.addView(edt)
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.cl_add_by_name).setView(til)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = edt.text?.toString().orEmpty().trim()
                if (n.isEmpty()) return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.addClientByName(n, "c")
                    navigator().push(ClientFragment.of(repo.state.value.clientId))
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun render(s: ShareState) {
        val b = _binding ?: return
        rendering = true
        b.progress.showInvisible(s.busy)
        b.tvClientsInfo.text = if (s.clients.isEmpty()) getString(R.string.cl_none)
        else getString(R.string.cl_info, s.clients.size, s.clients.sumOf { it.members })
        renderClients(s.clients)
        val lines = ArrayList<String>()
        lines += when {
            !s.cloudReady -> getString(R.string.share_st_no_firebase)
            s.lastPublishAt > 0 -> getString(R.string.share_st_published, formatDateTime(s.lastPublishAt))
            else -> getString(R.string.share_st_never)
        }
        if (s.lastPublishError.isNotEmpty()) lines += getString(R.string.share_st_error, s.lastPublishError)
        if (s.dirty && s.configured) lines += getString(R.string.share_st_dirty)
        b.tvStatus.text = lines.joinToString("\n")
        b.btnPublish.isEnabled = s.configured && !s.busy
        b.btnImportFile.isEnabled = !s.busy
        b.tvPc.text = when {
            !s.cloudReady -> getString(R.string.share_st_no_firebase)
            s.pcAt > 0 -> getString(if (s.pcOk) R.string.pc_st_ok else R.string.pc_st_err, formatDateTime(s.pcAt), s.pcStatus)
            else -> getString(R.string.pc_st_never)
        }
        b.tgPc.check(when (s.pcHours) { 1 -> R.id.pc1; 2 -> R.id.pc2; 3 -> R.id.pc3; else -> R.id.pc0 })
        b.btnPcConfig.isEnabled = s.cloudReady
        if (b.swAuto.isChecked != s.autoPublish) b.swAuto.isChecked = s.autoPublish
        if (b.swAnswer.isChecked != s.answerRequests) b.swAnswer.isChecked = s.answerRequests
        if (b.swOrders.isChecked != s.acceptOrders) b.swOrders.isChecked = s.acceptOrders
        b.tgSync.check(when (s.syncMinutes) { 30 -> R.id.sync30; 120 -> R.id.sync120; 0 -> R.id.sync0; else -> R.id.sync60 })
        b.tvOrders.text = when {
            !s.configured -> getString(R.string.ord_need_setup)
            !s.acceptOrders -> getString(R.string.ord_off)
            s.newOrders > 0 -> getString(R.string.ord_new_n, s.newOrders)
            else -> getString(R.string.ord_none_new)
        }
        b.btnOrders.isEnabled = s.configured
        b.tvShopCode.text = if (s.shopCode.isEmpty()) getString(R.string.fb_shop_code_none) else getString(R.string.fb_shop_code, s.shopCode)
        if (s.hasSecret && b.edtSecret.text.isNullOrEmpty()) b.tilSecret.helperText = getString(R.string.share_secret_saved)
        if (b.edtUrl.text.isNullOrEmpty() && s.dbUrl.isNotEmpty()) b.edtUrl.setText(s.dbUrl)
        rendering = false
    }

    private fun renderClients(list: List<ClientSummary>) {
        val b = _binding ?: return
        b.clientsList.removeAllViews()
        val inf = LayoutInflater.from(requireContext())
        for (c in list) {
            val ib = ItemClientBinding.inflate(inf, b.clientsList, false)
            ib.tvInitial.text = c.name.trim().firstOrNull()?.uppercase() ?: "?"
            ib.tvName.text = c.name
            val parts = ArrayList<String>()
            if (c.kind == "s") parts += getString(R.string.cl_kind_supplier)
            parts += if (c.entries > 0) getString(R.string.cl_entries, c.entries, Stamp.formatDate(c.coveredTo)) else getString(R.string.cl_no_entries)
            parts += if (c.members > 0) getString(R.string.cl_members, c.members) else getString(R.string.cl_no_members)
            if (c.hasPrinter) parts += getString(R.string.cl_has_printer)
            ib.tvSub.text = parts.joinToString(" · ")
            ib.tvBadge.show(c.newOrders > 0)
            ib.tvBadge.text = c.newOrders.toString()
            ib.root.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch { repo.select(c.id); navigator().push(ClientFragment.of(c.id)) }
            }
            b.clientsList.addView(ib.root)
        }
    }

    private fun saveFirebase() {
        val url = binding.edtUrl.text?.toString().orEmpty()
        val secret = binding.edtSecret.text?.toString().orEmpty().ifEmpty { if (repo.state.value.hasSecret) KEEP else "" }
        if (!CloudDb.looksLikeFirebase(CloudDb.normalizeUrl(url))) { snack(R.string.share_err_url); return }
        if (secret.isEmpty()) { snack(R.string.share_err_secret); return }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val real = if (secret == KEEP) ServiceLocator.shareStore.secret else secret
                repo.saveFirebase(url, real)
                _binding?.edtSecret?.setText("")
                ShareWorker.schedule(requireContext().applicationContext)
                snack(R.string.share_firebase_ok)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                snackText(getString(R.string.share_firebase_fail) + "\n" + cloudError(e))
            }
        }
    }

    private fun cloudError(e: Throwable): String = when {
        e is CloudDb.CloudException && e.denied -> getString(R.string.share_err_denied)
        e is CloudDb.CloudException && e.code == 404 -> getString(R.string.share_err_404)
        e is IllegalStateException && e.message == "rules_not_applied" -> getString(R.string.share_err_rules)
        e is java.net.UnknownHostException -> getString(R.string.share_err_net)
        e is java.io.IOException -> getString(R.string.share_err_net) + " (" + e.message.orEmpty().take(80) + ")"
        else -> e.message.orEmpty().take(160)
    }

    private fun publish() {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = repo.publish(all = true)
            if (!ok) ShareWorker.kick(app)
            snack(if (ok) R.string.share_published else R.string.share_publish_failed)
        }
    }

    private fun showPcConfig() {
        val ini = repo.forwarderIni()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_pc_config).setMessage(getString(R.string.share_pc_config_text))
            .setPositiveButton(R.string.share_send) { _, _ -> ImportUi.shareText(requireContext(), ini) }
            .setNeutralButton(R.string.share_copy) { _, _ -> copy(ini) }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun copy(text: String) {
        if (text.isEmpty()) return
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("savdo", text))
        snack(R.string.share_copied)
    }

    private fun snack(res: Int) { _binding?.let { Snackbar.make(it.root, res, Snackbar.LENGTH_LONG).show() } }
    private fun snackText(t: String) { _binding?.let { Snackbar.make(it.root, t, Snackbar.LENGTH_LONG).show() } }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Suppress("unused")
    private fun EditText.str() = text?.toString().orEmpty()

    private companion object {
        const val REQ_CLIENT = "share_client"
        const val KEEP = "\u0000keep"
    }
}
