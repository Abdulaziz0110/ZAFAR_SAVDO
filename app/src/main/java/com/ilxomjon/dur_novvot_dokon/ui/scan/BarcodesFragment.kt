package com.ilxomjon.dur_novvot_dokon.ui.scan

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodePreview
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentBarcodesBinding
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.barcode.BarcodeImport
import uz.imsoft.shared.report.TableReader

/** Sozlamalar ▸ Shtrix kodlar: 1C ro'yxatini import qilish, tarozi yorlig'i formati, sinash, skaner. */
class BarcodesFragment : Fragment() {
    private var _b: FragmentBarcodesBinding? = null
    private val b get() = _b!!
    private val repo get() = ServiceLocator.barcodes
    private var rendering = false
    private var testJob: Job? = null

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) import(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentBarcodesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.toolbar.setNavigationOnClickListener { navigator().pop() }
        b.btnImport.setOnClickListener { openFile.launch(arrayOf("*/*")) }
        b.btnHow.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.bc_how_title).setMessage(R.string.bc_how_text).setPositiveButton(R.string.close, null).show()
        }
        b.btnLearned.setOnClickListener { showLearned() }
        b.btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.bc_clear_title)
                .setItems(arrayOf(getString(R.string.bc_clear_imported), getString(R.string.bc_clear_learned))) { _, i ->
                    viewLifecycleOwner.lifecycleScope.launch { if (i == 0) repo.clearImported() else repo.clearLearned() }
                }.setNegativeButton(R.string.cancel, null).show()
        }
        b.btnTestScan.setOnClickListener { navigator().push(ScanFragment.test()) }
        b.edtTest.onTextChanged { t ->
            testJob?.cancel()
            testJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(250)
                _b?.tvTest?.text = BarcodeTestUi.explain(requireContext(), t)
            }
        }
        // tarozi sozlamasi
        val sc = repo.scale
        rendering = true
        b.swScale.isChecked = sc.enabled
        b.edtPrefixes.setText(sc.prefixes.joinToString(","))
        b.tgCodeLen.check(when (sc.codeLen) { 4 -> R.id.code4; 6 -> R.id.code6; else -> R.id.code5 })
        b.tgValueLen.check(when (sc.valueLen) { 4 -> R.id.val4; 6 -> R.id.val6; else -> R.id.val5 })
        b.tgUnit.check(if (sc.divisor == 100.0) R.id.unit10 else R.id.unitG)
        b.swCheck.isChecked = sc.checkDigit
        b.swTrust.isChecked = sc.trustCodes
        b.swBeep.isChecked = repo.beep
        b.swVibrate.isChecked = repo.vibrate
        rendering = false
        b.swScale.setOnCheckedChangeListener { _, _ -> saveScale() }
        b.swCheck.setOnCheckedChangeListener { _, _ -> saveScale() }
        b.swTrust.setOnCheckedChangeListener { _, _ -> saveScale() }
        b.edtPrefixes.onTextChanged { saveScale() }
        for (g in listOf<MaterialButtonToggleGroup>(b.tgCodeLen, b.tgValueLen, b.tgUnit)) g.addOnButtonCheckedListener { _, _, checked -> if (checked) saveScale() }
        b.swBeep.setOnCheckedChangeListener { _, on -> repo.beep = on }
        b.swVibrate.setOnCheckedChangeListener { _, on -> repo.vibrate = on }
        launchOnStarted { repo.state.collect { render() } }
        viewLifecycleOwner.lifecycleScope.launch { repo.load() }
    }

    private fun saveScale() {
        if (rendering) return
        val bb = _b ?: return
        val prefixes = bb.edtPrefixes.text?.toString().orEmpty().split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() && it.all { c -> c.isDigit() } && it.length <= 3 }
        repo.scale = repo.scale.copy(
            enabled = bb.swScale.isChecked,
            prefixes = prefixes.ifEmpty { listOf("20") },
            codeLen = when (bb.tgCodeLen.checkedButtonId) { R.id.code4 -> 4; R.id.code6 -> 6; else -> 5 },
            valueLen = when (bb.tgValueLen.checkedButtonId) { R.id.val4 -> 4; R.id.val6 -> 6; else -> 5 },
            divisor = if (bb.tgUnit.checkedButtonId == R.id.unit10) 100.0 else 1000.0,
            checkDigit = bb.swCheck.isChecked,
            trustCodes = bb.swTrust.isChecked,
        )
        // Sinash maydonini yangilash
        val t = bb.edtTest.text?.toString().orEmpty()
        if (t.isNotBlank()) viewLifecycleOwner.lifecycleScope.launch { _b?.tvTest?.text = BarcodeTestUi.explain(requireContext(), t) }
    }

    private fun render() {
        val bb = _b ?: return
        val s = repo.state.value
        bb.tvStatus.text = if (s.imported == 0) getString(R.string.bc_none) else getString(R.string.bc_status, s.imported)
        bb.tvStatusSub.text = listOfNotNull(
            if (s.importedAt > 0) getString(R.string.bc_status_file, s.fileName, formatDateTime(s.importedAt)) else null,
            if (s.unmatched > 0) getString(R.string.bc_status_unmatched, s.unmatched) else null,
            getString(R.string.bc_status_learned, s.learned + s.scaleLearned),
        ).joinToString("\n")
    }

    private fun import(uri: Uri) {
        val ctx = requireContext()
        _b?.progress?.showInvisible(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val n = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?: uri.lastPathSegment.orEmpty()
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException("open")
                    bytes to n
                }
                showPreview(repo.preview(bytes, name))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BarcodeImport.NotBarcodes) {
                MaterialAlertDialogBuilder(ctx).setTitle(R.string.bc_not_list_title).setMessage(R.string.bc_not_list).setPositiveButton(R.string.close, null).show()
            } catch (e: TableReader.UnsupportedFormat) {
                snack(getString(if (e.message == "mxl") R.string.share_err_mxl else R.string.share_err_xlsx))
            } catch (e: Exception) {
                snack(getString(R.string.share_import_fail) + "\n" + (e.message ?: e.javaClass.simpleName).take(120))
            } finally {
                _b?.progress?.showInvisible(false)
            }
        }
    }

    private fun showPreview(p: BarcodePreview) {
        val l = p.linked
        val sb = StringBuilder()
        sb.append(getString(R.string.bc_pv_rows, p.parsed.rows.size)).append('\n')
        sb.append(getString(R.string.bc_pv_linked, l.map.size, l.byCode, l.byName)).append('\n')
        if (l.unmatched.isNotEmpty()) {
            sb.append(getString(R.string.bc_pv_unmatched, l.unmatched.size)).append('\n')
            sb.append(l.unmatched.take(6).joinToString("\n") { "• " + (it.code.ifEmpty { "—" }) + " " + it.name + " (" + it.barcode + ")" }).append('\n')
        }
        if (p.parsed.scalePrefixes.isNotEmpty()) {
            val n = p.parsed.rows.count { r -> r.barcode.length == 7 && p.parsed.scalePrefixes.any { r.barcode.startsWith(it) } }
            sb.append(getString(R.string.bc_pv_scale, n, p.parsed.scalePrefixes.joinToString(", "))).append('\n')
        }
        if (p.parsed.codeCol < 0) sb.append('\n').append(getString(R.string.bc_pv_no_code))
        if (l.byCode == 0 && l.byName > 0) sb.append('\n').append(getString(R.string.bc_pv_code_mismatch))
        sb.append('\n').append(getString(R.string.bc_pv_replace))
        val d = MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.bc_pv_title, p.fileName)).setMessage(sb.toString())
            .setNegativeButton(R.string.cancel, null)
        if (l.map.isNotEmpty()) d.setPositiveButton(R.string.save) { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch {
                repo.commit(p)
                snack(getString(R.string.bc_saved, l.map.size))
                // tarozi prefikslari o'zgargan bo'lishi mumkin — maydonni yangilaymiz
                rendering = true
                _b?.edtPrefixes?.setText(repo.scale.prefixes.joinToString(","))
                _b?.swScale?.isChecked = repo.scale.enabled
                rendering = false
            }
        }
        d.show()
    }

    private fun showLearned() {
        val items = repo.learnedItems()
        if (items.isEmpty()) { snack(getString(R.string.bc_learned_none)); return }
        val byId = ServiceLocator.catalog.state.value.productsById
        val labels = items.map { (if (it.scale) "⚖ " + getString(R.string.bc_scale_code, it.key) else it.key) + " → " + (byId[it.productId]?.name ?: it.productId) }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.bc_learned_title)
            .setItems(labels.toTypedArray()) { _, i ->
                MaterialAlertDialogBuilder(requireContext()).setMessage(getString(R.string.bc_forget_q, labels[i]))
                    .setPositiveButton(R.string.delete) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { repo.forget(items[i]) } }
                    .setNegativeButton(R.string.cancel, null).show()
            }.setNegativeButton(R.string.close, null).show()
    }

    private fun snack(t: String) { _b?.let { Snackbar.make(it.root, t, Snackbar.LENGTH_LONG).show() } }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
