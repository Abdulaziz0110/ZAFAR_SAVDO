package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.UiLanguage
import com.ilxomjon.dur_novvot_dokon.data.UiTheme
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentSettingsBinding
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.viewModel
import com.ilxomjon.dur_novvot_dokon.ui.diag.DiagnosticsSheet
import com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxFragment
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SettingsViewModel
    private var rendering = false

    /** Zaxira paroli faqat shu ekran ochiq turganda xotirada; hech qayerga saqlanmaydi. */
    private var exportPassword: CharArray? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri, exportPassword)
        exportPassword = null
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.preview(uri, null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel {
            SettingsViewModel(ServiceLocator.auth, ServiceLocator.backup, ServiceLocator.appPrefs, ServiceLocator.catalog)
        }
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        binding.btnServer.setOnClickListener { navigator().push(ServerSettingsFragment()) }
        binding.btnBarcodes.setOnClickListener { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodesFragment()) }
        binding.btnLabels.setOnClickListener { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.labels.LabelsFragment()) }
        binding.btnShare.setOnClickListener { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.share.ShareFragment()) }
        binding.btnExtras.setOnClickListener { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.extras.ExtrasFragment()) }
        binding.btnRemoteHelp.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.remote_help_title).setMessage(R.string.remote_help_text)
                .setPositiveButton(R.string.close, null).show()
        }
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_logout_title)
                .setMessage(R.string.settings_logout_message)
                .setPositiveButton(R.string.settings_logout) { _, _ -> vm.logout() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.toggleLanguage.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            vm.setLanguage(if (id == R.id.btnCyrillic) UiLanguage.CYRILLIC else UiLanguage.LATIN)
        }
        binding.toggleTheme.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || rendering) return@addOnButtonCheckedListener
            vm.setTheme(
                when (id) {
                    R.id.btnThemeLight -> UiTheme.LIGHT
                    R.id.btnThemeDark -> UiTheme.DARK
                    else -> UiTheme.SYSTEM
                },
            )
        }
        for (slot in 0..1) {
            childFragmentManager.setFragmentResultListener("fav_$slot", viewLifecycleOwner) { _, bundle ->
                val id = bundle.getString(StorePickerSheet.KEY_ID).orEmpty()
                if (id.isNotEmpty()) vm.setFavorite(slot, id)
                com.ilxomjon.dur_novvot_dokon.ui.widget.QuickWidget.refresh(requireContext().applicationContext)
            }
        }
        binding.btnFavPick0.setOnClickListener { StorePickerSheet.show(childFragmentManager, "fav_0") }
        binding.btnFavPick1.setOnClickListener { StorePickerSheet.show(childFragmentManager, "fav_1") }
        binding.btnFavClear0.setOnClickListener { vm.clearFavorite(0) }
        binding.btnFavClear1.setOnClickListener { vm.clearFavorite(1) }
        binding.switchKeepScreen.setOnCheckedChangeListener { _, on ->
            if (!rendering) {
                vm.setKeepScreen(on)
                if (on) requireActivity().window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else requireActivity().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        binding.btnExport.setOnClickListener {
            askPassword(R.string.backup_password_title, R.string.backup_password_hint, optional = true) { pw ->
                exportPassword = pw
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                exportLauncher.launch("dokon_zaxira_$stamp.zip")
            }
        }
        binding.btnDiagnostics.setOnClickListener { DiagnosticsSheet.show(childFragmentManager) }
        binding.btnQueue.setOnClickListener { navigator().push(OutboxFragment()) }
        val p = vm.prefsRef
        bindFlag(binding.swStoreCard, p.fStoreCard) { p.fStoreCard = it }
        bindFlag(binding.swSmart, p.fSmartButton) { p.fSmartButton = it }
        bindFlag(binding.swExplain, p.fExplainSuggest) { p.fExplainSuggest = it }
        bindFlag(binding.swChecker, p.fCartChecker) { p.fCartChecker = it }
        bindFlag(binding.swMultiCart, p.fMultiCart) { p.fMultiCart = it }
        bindFlag(binding.swDupPayment, p.fDuplicatePaymentGuard) { p.fDuplicatePaymentGuard = it }
        bindFlag(binding.swKpi, p.fKpi) { p.fKpi = it; renderKpi() }
        renderKpi()
        binding.btnImport.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.restore_title)
                .setMessage(R.string.restore_message)
                .setPositiveButton(R.string.settings_import) { _, _ -> importLauncher.launch(arrayOf("*/*")) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        val version = runCatching {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrNull().orEmpty()
        binding.tvVersion.text = getString(R.string.settings_version, version)
        simplify()
        // Sirli menyu: versiya yozuvini uzoq bosish
        binding.tvVersion.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            navigator().push(AgentSecretFragment())
            true
        }
        com.ilxomjon.dur_novvot_dokon.ui.share.Tour.show(this, com.ilxomjon.dur_novvot_dokon.ui.share.Tour.SETTINGS)

        launchOnStarted { vm.state.collect { render(it) } }
        launchOnStarted {
            vm.events.collect { e ->
                when (e) {
                    SettingsEvent.BackupDone -> snack(R.string.backup_done)
                    is SettingsEvent.BackupFailed -> snackText(getString(R.string.backup_failed) + "\n" + backupError(e.cause))
                    is SettingsEvent.RestoreDone -> {
                        snackText(getString(R.string.restore_done) + if (e.needsCheck > 0) "\n" + getString(R.string.restore_needs_check, e.needsCheck) else "")
                        if (ServiceLocator.backup.lastRestoredSettings) BackupUi.askRestart(requireActivity())
                    }
                    is SettingsEvent.RestoreFailed -> snackText(getString(R.string.restore_failed) + "\n" + backupError(e.cause))
                    is SettingsEvent.NeedPassword -> askPassword(R.string.restore_password_title, R.string.restore_password_hint, optional = false) { pw -> vm.preview(e.uri, pw) }
                    is SettingsEvent.Preview -> showPreview(e)
                    SettingsEvent.LoggedOut -> navigator().onLoggedOut()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun snack(res: Int) = Snackbar.make(binding.root, res, Snackbar.LENGTH_LONG).show()
    private fun snackText(t: String) = Snackbar.make(binding.root, t, Snackbar.LENGTH_LONG).show()

    /**
     * v3.2: "Avtomatik yangilash va zaxira" bandi (hammada); Agent'da kam ishlatiladiganlar (shtrix kodlar, qulayliklar,
     * masofadan yordam, diagnostika, tajribaviy sozlamalar) "Ko'proq sozlamalar" ostida yashirin.
     */
    private fun simplify() {
        val column = binding.tvVersion.parent as? ViewGroup ?: return
        fun top(v: View): View? {
            var cur: View = v
            while (true) {
                val p = cur.parent as? View ?: return null
                if (p === column) return cur
                cur = p
            }
        }
        val ctx = requireContext()
        val first = top(binding.btnServer) ?: return
        if (column.findViewWithTag<View>("au_card") == null) {
            val (card, col) = uz.imsoft.shared.android.Ui.card(ctx, 0)
            card.tag = "au_card"
            col.addView(uz.imsoft.shared.android.Ui.item(ctx, getString(R.string.au_title), getString(R.string.au_sub), icon = R.drawable.ic_download) { navigator().push(AutoUpdateFragment()) })
            column.addView(card, column.indexOfChild(first), ViewGroup.MarginLayoutParams(first.layoutParams).apply {
                (first.layoutParams as? ViewGroup.MarginLayoutParams)?.let { setMargins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin) }
            })
        }
        if (com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin) return
        val advanced = listOfNotNull(top(binding.btnBarcodes), top(binding.btnExtras), top(binding.btnRemoteHelp), top(binding.btnDiagnostics), top(binding.swSmart))
            .distinct().filter { it !== top(binding.btnServer) && it !== top(binding.btnQueue) && it !== top(binding.btnExport) }
        if (advanced.isEmpty()) return
        var open = false
        advanced.forEach { it.show(false) }
        val more = uz.imsoft.shared.android.Ui.button(ctx, getString(R.string.st_more), outlined = true, top = 12, icon = R.drawable.ic_expand_more) { b ->
            open = !open
            advanced.forEach { it.show(open) }
            (b as? com.google.android.material.button.MaterialButton)?.text = getString(if (open) R.string.st_less else R.string.st_more)
        }
        column.addView(more, column.indexOfChild(binding.tvVersion))
    }

    private fun bindFlag(sw: com.google.android.material.materialswitch.MaterialSwitch, value: Boolean, set: (Boolean) -> Unit) {
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, on -> set(on) }
    }

    private fun renderKpi() {
        val on = vm.prefsRef.fKpi
        binding.tvKpi.show(on)
        if (!on) return
        val k = ServiceLocator.kpi.summary()
        binding.tvKpi.text = if (k.count == 0) getString(R.string.kpi_empty)
        else getString(R.string.kpi_summary, k.count, k.medianSeconds.toInt(), k.medianTaps.toInt())
    }

    private fun backupError(t: Throwable): String = when ((t as? BackupException)?.kind) {
        BackupException.Kind.WRONG_PASSWORD -> getString(R.string.backup_err_password)
        BackupException.Kind.NOT_BACKUP -> getString(R.string.backup_err_not_backup)
        BackupException.Kind.TOO_NEW -> getString(R.string.backup_err_too_new)
        BackupException.Kind.CORRUPT -> getString(R.string.backup_err_corrupt)
        BackupException.Kind.DISK_FULL -> getString(R.string.backup_err_disk_full)
        else -> t.message.orEmpty().take(120)
    }

    /** Parol so'raydi. [optional] bo'lsa bo'sh qoldirish mumkin (shifrsiz). */
    private fun askPassword(title: Int, hint: Int, optional: Boolean, done: (CharArray?) -> Unit) {
        val ctx = requireContext()
        val til = com.google.android.material.textfield.TextInputLayout(ctx).apply {
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            setPadding(48, 16, 48, 0)
        }
        val edt = com.google.android.material.textfield.TextInputEditText(til.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHint(hint)
        }
        til.addView(edt)
        MaterialAlertDialogBuilder(ctx).setTitle(title).setView(til)
            .setPositiveButton(R.string.ok) { _, _ ->
                val t = edt.text?.toString().orEmpty()
                if (t.isEmpty() && !optional) return@setPositiveButton
                done(if (t.isEmpty()) null else t.toCharArray())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Tiklashdan OLDIN: nima almashadi, nechta yuborilmagan qator "Tekshirish kerak"ka tushadi. */
    private fun showPreview(e: SettingsEvent.Preview) {
        val p = e.preview
        if (p.tooNew) { snackText(getString(R.string.backup_err_too_new)); return }
        val msg = BackupUi.previewText(requireContext(), p)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.restore_title).setMessage(msg)
            .setPositiveButton(R.string.settings_import) { _, _ -> vm.import(e.uri, e.password) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun render(s: SettingsState) {
        rendering = true
        binding.tvAgent.text = s.agentName
        binding.tvPhone.text = s.phone
        binding.tvServer.text = if (ServiceLocator.isRelay) getString(R.string.srv_mode_admin) else s.server.ifEmpty { getString(R.string.settings_server_none) }
        binding.toggleLanguage.check(if (s.language == UiLanguage.CYRILLIC) R.id.btnCyrillic else R.id.btnLatin)
        binding.toggleTheme.check(
            when (s.theme) {
                UiTheme.LIGHT -> R.id.btnThemeLight
                UiTheme.DARK -> R.id.btnThemeDark
                UiTheme.SYSTEM -> R.id.btnThemeSystem
            },
        )
        if (binding.switchKeepScreen.isChecked != s.keepScreen) binding.switchKeepScreen.isChecked = s.keepScreen
        binding.btnFavPick0.text = s.favorites.getOrNull(0)?.name ?: getString(R.string.settings_fav_empty)
        binding.btnFavPick1.text = s.favorites.getOrNull(1)?.name ?: getString(R.string.settings_fav_empty)
        binding.btnFavClear0.show(s.favorites.isNotEmpty())
        binding.btnFavClear1.show(s.favorites.size > 1)
        binding.btnExport.isEnabled = !s.busy
        binding.btnImport.isEnabled = !s.busy
        rendering = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
