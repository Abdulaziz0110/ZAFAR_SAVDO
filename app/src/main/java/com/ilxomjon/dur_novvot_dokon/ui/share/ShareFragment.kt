package com.ilxomjon.dur_novvot_dokon.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.util.Pair as UPair
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.share.ImportPreview
import com.ilxomjon.dur_novvot_dokon.data.share.NotACardException
import com.ilxomjon.dur_novvot_dokon.data.share.ShareState
import com.ilxomjon.dur_novvot_dokon.data.share.ShareWorker
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentShareBinding
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.Stamp
import uz.imsoft.shared.report.TableReader
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** "Mijoz ilovasi (2-ilova)": 1C hisobotlarini import qilish, Firebase sozlash va 2-ilovaga yuborish. */
class ShareFragment : Fragment(), com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerHost by com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost {
    private var _binding: FragmentShareBinding? = null
    private val binding get() = _binding!!
    private val repo get() = ServiceLocator.share
    private var rendering = false

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importUri(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        binding.edtUrl.setText(repo.state.value.dbUrl)
        binding.btnSaveFirebase.setOnClickListener { saveFirebase() }
        binding.btnClient.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_CLIENT) }
        childFragmentManager.setFragmentResultListener(REQ_CLIENT, viewLifecycleOwner) { _, b ->
            val id = b.getString(StorePickerSheet.KEY_ID).orEmpty()
            val store = ServiceLocator.catalog.storeById(id) ?: return@setFragmentResultListener
            val st = repo.state.value
            val apply = { viewLifecycleOwner.lifecycleScope.launch { repo.setClient(store) }; Unit }
            if (st.clientId.isNotEmpty() && st.clientId != store.id) {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_change_client_title)
                    .setMessage(getString(R.string.share_change_client_msg, st.clientName, store.name))
                    .setPositiveButton(R.string.ok) { _, _ -> apply() }.setNegativeButton(R.string.cancel, null).show()
            } else apply()
        }
        binding.btnImportFile.setOnClickListener {
            if (needClient()) return@setOnClickListener
            openFile.launch(arrayOf("*/*"))
        }
        binding.btnImportServer.setOnClickListener { if (!needClient()) pickServerPeriod() }
        binding.btnHowExport.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_how_export).setMessage(R.string.share_how_export_text)
                .setPositiveButton(R.string.close, null).show()
        }
        binding.btnPublish.setOnClickListener { publish() }
        binding.btnLinkCode.setOnClickListener { shareLinkCode() }
        binding.btnPcConfig.setOnClickListener { showPcConfig() }
        binding.btnNewKeys.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_new_keys).setMessage(R.string.share_new_keys_msg)
                .setPositiveButton(R.string.ok) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { repo.regenerateKeys(); snack(R.string.share_new_keys_done) } }
                .setNegativeButton(R.string.cancel, null).show()
        }
        binding.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_clear).setMessage(R.string.share_clear_msg)
                .setPositiveButton(R.string.delete) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { repo.clearLedger() } }
                .setNegativeButton(R.string.cancel, null).show()
        }
        binding.swAuto.setOnCheckedChangeListener { _, on -> if (!rendering) repo.setAuto(on) }
        binding.swAnswer.setOnCheckedChangeListener { _, on -> if (!rendering) repo.setAnswer(on) }
        binding.swOrders.setOnCheckedChangeListener { _, on -> if (!rendering) { repo.setAcceptOrders(on); ShareWorker.kick(requireContext().applicationContext) } }
        binding.btnOrders.setOnClickListener { OrdersSheet.show(childFragmentManager) }
        binding.btnHistory.setOnClickListener {
            if (repo.state.value.entries == 0) { snack(R.string.share_ledger_empty); return@setOnClickListener }
            val hist = parentFragmentManager.findFragmentByTag("tab_" + R.id.nav_history) as? com.ilxomjon.dur_novvot_dokon.ui.history.HistoryFragment
            com.ilxomjon.dur_novvot_dokon.ui.history.HistoryFragment.open1C(navigator(), hist)
        }
        binding.tgSync.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            val m = when (id) { R.id.sync30 -> 30; R.id.sync60 -> 60; R.id.sync120 -> 120; else -> 0 }
            repo.setSyncMinutes(m)
            if (repo.state.value.configured) ShareWorker.schedule(requireContext().applicationContext, m)
        }
        binding.edtHeader.setText(ServiceLocator.shareStore.receiptHeader)
        binding.edtHeader.setOnFocusChangeListener { _, focus -> if (!focus) repo.setReceiptHeader(binding.edtHeader.text?.toString().orEmpty()) }
        binding.edtHeader.setOnEditorActionListener { v, _, _ -> repo.setReceiptHeader(v.text?.toString().orEmpty()); v.clearFocus(); false }

        launchOnStarted { repo.state.collect { render(it) } }
        viewLifecycleOwner.lifecycleScope.launch { repo.load(); refreshPrinter() }
    }

    private fun needClient(): Boolean {
        if (repo.state.value.clientId.isNotEmpty()) return false
        snack(R.string.share_need_client)
        return true
    }

    private fun render(s: ShareState) {
        if (_binding == null) return
        rendering = true
        binding.progress.show(s.busy)
        binding.btnClient.text = s.clientName.ifEmpty { getString(R.string.share_pick_client) }
        val lines = ArrayList<String>()
        lines += if (s.clientName.isEmpty()) getString(R.string.share_st_no_client) else getString(R.string.share_st_client, s.clientName)
        lines += when {
            !s.rulesOk || !s.hasSecret -> getString(R.string.share_st_no_firebase)
            s.lastPublishAt > 0 -> getString(R.string.share_st_published, formatDateTime(s.lastPublishAt))
            else -> getString(R.string.share_st_never)
        }
        if (s.lastPublishError.isNotEmpty()) lines += getString(R.string.share_st_error, s.lastPublishError)
        if (s.dirty && s.configured) lines += getString(R.string.share_st_dirty)
        binding.tvStatus.text = lines.joinToString("\n")
        binding.btnPublish.isEnabled = s.configured && !s.busy
        binding.btnLinkCode.isEnabled = s.configured
        binding.btnPcConfig.isEnabled = s.configured && s.hasQueue
        binding.btnNewKeys.isEnabled = s.configured
        binding.btnImportFile.isEnabled = !s.busy
        binding.btnImportServer.isEnabled = !s.busy
        binding.tvLedger.text = if (s.entries == 0) getString(R.string.share_ledger_empty)
        else getString(R.string.share_ledger_info, s.entries, s.coverage.joinToString(", ") { Stamp.formatDate(it.from) + "–" + Stamp.formatDate(it.to) })
        binding.btnHistory.isEnabled = s.entries > 0
        binding.tvLog.text = s.log.take(5).joinToString("\n") {
            getString(R.string.share_log_line, formatDateTime(it.at), if (it.source == "server") getString(R.string.share_src_server) else it.name, it.added, it.removed)
        }
        binding.tvLog.show(s.log.isNotEmpty())
        if (binding.swAuto.isChecked != s.autoPublish) binding.swAuto.isChecked = s.autoPublish
        if (binding.swOrders.isChecked != s.acceptOrders) binding.swOrders.isChecked = s.acceptOrders
        binding.tgSync.check(when (s.syncMinutes) { 30 -> R.id.sync30; 60 -> R.id.sync60; 120 -> R.id.sync120; 0 -> R.id.sync0; else -> R.id.sync60 })
        binding.tvOrders.text = when {
            !s.configured -> getString(R.string.ord_need_setup)
            !s.acceptOrders -> getString(R.string.ord_off)
            s.newOrders > 0 -> getString(R.string.ord_new_n, s.newOrders)
            else -> getString(R.string.ord_none_new)
        }
        binding.btnOrders.isEnabled = s.configured
        if (binding.swAnswer.isChecked != s.answerRequests) binding.swAnswer.isChecked = s.answerRequests
        if (s.hasSecret && binding.edtSecret.text.isNullOrEmpty()) binding.tilSecret.helperText = getString(R.string.share_secret_saved)
        if (binding.edtUrl.text.isNullOrEmpty() && s.dbUrl.isNotEmpty()) binding.edtUrl.setText(s.dbUrl)
        rendering = false
    }

    private suspend fun refreshPrinter() {
        val b = _binding ?: return
        if (!repo.state.value.configured) { b.tvPrinter.show(false); return }
        val st = runCatching { repo.printerStatus() }.getOrNull()
        val bb = _binding ?: return
        bb.tvPrinter.show(st != null)
        if (st == null) return
        bb.tvPrinter.text = when {
            st.at == 0L -> getString(R.string.share_printer_never, maxOf(st.queue, 0))
            else -> getString(
                if (st.printerReady) R.string.share_printer_ready else R.string.share_printer_not_ready,
                formatDateTime(st.at), maxOf(st.queue, 0),
            ) + if (st.text.isNotEmpty()) "\n" + st.text else ""
        }
    }

    private fun saveFirebase() {
        val url = binding.edtUrl.text?.toString().orEmpty()
        val secret = binding.edtSecret.text?.toString().orEmpty().ifEmpty { if (repo.state.value.hasSecret) KEEP else "" }
        if (!CloudDb.looksLikeFirebase(url)) { snack(R.string.share_err_url); return }
        if (secret.isEmpty()) { snack(R.string.share_err_secret); return }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val real = if (secret == KEEP) ServiceLocator.shareStore.secret else secret
                repo.saveFirebase(url, real)
                binding.edtSecret.setText("")
                ShareWorker.schedule(requireContext().applicationContext)
                snack(R.string.share_firebase_ok)
                refreshPrinter()
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
            val ok = repo.publish()
            // Internet bo'lmasa — paydo bo'lishi bilan fon ishi o'zi yuboradi.
            if (!ok) ShareWorker.kick(app)
            if (_binding == null) return@launch
            snack(if (ok) R.string.share_published else R.string.share_publish_failed)
            refreshPrinter()
        }
    }

    private fun shareLinkCode() {
        val code = repo.linkCode()
        val text = getString(R.string.share_link_message, repo.state.value.clientName, code)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_link_code).setMessage(getString(R.string.share_link_explain))
            .setPositiveButton(R.string.share_send) { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, getString(R.string.share_chooser)))
            }
            .setNeutralButton(R.string.share_copy) { _, _ -> copy(code) }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun showPcConfig() {
        val ini = repo.forwarderIni()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_pc_config).setMessage(getString(R.string.share_pc_config_text))
            .setPositiveButton(R.string.share_send) { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, ini) }, getString(R.string.share_chooser)))
            }
            .setNeutralButton(R.string.share_copy) { _, _ -> copy(ini) }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun copy(text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("imsoft", text))
        snack(R.string.share_copied)
    }

    // ---------------- import ----------------

    private fun importUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ctx = requireContext()
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val n = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    val b = ctx.contentResolver.openInputStream(uri)?.use { s ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val k = s.read(buf)
                            if (k < 0) break
                            out.write(buf, 0, k)
                            if (out.size() > MAX_FILE) throw IllegalArgumentException("too_big")
                        }
                        out.toByteArray()
                    } ?: throw java.io.IOException("open")
                    b to n
                }
                showPreview(repo.previewFile(bytes, name))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: NotACardException) {
                showNotACard(e)
            } catch (e: TableReader.UnsupportedFormat) {
                snackText(getString(if (e.message == "mxl") R.string.share_err_mxl else R.string.share_err_xlsx))
            } catch (e: Exception) {
                snackText(getString(R.string.share_import_fail) + "\n" + (e.message ?: e.javaClass.simpleName).take(120))
            }
        }
    }

    private fun pickServerPeriod() {
        val utc = TimeZone.getTimeZone("UTC")
        val end = Calendar.getInstance(utc).timeInMillis
        val start = Calendar.getInstance(utc).apply { add(Calendar.DAY_OF_MONTH, -30) }.timeInMillis
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText(R.string.share_import_server)
            .setSelection(UPair(start, end)).build()
        picker.addOnPositiveButtonClickListener { sel ->
            val a = sel.first ?: return@addOnPositiveButtonClickListener
            val b = sel.second ?: a
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    showPreview(repo.previewServer(localDate(a), localDate(b)))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: NotACardException) {
                    showNotACard(e)
                } catch (e: Exception) {
                    snackText(requireContext().describeError(e))
                }
            }
        }
        picker.show(childFragmentManager, "range")
    }

    /** MaterialDatePicker UTC yarim tun qaytaradi — mahalliy sanaga o'tkazamiz. */
    private fun localDate(utcMillis: Long): Date {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.time
    }

    private fun showPreview(p: ImportPreview) {
        if (_binding == null) return
        val r = p.report
        val ctx = requireContext()
        val sale = r.entries.filter { it.kind == EntryKind.SALE }
        val pay = r.entries.filter { it.kind == EntryKind.PAYMENT }
        val ret = r.entries.filter { it.kind == EntryKind.RETURN }
        fun sum(list: List<uz.imsoft.shared.report.LedgerEntry>, c: Col): String {
            val som = list.sumOf { it.v(c).som }
            val usd = list.sumOf { it.v(c).usd }
            val parts = ArrayList<String>()
            if (som != 0.0 || usd == 0.0) parts += Money.display(som) + " " + getString(R.string.unit_som)
            if (usd != 0.0) parts += Money.display(usd) + " $"
            return parts.joinToString(" + ")
        }
        fun money2(m: uz.imsoft.shared.report.Money2): String {
            val parts = ArrayList<String>()
            if (m.som != 0.0 || m.usd == 0.0) parts += Money.display(m.som) + " " + getString(R.string.unit_som)
            if (m.usd != 0.0) parts += Money.display(m.usd) + " $"
            return parts.joinToString(" + ")
        }
        val sb = StringBuilder()
        if (p.clientMismatch != null) sb.append(getString(R.string.share_pv_mismatch, p.clientMismatch, repo.state.value.clientName)).append("\n\n")
        sb.append(getString(R.string.share_pv_period, r.periodFrom?.let { Stamp.formatDate(it) } ?: "—", r.periodTo?.let { Stamp.formatDate(it) } ?: "—")).append('\n')
        sb.append(getString(R.string.share_pv_counts, r.entries.size, sale.size, pay.size, ret.size)).append('\n')
        sb.append(getString(R.string.share_pv_sales, sum(sale, Col.SUM))).append('\n')
        sb.append(getString(R.string.share_pv_payments, sum(r.entries, Col.PAYMENT))).append('\n')
        r.opening?.let { sb.append(getString(R.string.share_pv_opening, Stamp.formatDate(it.first), money2(it.second))).append('\n') }
        r.closing?.let { sb.append(getString(R.string.share_pv_closing, Stamp.formatDate(it.first), money2(it.second))).append('\n') }
        if (r.subtotalRows > 0) sb.append(getString(R.string.share_pv_subtotals, r.subtotalRows)).append('\n')
        if (r.skippedRows > 0) sb.append(getString(R.string.share_pv_skipped, r.skippedRows)).append('\n')
        sb.append(getString(R.string.share_pv_products, p.matchedProducts, p.matchedProducts + p.unmatchedProducts.size)).append('\n')
        if (p.unmatchedProducts.isNotEmpty()) sb.append(getString(R.string.share_pv_unmatched, p.unmatchedProducts.take(8).joinToString(", "))).append('\n')
        sb.append('\n').append(getString(R.string.share_pv_merge, p.merge.added, p.merge.removed, p.merge.same))
        r.warnings.forEach { sb.append("\n⚠ ").append(it) }
        r.entries.take(3).forEach { e ->
            sb.append("\n• ").append(Stamp.formatDateTime(e.stamp)).append(" · ").append(e.kindText).append(" · ").append(e.product)
            if (e.qty != 0.0) sb.append(" · ").append(Money.qtyDisplay(e.qty))
        }
        val dialog = MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.share_pv_title, p.name)).setMessage(sb.toString())
            .setNegativeButton(R.string.cancel, null)
        if (r.entries.isNotEmpty()) dialog.setPositiveButton(R.string.share_pv_save) { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch {
                repo.commit(p)
                snack(R.string.share_saved)
                if (repo.state.value.configured && repo.state.value.autoPublish) publish()
            }
        }
        dialog.show()
    }

    private fun showNotACard(e: NotACardException) {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.share_not_card_title)
            .setMessage(getString(R.string.share_not_card_msg) + "\n\n" + e.dump.take(1500))
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.share_send) { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, e.message + "\n\n" + e.dump) }, getString(R.string.share_chooser)))
            }.show()
    }

    private fun snack(res: Int) { _binding?.let { Snackbar.make(it.root, res, Snackbar.LENGTH_LONG).show() } }
    private fun snackText(t: String) { _binding?.let { Snackbar.make(it.root, t, Snackbar.LENGTH_LONG).show() } }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val REQ_CLIENT = "share_client"
        const val KEEP = "\u0000keep"
        const val MAX_FILE = 30 * 1024 * 1024
    }
}
