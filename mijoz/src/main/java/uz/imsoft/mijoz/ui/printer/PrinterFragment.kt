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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
            render(PrinterHub.state.value)
        }
        // v5.3: sinov cheki servis yoqilmagan bo'lsa ham chiqadi (printer ishlashini darhol ko'rish uchun)
        b.btnTest.setOnClickListener {
            if (PrinterHub.state.value.running) { PrinterHub.commands.trySend(PrinterHub.Command.TestPage); PrinterHub.nudge() } else testDirect()
        }
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
        launchOnStarted { PrinterHub.state.collect { render(it); refreshPending(it.queue) } }
    }

    /** Printer ulandi/uzildi/ruxsat berildi — ekran darhol yangilanadi (servis ishlamayotgan bo'lsa ham). */
    private val usbReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            if (_b != null) render(PrinterHub.state.value)
            PrinterHub.nudge()
        }
    }

    override fun onStart() {
        super.onStart()
        val f = android.content.IntentFilter().apply {
            addAction(UsbPrinter.ACTION_PERMISSION)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        androidx.core.content.ContextCompat.registerReceiver(requireContext(), usbReceiver, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        if (_b != null) render(PrinterHub.state.value)
    }

    override fun onStop() {
        runCatching { requireContext().unregisterReceiver(usbReceiver) }
        super.onStop()
    }

    private fun testDirect() {
        val ctx = requireContext().applicationContext
        when (UsbPrinter.ready(ctx)) {
            UsbPrinter.Ready.NO_PERMISSION -> { UsbPrinter.requestPermission(ctx); return }
            UsbPrinter.Ready.OK -> Unit
            else -> { com.google.android.material.snackbar.Snackbar.make(b.root, R.string.pr_ready_none, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show(); return }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val lines = listOf("IMSOFT Mijoz", "Sinov cheki / Test", java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US).format(java.util.Date()), "OK")
            val err = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { UsbPrinter.send(ctx, uz.imsoft.mijoz.printer.EscPos.testPage(lines)); null } catch (e: UsbPrinter.SendException) { e.message ?: "?" }
            }
            PrinterHub.log(err?.let { "! $it" } ?: getString(R.string.pr_log_test_ok))
            PrinterHub.update { it.copy(lastError = err.orEmpty()) }
        }
    }

    private var pendingFor = Int.MIN_VALUE
    private var pendingJob: kotlinx.coroutines.Job? = null

    /** Navbatdagi cheklar ro'yxati (navbat soni o'zgarganda yangilanadi). */
    private fun refreshPending(queue: Int) {
        if (queue == pendingFor) return
        pendingFor = queue
        pendingJob?.cancel()
        pendingJob = viewLifecycleOwner.lifecycleScope.launch {
            val list = runCatching { uz.imsoft.mijoz.data.SaleReceipts.pending() }.getOrNull()
            val bb = _b ?: return@launch
            bb.tvPending.text = when {
                list == null -> getString(R.string.sr_list_offline)
                list.isEmpty() -> getString(R.string.sr_list_empty)
                else -> list.take(30).joinToString("\n") { p ->
                    "• " + (if (p.at > 0) Fmt.dateTime(p.at) + " · " else "") + p.text.ifBlank { getString(R.string.sr_from_pc) }
                } + if (list.size > 30) "\n…" else ""
            }
        }
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
        // Printer holati shu yerda, hozir tekshiriladi — servis o'chiq bo'lsa ham to'g'ri ko'rinadi
        val ready = UsbPrinter.ready(requireContext())
        UsbPrinter.name(requireContext()).takeIf { it.isNotEmpty() }?.let { lines += getString(R.string.pr_found, it) }
        lines += getString(
            when (ready) {
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
        bb.btnTest.isEnabled = true
        bb.btnCheck.isEnabled = true
        bb.cardUnknown.show(s.unknownKey.isNotEmpty())
        bb.tvLog.text = s.log.joinToString("\n")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
