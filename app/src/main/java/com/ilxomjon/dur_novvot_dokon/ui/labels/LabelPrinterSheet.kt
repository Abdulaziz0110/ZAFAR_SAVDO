package com.ilxomjon.dur_novvot_dokon.ui.labels

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.labels.LabelPrinterClient
import com.ilxomjon.dur_novvot_dokon.data.labels.LabelState
import com.ilxomjon.dur_novvot_dokon.databinding.SheetLabelPrinterBinding
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.share.ImportUi
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import kotlinx.coroutines.launch

/** Yorliq printeri: kompyuterni topish (Wi-Fi), manzil, bulut orqali, yorliq o'lchami, sinov va kompyuter sozlamasi. */
class LabelPrinterSheet : BottomSheetDialogFragment() {
    private var _b: SheetLabelPrinterBinding? = null
    private val b get() = _b!!
    private val repo get() = ServiceLocator.labels
    private var busy = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val d = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        d.behavior.skipCollapsed = true
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return d
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetLabelPrinterBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val s = repo.state.value
        // Ega faqat ilova ichida ishlaydi: kompyuterdagi dastur rejimi (label_printer.py) yashirin, doim ulashilgan printer.
        b.tgMode.show(false)
        if (!s.lpd) repo.setMode(com.ilxomjon.dur_novvot_dokon.data.labels.MODE_LPD)
        b.tgMode.check(R.id.modeLpd)
        b.tgMode.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            saveHost()
            repo.setMode(if (id == R.id.modeApp) com.ilxomjon.dur_novvot_dokon.data.labels.MODE_APP else com.ilxomjon.dur_novvot_dokon.data.labels.MODE_LPD)
            applyMode()
            check()
        }
        b.edtQueue.setText(s.queue)
        b.swRotate.isChecked = s.rotate
        b.swRotate.setOnCheckedChangeListener { _, on -> repo.setRotate(on) }
        b.edtHost.setText(s.host)
        b.edtPort.setText(s.port.toString())
        val cloudReady = ServiceLocator.shareStore.isCloudReady
        b.swCloud.isChecked = s.cloud && cloudReady
        b.swCloud.isEnabled = cloudReady
        b.tvCloudHint.setText(if (cloudReady) R.string.lb_cloud_hint else R.string.lb_cloud_off)
        b.swCloud.setOnCheckedChangeListener { _, on -> repo.setCloud(on); renderStatus() }
        b.tgSize.check(sizeId(s.wMm, s.hMm))
        b.tgDpi.check(if (s.dpi >= 300) R.id.dpi300 else R.id.dpi203)
        b.tgSize.addOnButtonCheckedListener { _, id, checked -> if (checked) saveSize(id) }
        b.tgDpi.addOnButtonCheckedListener { _, _, checked -> if (checked) saveSize(b.tgSize.checkedButtonId) }
        b.btnFind.setOnClickListener { find() }
        b.btnCheck.setOnClickListener { saveHost(); check() }
        b.btnTest.setOnClickListener { saveHost(); test() }
        b.btnIni.setOnClickListener { showIni() }
        // Qog'ozga moslash
        b.sShiftX.value = (Math.round(s.shiftXmm * 2) / 2.0).toFloat().coerceIn(-15f, 15f)
        b.sShiftY.value = (Math.round(s.shiftYmm * 2) / 2.0).toFloat().coerceIn(-10f, 10f)
        b.sDensity.value = s.density.toFloat().coerceIn(4f, 15f)
        renderFit()
        val fitListener = com.google.android.material.slider.Slider.OnChangeListener { _, _, fromUser ->
            if (fromUser) { repo.setFit(b.sShiftX.value.toDouble(), b.sShiftY.value.toDouble(), b.sDensity.value.toInt()); renderFit() }
        }
        b.sShiftX.addOnChangeListener(fitListener)
        b.sShiftY.addOnChangeListener(fitListener)
        b.sDensity.addOnChangeListener(fitListener)
        b.btnCalib.setOnClickListener { saveHost(); calibrate() }
        b.btnCustomSize.setOnClickListener { customSize() }
        applyMode()
        renderStatus()
        if (s.host.isNotBlank() || cloudReady) check()
    }

    /** Ulashilgan printer (LPD) yoki kompyuter dasturi: keraksiz maydonlar yashiriladi. */
    private fun applyMode() {
        val bb = _b ?: return
        val lpd = repo.state.value.lpd
        val cloudReady = ServiceLocator.shareStore.isCloudReady
        bb.tilQueue.show(lpd)
        bb.swRotate.show(lpd)
        bb.tilPort.show(!lpd)
        bb.swCloud.show(!lpd)
        bb.tvCloudHint.show(!lpd)
        bb.btnIni.show(!lpd)
        bb.tgDpi.show(true)
        bb.tvSetupHint.setText(if (lpd) R.string.lb_setup_hint_lpd else R.string.lb_setup_hint)
        bb.tilHost.helperText = getString(if (lpd) R.string.lb_host_hint_lpd else R.string.lb_host_hint)
        if (!lpd) bb.swCloud.isEnabled = cloudReady
    }

    private fun renderFit() {
        val bb = _b ?: return
        val s = repo.state.value
        bb.tvShiftX.text = getString(R.string.lb_shift_x, "%+.1f".format(java.util.Locale.US, s.shiftXmm))
        bb.tvShiftY.text = getString(R.string.lb_shift_y, "%+.1f".format(java.util.Locale.US, s.shiftYmm))
        bb.tvDensity.text = getString(R.string.lb_density, s.density)
    }

    private fun calibrate() {
        val act = requireActivity()
        val root = b.root
        act.lifecycleScope.launch {
            val msg = runCatching { repo.printCalibration(); act.getString(R.string.lb_calib_sent) }
                .getOrElse { e -> if (e is Exception) act.labelErrorText(e) else (e.message ?: "") }
            com.google.android.material.snackbar.Snackbar.make(root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
        }
    }

    /** Ro'yxatda yo'q o'lcham (mm): eni va bo'yi. */
    private fun customSize() {
        val ctx = requireContext()
        val s = repo.state.value
        val box = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(48, 16, 48, 0) }
        fun field(hint: Int, v: Double): com.google.android.material.textfield.TextInputEditText {
            val til = com.google.android.material.textfield.TextInputLayout(ctx).apply { this.hint = getString(hint) }
            val e = com.google.android.material.textfield.TextInputEditText(til.context).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText("%.0f".format(java.util.Locale.US, v))
            }
            til.addView(e)
            box.addView(til, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return e
        }
        val ew = field(R.string.lb_size_w, s.wMm)
        val eh = field(R.string.lb_size_h, s.hMm)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx).setTitle(R.string.lb_custom_size).setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val w = ew.text?.toString()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 15.0..110.0 }
                val h = eh.text?.toString()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 10.0..200.0 }
                if (w == null || h == null) return@setPositiveButton
                repo.setSize(w, h, repo.state.value.dpi)
                _b?.let { bb -> bb.tgSize.clearChecked(); sizeId(w, h).takeIf { it != View.NO_ID }?.let { bb.tgSize.check(it) } }
                renderStatus()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun sizeId(w: Double, h: Double): Int = when {
        w == 58.0 && h == 30.0 -> R.id.size5830
        w == 40.0 && h == 30.0 -> R.id.size4030
        w == 30.0 && h == 20.0 -> R.id.size3020
        w == 58.0 && h == 40.0 -> R.id.size5840
        else -> View.NO_ID
    }

    private fun saveSize(id: Int) {
        val (w, h) = when (id) {
            R.id.size5830 -> 58.0 to 30.0
            R.id.size4030 -> 40.0 to 30.0
            R.id.size3020 -> 30.0 to 20.0
            R.id.size5840 -> 58.0 to 40.0
            else -> repo.state.value.wMm to repo.state.value.hMm
        }
        repo.setSize(w, h, if (b.tgDpi.checkedButtonId == R.id.dpi300) 300 else 203)
        renderStatus()
    }

    private fun saveHost() {
        if (repo.state.value.lpd) repo.setQueue(b.edtQueue.text?.toString().orEmpty())
        val host = b.edtHost.text?.toString()?.trim().orEmpty()
        val port = b.edtPort.text?.toString()?.toIntOrNull()?.takeIf { it in 1..65535 && it != 515 } ?: 9110
        b.tilHost.error = if (host.isNotEmpty() && !IP.matches(host)) getString(R.string.lb_host_bad) else null
        if (host.isEmpty() || IP.matches(host)) repo.setHost(host, port)
    }

    private fun renderStatus() {
        val bb = _b ?: return
        val s: LabelState = repo.state.value
        val i = s.info
        val cloudOk = !s.lpd && s.cloud && ServiceLocator.shareStore.isCloudReady
        if (s.lpd) {
            bb.tvStatus.text = when {
                s.host.isBlank() -> getString(R.string.lb_st_none)
                i != null && s.infoError.isEmpty() -> getString(R.string.lb_st_lpd_ok, s.queue, s.host)
                s.infoError.isNotEmpty() -> getString(R.string.lb_st_offline, s.host)
                else -> getString(R.string.lb_st_lpd_unknown, s.queue, s.host)
            }
            val sub = ArrayList<String>()
            if (s.infoError.isNotEmpty()) sub += getString(R.string.lb_st_lpd_err, s.infoError.take(80))
            else i?.text?.takeIf { it.isNotBlank() }?.let { sub += it }
            bb.tvStatusSub.text = sub.joinToString("\n")
            bb.tvStatusSub.show(sub.isNotEmpty())
            val (w, h) = repo.sizePx()
            bb.tvSize.text = getString(R.string.lb_size_manual, w, h)
            return
        }
        bb.tvStatus.text = when {
            s.host.isNotBlank() && i != null && s.infoError.isEmpty() -> getString(R.string.lb_st_ok, i.printer.ifBlank { "?" })
            s.host.isNotBlank() && s.infoError.isNotEmpty() -> getString(R.string.lb_st_offline, s.host)
            s.host.isBlank() && cloudOk && i != null -> getString(R.string.lb_st_cloud, i.printer.ifBlank { "?" })
            s.host.isBlank() && cloudOk -> getString(R.string.lb_st_cloud_unknown)
            else -> getString(R.string.lb_st_none)
        }
        val sub = ArrayList<String>()
        if (i != null) {
            if (i.host.isNotBlank()) sub += getString(R.string.lb_st_pc, i.host)
            if (i.wMm > 0 && i.hMm > 0) sub += getString(R.string.lb_st_paper, Math.round(i.wMm).toInt(), Math.round(i.hMm).toInt(), i.dpi)
            if (i.at > 0) sub += getString(R.string.lb_st_seen, formatDateTime(i.at))
            if (i.text.isNotBlank()) sub += i.text
        }
        if (s.infoError.isNotEmpty() && s.host.isNotBlank()) sub += getString(R.string.lb_st_err, s.infoError.take(80))
        if (s.host.isNotBlank() && s.infoError.isNotEmpty() && cloudOk) sub += getString(R.string.lb_printer_cloud_fallback)
        bb.tvStatusSub.text = sub.joinToString("\n")
        bb.tvStatusSub.show(sub.isNotEmpty())
        val (w, h) = repo.sizePx()
        val fromPc = i != null && i.wPx in 80..3000 && i.hPx in 60..3000
        bb.tvSize.text = if (fromPc) getString(R.string.lb_size_from_pc, w, h) else getString(R.string.lb_size_manual, w, h)
    }

    private fun setBusy(on: Boolean) {
        busy = on
        val bb = _b ?: return
        bb.progress.showInvisible(on)
        bb.btnFind.isEnabled = !on; bb.btnCheck.isEnabled = !on; bb.btnTest.isEnabled = !on
    }

    private fun check() {
        if (busy) return
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { repo.refreshInfo() }
            setBusy(false)
            renderStatus()
        }
    }

    private fun find() {
        if (busy) return
        setBusy(true)
        b.foundList.removeAllViews()
        viewLifecycleOwner.lifecycleScope.launch {
            val found = runCatching { repo.discover() }.getOrDefault(emptyList())
            setBusy(false)
            val bb = _b ?: return@launch
            if (found.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lb_find_pc)
                    .setMessage(if (repo.state.value.lpd) R.string.lb_find_none_lpd else R.string.lb_find_none)
                    .setPositiveButton(R.string.close, null).show()
                return@launch
            }
            if (found.size == 1) { use(found[0].ip, found[0].port); return@launch }
            for (pc in found) {
                bb.foundList.addView(
                    MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = if (repo.state.value.lpd) getString(R.string.lb_found_lpd, pc.ip)
                        else getString(R.string.lb_found_item, pc.host.ifBlank { pc.ip }, pc.ip, pc.printer.ifBlank { "?" })
                        isAllCaps = false
                        setOnClickListener { use(pc.ip, pc.port) }
                    },
                )
            }
        }
    }

    private fun use(ip: String, port: Int) {
        val bb = _b ?: return
        bb.foundList.removeAllViews()
        bb.edtHost.setText(ip)
        if (repo.state.value.lpd) {
            repo.setQueue(bb.edtQueue.text?.toString().orEmpty())
            repo.setHost(ip, repo.state.value.port)
        } else {
            bb.edtPort.setText(port.toString())
            repo.setHost(ip, port)
        }
        check()
    }

    private fun test() {
        if (busy) return
        val app = requireContext().applicationContext
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = repo.printTest(getString(R.string.lb_test_name))
                Toast.makeText(
                    app,
                    when (r) {
                        is LabelPrinterClient.Sent.Printed -> app.getString(R.string.lb_printed, r.count, r.printer.ifBlank { "Xprinter" })
                        is LabelPrinterClient.Sent.Queued -> app.getString(R.string.lb_queued, r.count)
                    },
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = requireContext().labelErrorText(e)
                if (_b != null) MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lb_print_fail).setMessage(msg).setPositiveButton(R.string.close, null).show()
            } finally {
                setBusy(false)
                renderStatus()
            }
        }
    }

    /** Kompyuterdagi forwarder.ini: bulut sozlangan bo'lsa to'liq (chek + yorliq), bo'lmasa faqat [labels]. */
    private fun showIni() {
        val ini = if (ServiceLocator.shareStore.isCloudReady) ServiceLocator.share.forwarderIni() else repo.iniForPc()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.b_pc_settings).setMessage(R.string.lb_ini_text)
            .setPositiveButton(R.string.share_send) { _, _ -> ImportUi.shareText(requireContext(), ini) }
            .setNegativeButton(R.string.close, null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private val IP = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag("label_printer") != null) return
            LabelPrinterSheet().show(fm, "label_printer")
        }
    }
}
