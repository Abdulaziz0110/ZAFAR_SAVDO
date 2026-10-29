package uz.imsoft.mijoz.ui.printer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.FragmentPrinterBinding
import uz.imsoft.mijoz.printer.PrintService
import uz.imsoft.mijoz.printer.PrinterHub
import uz.imsoft.mijoz.printer.PrinterState
import uz.imsoft.mijoz.printer.UsbPrinter
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.mijoz.ui.show

/** 2-filial chek printeri: servisni yoqish/o'chirish, holat, sinov cheki, jurnal. */
class PrinterFragment : Fragment() {
    private var _b: FragmentPrinterBinding? = null
    private val b get() = _b!!

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startService() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentPrinterBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnToggle.setOnClickListener {
            if (PrinterHub.state.value.running) PrintService.stop(requireContext())
            else if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else startService()
        }
        b.btnCheck.setOnClickListener {
            UsbPrinter.requestPermission(requireContext())
            PrinterHub.nudge()
        }
        b.btnTest.setOnClickListener { PrinterHub.commands.trySend(PrinterHub.Command.TestPage); PrinterHub.nudge() }
        b.btnReprint.setOnClickListener { PrinterHub.commands.trySend(PrinterHub.Command.ReprintUnknown); PrinterHub.nudge() }
        b.btnDrop.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.pr_drop).setMessage(R.string.pr_drop_msg)
                .setPositiveButton(R.string.ok) { _, _ -> PrinterHub.commands.trySend(PrinterHub.Command.DropUnknown); PrinterHub.nudge() }
                .setNegativeButton(R.string.cancel, null).show()
        }
        b.swAutostart.isChecked = Locator.prefs.autostart
        b.swAutostart.setOnCheckedChangeListener { _, on -> Locator.prefs.autostart = on }
        b.btnBattery.setOnClickListener { askBattery() }
        b.btnUsb.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.pr_usb_list)
                .setMessage(UsbPrinter.describeAll(requireContext())).setPositiveButton(R.string.close, null).show()
        }
        launchOnStarted { PrinterHub.state.collect { render(it) } }
    }

    private fun startService() {
        if (Locator.prefs.link?.queueKey.isNullOrEmpty()) {
            MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.pr_err_no_link).setPositiveButton(R.string.ok, null).show()
            return
        }
        PrintService.start(requireContext())
    }

    @SuppressLint("BatteryLife")
    private fun askBattery() {
        val ctx = requireContext()
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            MaterialAlertDialogBuilder(ctx).setMessage(R.string.pr_battery_ok).setPositiveButton(R.string.ok, null).show()
            return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
        }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    }

    private fun render(s: PrinterState) {
        val bb = _b ?: return
        val lines = ArrayList<String>()
        lines += getString(if (s.running) R.string.pr_st_running else R.string.pr_st_stopped)
        lines += getString(
            when (s.ready) {
                UsbPrinter.Ready.OK -> R.string.pr_ready_ok
                UsbPrinter.Ready.NO_PERMISSION -> R.string.pr_ready_perm
                UsbPrinter.Ready.NO_USB_HOST -> R.string.pr_ready_nohost
                UsbPrinter.Ready.NO_DEVICE -> R.string.pr_ready_none
            },
        )
        if (s.running) lines += if (s.online) getString(R.string.pr_queue, maxOf(s.queue, 0)) else getString(R.string.pr_offline)
        lines += getString(R.string.pr_printed, s.printedTotal, if (s.lastPrintAt > 0) Fmt.dateTime(s.lastPrintAt) else "—")
        if (s.lastError.isNotEmpty()) lines += "⚠ " + s.lastError
        bb.tvState.text = lines.joinToString("\n")
        bb.btnToggle.setText(if (s.running) R.string.pr_stop else R.string.pr_start)
        bb.btnTest.isEnabled = s.running
        bb.btnCheck.isEnabled = s.running
        bb.cardUnknown.show(s.unknownKey.isNotEmpty())
        bb.tvLog.text = s.log.joinToString("\n")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
