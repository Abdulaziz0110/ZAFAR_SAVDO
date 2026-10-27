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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SettingsViewModel
    private var rendering = false

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
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
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            exportLauncher.launch("dokon_zaxira_$stamp.zip")
        }
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

        launchOnStarted { vm.state.collect { render(it) } }
        launchOnStarted {
            vm.events.collect { e ->
                when (e) {
                    SettingsEvent.BackupDone -> snack(R.string.backup_done)
                    SettingsEvent.BackupFailed -> snack(R.string.backup_failed)
                    SettingsEvent.RestoreDone -> snack(R.string.restore_done)
                    SettingsEvent.RestoreFailed -> snack(R.string.restore_failed)
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

    private fun render(s: SettingsState) {
        rendering = true
        binding.tvAgent.text = s.agentName
        binding.tvPhone.text = s.phone
        binding.tvServer.text = s.server.ifEmpty { getString(R.string.settings_server_none) }
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
