package com.ilxomjon.dur_novvot_dokon.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.ui.settings.BackupUi
import kotlinx.coroutines.launch
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentLoginBinding
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.settings.ServerSettingsFragment
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.viewModel

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: LoginViewModel

    private val pickBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) BackupUi.askPassword(requireContext(), R.string.restore_password_title, R.string.restore_password_hint, optional = false) { pw -> transfer(uri, pw) }
    }

    /** Ko'rib chiqish -> tasdiq -> tiklash -> qayta ishga tushirish. */
    private fun transfer(uri: Uri, pw: CharArray?) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val p = runCatching { ServiceLocator.backup.preview(uri, pw) }.getOrElse { e -> toast(BackupUi.errorText(ctx, e)); return@launch }
            if (p.tooNew) { toast(getString(R.string.backup_err_too_new)); return@launch }
            if (!p.hasSettings) { toast(getString(R.string.login_transfer_no_settings)); return@launch }
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.restore_title).setMessage(BackupUi.previewText(ctx, p))
                .setPositiveButton(R.string.settings_import) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        _binding?.progress?.show(true)
                        val r = runCatching { ServiceLocator.backup.import(uri, pw) }
                        _binding?.progress?.show(false)
                        r.onSuccess { activity?.let { BackupUi.askRestart(it) } }
                            .onFailure { e -> toast(getString(R.string.restore_failed) + "\n" + BackupUi.errorText(ctx, e)) }
                    }
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun toast(t: String) {
        val b = _binding ?: return
        Snackbar.make(b.root, t, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { LoginViewModel(ServiceLocator.auth) }

        binding.edtPhone.onTextChanged { binding.tilPhone.error = null }
        binding.edtPassword.onTextChanged { binding.tilPassword.error = null }
        binding.edtPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        binding.btnLogin.setOnClickListener { submit() }
        binding.btnServer.setOnClickListener { navigator().push(ServerSettingsFragment()) }
        // Admin: Agent ilovadagi parolli zaxiradan (ma'lumot + sozlamalar) bir martada ko'chirish
        binding.btnTransfer.show(com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin)
        binding.btnTransfer.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.login_transfer).setMessage(R.string.login_transfer_msg)
                .setPositiveButton(R.string.login_transfer_pick) { _, _ -> pickBackup.launch(arrayOf("*/*")) }
                .setNegativeButton(R.string.cancel, null).show()
        }

        launchOnStarted {
            vm.state.collect { render(it) }
        }
    }

    private fun submit() {
        vm.login(binding.edtPhone.text?.toString().orEmpty(), binding.edtPassword.text?.toString().orEmpty())
    }

    private fun render(s: LoginState) {
        binding.progress.show(s.loading)
        binding.btnLogin.isEnabled = !s.loading
        binding.tilPhone.error = if (s.error is LoginError.BadPhone) getString(R.string.login_error_phone) else null
        binding.tilPassword.error = if (s.error is LoginError.BadPassword) getString(R.string.login_error_password) else null
        val message = when (val e = s.error) {
            LoginError.Credentials -> getString(R.string.login_error_credentials)
            LoginError.ServerMissing -> getString(R.string.login_server_missing)
            is LoginError.Failure -> requireContext().describeError(e.cause)
            else -> null
        }
        binding.tvError.text = message
        binding.tvError.show(message != null)
        if (s.loggedIn) {
            // v5.3: rol kirishdan aniqlanadi (egasi — Admin, boshqalar — xodim); o'zgarsa ilova yangi rolda qayta ochiladi
            val ctx = requireContext()
            if (com.ilxomjon.dur_novvot_dokon.core.AppRole.onLogin(ctx, binding.edtPhone.text?.toString().orEmpty())) {
                com.ilxomjon.dur_novvot_dokon.core.AppRole.restart(ctx)
            } else navigator().onLoggedIn()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
