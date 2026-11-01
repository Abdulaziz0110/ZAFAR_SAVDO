package com.ilxomjon.dur_novvot_dokon.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.util.Pair as UPair
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.share.NotACardException
import com.ilxomjon.dur_novvot_dokon.data.share.ShareState
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentClientBinding
import com.ilxomjon.dur_novvot_dokon.databinding.ItemMemberBinding
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import kotlinx.coroutines.launch
import uz.imsoft.shared.LoginRecord
import uz.imsoft.shared.Phone
import uz.imsoft.shared.report.Stamp
import java.security.SecureRandom
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Bitta mijoz: 2-ilovaga kiradigan telefonlar, 1C tarixi, chek printeri, boshqa sozlamalar. */
class ClientFragment : Fragment() {
    private var _b: FragmentClientBinding? = null
    private val b get() = _b!!
    private val repo get() = ServiceLocator.share
    private val clientId get() = requireArguments().getString(ARG_ID).orEmpty()
    private var rendering = false

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) ImportUi.import(this, uri, clientId) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentClientBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.toolbar.setNavigationOnClickListener { navigator().pop() }
        b.btnAddMember.setOnClickListener { addMember() }
        b.tvShop.setOnClickListener { Guide.members(requireContext()) }
        b.btnImport.setOnClickListener { openFile.launch(arrayOf("*/*")) }
        b.btnImport.visibility = View.GONE // v3.2: Excel import yo'q — kompyuter va server orqali
        b.btnImportServer.setOnClickListener { pickServerPeriod() }
        b.btnHistory.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                repo.select(clientId)
                val hist = parentFragmentManager.findFragmentByTag("tab_" + R.id.nav_history) as? com.ilxomjon.dur_novvot_dokon.ui.history.HistoryFragment
                com.ilxomjon.dur_novvot_dokon.ui.history.HistoryFragment.open1C(navigator(), hist)
            }
        }
        b.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_clear).setMessage(R.string.share_clear_msg)
                .setPositiveButton(R.string.delete) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { repo.clearLedger(clientId) } }
                .setNegativeButton(R.string.cancel, null).show()
        }
        b.btnPrinter.setOnClickListener {
            val c = ServiceLocator.shareStore.client(clientId) ?: return@setOnClickListener
            if (c.queueKey.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.cl_printer_on).setMessage(R.string.cl_printer_on_msg)
                    .setPositiveButton(R.string.ok) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { repo.enablePrinter(clientId); repo.publish(); showPrinterConfig() } }
                    .setNegativeButton(R.string.cancel, null).show()
            } else showPrinterConfig()
        }
        b.tgKind.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            viewLifecycleOwner.lifecycleScope.launch { repo.setKind(clientId, if (id == R.id.kindSupplier) "s" else "c") }
        }
        b.btnLinkStore.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_STORE) }
        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, bundle ->
            val st = ServiceLocator.catalog.storeById(bundle.getString(StorePickerSheet.KEY_ID).orEmpty()) ?: return@setFragmentResultListener
            viewLifecycleOwner.lifecycleScope.launch { repo.linkStore(clientId, st) }
        }
        b.btnLinkCode.setOnClickListener {
            val code = repo.linkCode(clientId)
            val name = ServiceLocator.shareStore.client(clientId)?.name.orEmpty()
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_link_code).setMessage(getString(R.string.share_link_explain))
                .setPositiveButton(R.string.share_send) { _, _ -> ImportUi.shareText(requireContext(), getString(R.string.share_link_message, name, code)) }
                .setNeutralButton(R.string.share_copy) { _, _ -> copy(code) }
                .setNegativeButton(R.string.close, null).show()
        }
        b.btnNewKeys.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_new_keys).setMessage(R.string.cl_new_keys_msg)
                .setPositiveButton(R.string.ok) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { runCatching { repo.rotateKeys(clientId) }; snack(getString(R.string.share_new_keys_done)) } }
                .setNegativeButton(R.string.cancel, null).show()
        }
        b.btnDelete.setOnClickListener {
            val name = ServiceLocator.shareStore.client(clientId)?.name.orEmpty()
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.cl_delete).setMessage(getString(R.string.cl_delete_msg, name))
                .setPositiveButton(R.string.delete) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { repo.removeClient(clientId); navigator().pop() } }
                .setNegativeButton(R.string.cancel, null).show()
        }
        launchOnStarted { repo.state.collect { render(it) } }
        viewLifecycleOwner.lifecycleScope.launch { repo.select(clientId); refreshPrinter() }
        Tour.show(this, Tour.CLIENT)
    }

    private fun render(s: ShareState) {
        val bb = _b ?: return
        val c = ServiceLocator.shareStore.client(clientId) ?: return
        rendering = true
        bb.toolbar.title = c.name
        bb.progress.showInvisible(s.busy)
        bb.tvShop.text = if (s.shopCode.isEmpty()) getString(R.string.fb_shop_code_none) else getString(R.string.fb_shop_code, s.shopCode)
        bb.membersList.removeAllViews()
        val inf = LayoutInflater.from(requireContext())
        for (m in c.members.sortedBy { it.addedAt }) {
            val mb = ItemMemberBinding.inflate(inf, bb.membersList, false)
            mb.tvName.text = m.name.ifBlank { getString(R.string.mb_no_name) }
            mb.tvPhone.text = Phone.pretty(m.phone)
            mb.btnShare.setOnClickListener { sendLogin(m.phone) }
            mb.btnDelete.setOnClickListener { removeMember(m.phone, m.name) }
            mb.root.setOnClickListener { sendLogin(m.phone) }
            bb.membersList.addView(mb.root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        bb.btnAddMember.isEnabled = s.cloudReady
        val isCur = s.clientId == clientId
        bb.tvLedger.text = if (!isCur || s.entries == 0) getString(R.string.share_ledger_empty)
        else getString(R.string.share_ledger_info, s.entries, s.coverage.joinToString(", ") { Stamp.formatDate(it.from) + "–" + Stamp.formatDate(it.to) })
        bb.tvLog.text = if (isCur) s.log.take(5).joinToString("\n") {
            getString(
                R.string.share_log_line, formatDateTime(it.at),
                when (it.source) { "server" -> getString(R.string.share_src_server); "pc" -> getString(R.string.cl_src_pc); else -> it.name }, it.added, it.removed,
            )
        } else ""
        bb.btnHistory.isEnabled = isCur && s.entries > 0
        bb.btnImportServer.isEnabled = c.storeId.isNotEmpty()
        bb.tgKind.check(if (c.kind == "s") R.id.kindSupplier else R.id.kindClient)
        val st = c.storeId.takeIf { it.isNotEmpty() }?.let { ServiceLocator.catalog.storeById(it) }
        bb.tvStore.text = if (st != null) getString(R.string.cl_store, st.name) else getString(R.string.cl_store_none)
        bb.btnPrinter.setText(if (c.queueKey.isEmpty()) R.string.cl_printer_on else R.string.share_pc_config_short)
        rendering = false
    }

    private suspend fun refreshPrinter() {
        val st = runCatching { repo.printerStatus() }.getOrNull()
        val bb = _b ?: return
        bb.tvPrinter.text = when {
            ServiceLocator.shareStore.client(clientId)?.queueKey.isNullOrEmpty() -> getString(R.string.cl_printer_off)
            st == null -> getString(R.string.share_printer_never, 0)
            st.at == 0L -> getString(R.string.share_printer_never, maxOf(st.queue, 0))
            else -> getString(if (st.printerReady) R.string.share_printer_ready else R.string.share_printer_not_ready, formatDateTime(st.at), maxOf(st.queue, 0)) +
                if (st.text.isNotEmpty()) "\n" + st.text else ""
        }
    }

    private fun showPrinterConfig() {
        val ini = repo.forwarderIni()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.share_pc_config).setMessage(getString(R.string.share_pc_config_text))
            .setPositiveButton(R.string.share_send) { _, _ -> ImportUi.shareText(requireContext(), ini) }
            .setNeutralButton(R.string.share_copy) { _, _ -> copy(ini) }
            .setNegativeButton(R.string.close, null).show()
    }

    // ---------------- telefonlar ----------------

    private fun field(hint: Int, type: Int, text: String = ""): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(requireContext()).apply { this.hint = getString(hint) }
        val edt = TextInputEditText(til.context).apply { inputType = type; setText(text) }
        til.addView(edt)
        return til to edt
    }

    private fun randomPassword(): String {
        val r = SecureRandom()
        val alpha = "abcdefghijkmnpqrstuvwxyz"
        // 8 belgi (masalan "kmrt4821"): eslash oson, taxmin qilish qiyin
        return String(CharArray(4) { alpha[r.nextInt(alpha.length)] }) + (1000 + r.nextInt(9000)).toString()
    }

    private fun addMember() {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }
        val (t1, eName) = field(R.string.mb_name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val (t2, ePhone) = field(R.string.mb_phone, InputType.TYPE_CLASS_PHONE, "+998 ")
        val (t3, ePw) = field(R.string.mb_password, InputType.TYPE_CLASS_TEXT, randomPassword())
        t3.helperText = getString(R.string.mb_password_hint)
        box.addView(t1); box.addView(t2); box.addView(t3)
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.mb_add).setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val phone = ePhone.text?.toString().orEmpty()
                val pw = ePw.text?.toString().orEmpty().trim()
                when {
                    !Phone.isValid(phone) -> snack(getString(R.string.mb_bad_phone))
                    pw.length < LoginRecord.MIN_PASSWORD -> snack(getString(R.string.mb_short_password, LoginRecord.MIN_PASSWORD))
                    else -> viewLifecycleOwner.lifecycleScope.launch {
                        b.progress.showInvisible(true)
                        try {
                            repo.addMember(clientId, phone, eName.text?.toString().orEmpty(), pw)
                            sendLogin(phone)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            snack(getString(R.string.mb_add_fail) + "\n" + requireContext().describeError(e))
                        } finally {
                            _b?.progress?.showInvisible(false)
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** Ishchiga kirish ma'lumotini yuborish (Telegram/SMS): do'kon kodi, telefon, parol. */
    private fun sendLogin(phone: String) {
        val text = repo.loginText(clientId, phone)
        if (text.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.mb_send).setMessage(text)
            .setPositiveButton(R.string.share_send) { _, _ -> ImportUi.shareText(requireContext(), text) }
            .setNeutralButton(R.string.share_copy) { _, _ -> copy(text) }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun removeMember(phone: String, name: String) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.mb_remove).setMessage(getString(R.string.mb_remove_msg, name.ifBlank { Phone.pretty(phone) }))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { repo.removeMember(clientId, phone) }
                        .onSuccess { snack(getString(R.string.mb_removed)) }
                        .onFailure { snack(requireContext().describeError(it)) }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    // ---------------- server hisoboti ----------------

    private fun pickServerPeriod() {
        val utc = TimeZone.getTimeZone("UTC")
        val end = Calendar.getInstance(utc).timeInMillis
        val start = Calendar.getInstance(utc).apply { add(Calendar.DAY_OF_MONTH, -30) }.timeInMillis
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText(R.string.share_import_server)
            .setSelection(UPair(start, end)).build()
        picker.addOnPositiveButtonClickListener { sel ->
            val a = sel.first ?: return@addOnPositiveButtonClickListener
            val z = sel.second ?: a
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    repo.select(clientId)
                    ImportUi.showPreview(this@ClientFragment, repo.previewServer(localDate(a), localDate(z)))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: NotACardException) {
                    ImportUi.showNotACard(this@ClientFragment, e)
                } catch (e: Exception) {
                    snack(requireContext().describeError(e))
                }
            }
        }
        picker.show(childFragmentManager, "range")
    }

    /** MaterialDatePicker UTC yarim tun qaytaradi — mahalliy sanaga o'tkazamiz. */
    private fun localDate(utcMillis: Long): Date {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply { clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH)) }.time
    }

    private fun copy(text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("savdo", text))
        snack(getString(R.string.share_copied))
    }

    private fun snack(t: String) { _b?.let { Snackbar.make(it.root, t, Snackbar.LENGTH_LONG).show() } }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_ID = "id"
        private const val REQ_STORE = "client_store"
        fun of(id: String) = ClientFragment().apply { arguments = bundleOf(ARG_ID to id) }
    }
}
