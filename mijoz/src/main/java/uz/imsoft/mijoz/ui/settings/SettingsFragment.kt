package uz.imsoft.mijoz.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.FragmentSettingsBinding
import uz.imsoft.mijoz.printer.PrintService
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.launchOnStarted

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private var rendering = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        uz.imsoft.mijoz.ui.tour.Tour.show(this, uz.imsoft.mijoz.ui.tour.Tour.SETTINGS)
        val p = Locator.prefs
        render()
        b.btnPaste.setText(R.string.lgn_login_other)
        b.btnPaste.setOnClickListener { (activity as? uz.imsoft.mijoz.ui.MainActivity)?.showLogin() }
        b.btnUnlink.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.link_remove).setMessage(R.string.link_remove_msg)
                .setPositiveButton(R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        PrintService.stop(requireContext())
                        Locator.data.unlink()
                        Locator.login.logout()
                        render()
                        (activity as? uz.imsoft.mijoz.ui.MainActivity)?.showLogin()
                    }
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
        onToggle(b.tgLang) { id -> p.language = if (id == R.id.langCyr) "cyr" else "lat"; Locator.applyUi() }
        onToggle(b.tgTheme) { id -> p.theme = when (id) { R.id.themeLight -> "light"; R.id.themeDark -> "dark"; else -> "system" }; Locator.applyUi() }
        onToggle(b.tgScale) { id ->
            p.uiScale = when (id) { R.id.scale90 -> 0.9f; R.id.scale110 -> 1.1f; R.id.scale120 -> 1.2f; else -> 1.0f }
            requireActivity().recreate()
        }
        onToggle(b.tgAuto) { id ->
            p.autoMinutes = when (id) { R.id.auto60 -> 60; R.id.auto120 -> 120; else -> 0 }
            uz.imsoft.mijoz.data.RefreshWorker.schedule(requireContext().applicationContext, p.autoMinutes)
        }
        b.swNotify.setOnCheckedChangeListener { _, on -> if (!rendering) { p.notifyNew = on; if (on) askNotify() } }
        onToggle(b.tgFont) { id ->
            p.fontScale = when (id) { R.id.font90 -> 0.9f; R.id.font115 -> 1.15f; else -> 1.0f }
            requireActivity().recreate()
        }
        b.swAutoRefresh.setOnCheckedChangeListener { _, on -> if (!rendering) p.autoRefresh = on }
        b.swFuzzy.setOnCheckedChangeListener { _, on -> if (!rendering) p.fuzzy = on }
        b.swPrinter.setOnCheckedChangeListener { _, on ->
            if (rendering) return@setOnCheckedChangeListener
            p.printerSection = on
            if (!on) PrintService.stop(requireContext())
            (activity as? MainActivity)?.updatePrinterTab()
        }
        onToggle(b.tgSkip) { id -> p.skipOlderDays = when (id) { R.id.skip1 -> 1; R.id.skip3 -> 3; R.id.skip7 -> 7; else -> 0 } }
        val version = runCatching { requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName }.getOrNull().orEmpty()
        b.tvAbout.text = getString(R.string.st_about_text, version)
        // Sirli menyu: versiya yozuvini uzoq bosish
        b.tvAbout.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            (activity as? uz.imsoft.mijoz.ui.MainActivity)?.push(MijozSecretFragment())
            true
        }
        launchOnStarted { Locator.data.state.collect { render() } }
    }

    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private fun askNotify() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun onToggle(g: MaterialButtonToggleGroup, block: (Int) -> Unit) {
        g.addOnButtonCheckedListener { _, id, checked -> if (checked && !rendering) block(id) }
    }

    private fun render() {
        val bb = _b ?: return
        val p = Locator.prefs
        rendering = true
        val link = p.link
        bb.tvLink.text = if (link == null) getString(R.string.link_none)
        else getString(R.string.link_info, link.clientName.ifEmpty { "—" }, if (link.queueKey.isNotEmpty()) getString(R.string.link_with_printer) else "") +
            if (p.loginPhone.isNotEmpty()) "\n" + getString(R.string.lgn_as, uz.imsoft.shared.Phone.pretty(p.loginPhone), p.memberName.ifBlank { "—" }) else 
        bb.btnUnlink.isEnabled = link != null
        bb.tgLang.check(if (p.language == "cyr") R.id.langCyr else R.id.langLat)
        bb.tgTheme.check(when (p.theme) { "light" -> R.id.themeLight; "dark" -> R.id.themeDark; else -> R.id.themeSystem })
        bb.tgScale.check(when (p.uiScale) { 0.9f -> R.id.scale90; 1.1f -> R.id.scale110; 1.2f -> R.id.scale120; else -> R.id.scale100 })
        bb.tgAuto.check(when (p.autoMinutes) { 60 -> R.id.auto60; 120 -> R.id.auto120; 0 -> R.id.auto0; else -> R.id.auto60 })
        bb.swNotify.isChecked = p.notifyNew
        bb.tgFont.check(when (p.fontScale) { 0.9f -> R.id.font90; 1.15f -> R.id.font115; else -> R.id.font100 })
        bb.swAutoRefresh.isChecked = p.autoRefresh
        bb.swFuzzy.isChecked = p.fuzzy
        bb.swPrinter.isChecked = p.printerSection
        bb.tgSkip.check(when (p.skipOlderDays) { 1 -> R.id.skip1; 3 -> R.id.skip3; 7 -> R.id.skip7; else -> R.id.skip0 })
        rendering = false
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) { uz.imsoft.mijoz.ui.tour.Tour.show(this, uz.imsoft.mijoz.ui.tour.Tour.SETTINGS) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
