package com.ilxomjon.dur_novvot_dokon.ui.extras

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.extras.Extras
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentExtrasBinding
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.launch

/** Sozlamalar ▸ Qulayliklar: ekran, savdo ekrani, qidiruv, avto-yangilash, PIN, zaxira, eslatmalar (har biri o'chiriladi). */
class ExtrasFragment : Fragment() {
    private var _binding: FragmentExtrasBinding? = null
    private val binding get() = _binding!!
    private val prefs get() = ServiceLocator.appPrefs
    private var rendering = false
    private var pendingNotify: (() -> Unit)? = null

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingNotify?.invoke() else { snack(R.string.ex_notify_denied); render() }
        pendingNotify = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExtrasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        render()

        onToggle(binding.tgScale) { id ->
            prefs.uiScale = when (id) { R.id.scale90 -> 0.9f; R.id.scale110 -> 1.1f; R.id.scale120 -> 1.2f; else -> 1.0f }
            requireActivity().recreate()
        }
        onToggle(binding.tgFont) { id ->
            prefs.fontScale = when (id) { R.id.font90 -> 0.9f; R.id.font115 -> 1.15f; else -> 1.0f }
            requireActivity().recreate()
        }
        onSwitch(binding.swFuzzy) { prefs.fFuzzySearch = it }
        onSwitch(binding.swLayout) { prefs.fLayoutFix = it }
        onToggle(binding.tgSort) { id -> prefs.productSort = when (id) { R.id.sortName -> "name"; R.id.sortPopular -> "popular"; else -> "server" } }
        onToggle(binding.tgAdd) { id -> prefs.addMode = if (id == R.id.addAsk) "ask" else "plus" }
        onSwitch(binding.swHaptics) { prefs.fHaptics = it }
        onSwitch(binding.swCostSwitch) { prefs.fCostSwitch = it }
        onToggle(binding.tgSync) { id -> prefs.autoSyncHours = when (id) { R.id.sync1 -> 1; R.id.sync3 -> 3; R.id.sync6 -> 6; else -> 0 } }
        onSwitch(binding.swPin) { on -> if (on) askNewPin() else confirmRemovePin() }
        binding.btnPinChange.setOnClickListener { askNewPin() }
        onToggle(binding.tgLock) { id -> prefs.lockAfterMin = when (id) { R.id.lock0 -> 0; R.id.lock5 -> 5; R.id.lock15 -> 15; else -> 1 } }
        onSwitch(binding.swAutoBackup) { on ->
            prefs.fAutoBackup = on
            Extras.scheduleBackup(requireContext().applicationContext, on)
        }
        binding.btnBackupNow.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = runCatching { Extras.backupNow(requireContext().applicationContext) }.isSuccess
                snack(if (ok) R.string.backup_done else R.string.backup_failed)
            }
        }
        binding.btnBackupList.setOnClickListener { showBackups() }
        onSwitch(binding.swReminder) { on ->
            if (!on) { prefs.reminderTime = ""; Extras.scheduleReminder(requireContext().applicationContext, ""); render() }
            else withNotifyPermission { pickReminderTime(prefs.reminderTime.ifEmpty { "19:00" }) }
        }
        binding.btnReminderTime.setOnClickListener { withNotifyPermission { pickReminderTime(prefs.reminderTime.ifEmpty { "19:00" }) } }
        onSwitch(binding.swQueueAlert) { on ->
            if (!on) prefs.fQueueAlert = false
            else withNotifyPermission { prefs.fQueueAlert = true; render() }
        }
    }

    private fun render() {
        val b = _binding ?: return
        rendering = true
        b.tgScale.check(when (prefs.uiScale) { 0.9f -> R.id.scale90; 1.1f -> R.id.scale110; 1.2f -> R.id.scale120; else -> R.id.scale100 })
        b.tgFont.check(when (prefs.fontScale) { 0.9f -> R.id.font90; 1.15f -> R.id.font115; else -> R.id.font100 })
        b.swFuzzy.isChecked = prefs.fFuzzySearch
        b.swLayout.isChecked = prefs.fLayoutFix
        b.tgSort.check(when (prefs.productSort) { "name" -> R.id.sortName; "popular" -> R.id.sortPopular; else -> R.id.sortServer })
        b.tgAdd.check(if (prefs.addMode == "ask") R.id.addAsk else R.id.addPlus)
        b.swHaptics.isChecked = prefs.fHaptics
        b.swCostSwitch.isChecked = prefs.fCostSwitch
        b.tgSync.check(when (prefs.autoSyncHours) { 1 -> R.id.sync1; 3 -> R.id.sync3; 6 -> R.id.sync6; else -> R.id.sync0 })
        val pinOn = prefs.pinHash.isNotEmpty()
        b.swPin.isChecked = pinOn
        b.btnPinChange.isEnabled = pinOn
        b.tgLock.check(when (prefs.lockAfterMin) { 0 -> R.id.lock0; 5 -> R.id.lock5; 15 -> R.id.lock15; else -> R.id.lock1 })
        b.swAutoBackup.isChecked = prefs.fAutoBackup
        b.swReminder.isChecked = prefs.reminderTime.isNotEmpty()
        b.btnReminderTime.isEnabled = prefs.reminderTime.isNotEmpty()
        b.btnReminderTime.text = getString(R.string.ex_reminder_time_v, prefs.reminderTime.ifEmpty { "—" })
        b.swQueueAlert.isChecked = prefs.fQueueAlert
        rendering = false
    }

    private fun onToggle(g: MaterialButtonToggleGroup, block: (Int) -> Unit) {
        g.addOnButtonCheckedListener { _, id, checked -> if (checked && !rendering) block(id) }
    }

    private fun onSwitch(sw: MaterialSwitch, block: (Boolean) -> Unit) {
        sw.setOnCheckedChangeListener { _, on -> if (!rendering) block(on) }
    }

    private fun withNotifyPermission(action: () -> Unit) {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= 33 && !Extras.canNotify(ctx)) {
            pendingNotify = action
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }

    private fun pickReminderTime(current: String) {
        val (h, m) = Extras.parseTime(current) ?: (19 to 0)
        val picker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(h).setMinute(m)
            .setTitleText(R.string.ex_reminder_time).build()
        picker.addOnPositiveButtonClickListener {
            val t = "%02d:%02d".format(picker.hour, picker.minute)
            prefs.reminderTime = t
            Extras.scheduleReminder(requireContext().applicationContext, t)
            render()
        }
        picker.addOnNegativeButtonClickListener { render() }
        picker.addOnCancelListener { render() }
        picker.show(childFragmentManager, "time")
    }

    private fun pinField(hint: Int): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(requireContext()).apply { setPadding(48, 8, 48, 0) }
        val edt = TextInputEditText(til.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setHint(hint)
        }
        til.addView(edt)
        return til to edt
    }

    private fun askNewPin() {
        val box = android.widget.LinearLayout(requireContext()).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val (t1, e1) = pinField(R.string.ex_pin_new)
        val (t2, e2) = pinField(R.string.ex_pin_repeat)
        box.addView(t1); box.addView(t2)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ex_pin_change).setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val a = e1.text?.toString().orEmpty()
                val b = e2.text?.toString().orEmpty()
                when {
                    a.length < 4 -> snack(R.string.ex_pin_short)
                    a != b -> snack(R.string.ex_pin_mismatch)
                    else -> { Extras.setPin(prefs, a); snack(R.string.ex_pin_set) }
                }
                render()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> render() }
            .setOnCancelListener { render() }
            .show()
    }

    private fun confirmRemovePin() {
        val (t, e) = pinField(R.string.ex_pin_current)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ex_pin_off).setView(t)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (Extras.checkPin(prefs, e.text?.toString().orEmpty())) { Extras.setPin(prefs, null); snack(R.string.ex_pin_removed) }
                else snack(R.string.pin_wrong)
                render()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> render() }
            .setOnCancelListener { render() }
            .show()
    }

    private fun showBackups() {
        val files = Extras.backups(requireContext())
        if (files.isEmpty()) { snack(R.string.ex_backup_none); return }
        val labels = files.map { getString(R.string.ex_backup_item, formatDateTime(it.lastModified()), (it.length() / 1024).toInt()) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ex_backup_list)
            .setItems(labels) { _, i ->
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.restore_title).setMessage(R.string.restore_message)
                    .setPositiveButton(R.string.settings_import) { _, _ -> restore(Uri.fromFile(files[i])) }
                    .setNegativeButton(R.string.cancel, null).show()
            }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun restore(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val n = ServiceLocator.backup.import(uri, null)
                Snackbar.make(binding.root, getString(R.string.restore_done) + if (n > 0) "\n" + getString(R.string.restore_needs_check, n) else "", Snackbar.LENGTH_LONG).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                snack(R.string.restore_failed)
            }
        }
    }

    private fun snack(res: Int) { _binding?.let { Snackbar.make(it.root, res, Snackbar.LENGTH_LONG).show() } }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
