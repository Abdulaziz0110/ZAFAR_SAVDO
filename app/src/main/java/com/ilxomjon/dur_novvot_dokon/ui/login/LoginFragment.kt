package com.ilxomjon.dur_novvot_dokon.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
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
        if (s.loggedIn) navigator().onLoggedIn()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
