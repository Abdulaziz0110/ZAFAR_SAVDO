package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentServerSettingsBinding
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import com.ilxomjon.dur_novvot_dokon.ui.viewModel

class ServerSettingsFragment : Fragment() {
    private var _binding: FragmentServerSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: ServerSettingsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentServerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { ServerSettingsViewModel(ServiceLocator.auth, ServiceLocator.secure) }
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        // v3.0: ulanish turi faqat Agent'da (Admin har doim serverga o'zi ulanadi)
        val agent = !com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin
        binding.tvModeTitle.show(agent)
        binding.tgMode.show(agent)
        if (savedInstanceState == null) binding.edtRelayCode.setText(vm.relayCode)
        binding.tgMode.check(if (agent && vm.relayMode) R.id.btnModeAdmin else R.id.btnModeServer)
        renderMode()
        binding.tgMode.addOnButtonCheckedListener { _, _, checked -> if (checked) renderMode() }

        if (savedInstanceState == null) {
            val c = vm.current
            binding.edtHost.setText(c.host); binding.edtDb.setText(c.database)
            binding.edtLogin.setText(c.login)
            // Saqlangan parol HECH QACHON ekranga chiqarilmaydi (Login ekranidan ham shu sahifaga kirish mumkin).
            binding.edtPassword.setText("")
            binding.edtRemoteHost.setText(c.remoteHost)
        }
        binding.tilPassword.helperText = if (vm.hasPassword) getString(R.string.server_password_saved) else null
        binding.btnTest.setOnClickListener {
            if (relaySelected()) readRelay()?.let { vm.testRelay(it) } else readConfig()?.let { vm.test(it) }
        }
        binding.btnSave.setOnClickListener {
            if (relaySelected()) readRelay()?.let { vm.saveRelay(it) } else readConfig()?.let { vm.save(it) }
        }

        launchOnStarted { vm.state.collect { render(it) } }
    }

    private fun relaySelected(): Boolean = binding.tgMode.visibility == View.VISIBLE && binding.tgMode.checkedButtonId == R.id.btnModeAdmin

    /** "Admin orqali": server maydonlari yashiriladi, Admin bergan kod so'raladi. */
    private fun renderMode() {
        val relay = relaySelected()
        binding.tvModeHint.show(relay)
        binding.tilRelayCode.show(relay)
        for (v in listOf(binding.tilHost, binding.tilDb, binding.tilLogin, binding.tilPassword, binding.tilRemoteHost)) v.show(!relay)
        binding.tvResult.show(false)
    }

    private fun readRelay(): com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.Config? {
        val c = com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec.decodeCode(binding.edtRelayCode.text?.toString().orEmpty())
        binding.tilRelayCode.error = if (c == null) getString(R.string.srv_relay_code_bad) else null
        return c
    }

    private fun readConfig(): ServerConfig? {
        val host = binding.edtHost.text?.toString().orEmpty().trim()
        val db = binding.edtDb.text?.toString().orEmpty().trim()
        val login = binding.edtLogin.text?.toString().orEmpty().trim()
        // Bo'sh qoldirilsa — saqlangan parol o'zgarmaydi (ekranga chiqmaydi, faqat ichkarida ishlatiladi).
        val password = binding.edtPassword.text?.toString().orEmpty().ifEmpty { vm.currentPasswordInternal() }
        val remote = binding.edtRemoteHost.text?.toString().orEmpty().trim()
        val empty = getString(R.string.server_error_empty)
        binding.tilHost.error = if (host.isEmpty()) empty else null
        binding.tilDb.error = if (db.isEmpty()) empty else null
        binding.tilLogin.error = if (login.isEmpty()) empty else null
        if (host.isEmpty() || db.isEmpty() || login.isEmpty()) return null
        return ServerConfig(host, db, login, password, remote)
    }

    private fun render(s: ServerSettingsState) {
        binding.progress.showInvisible(s.testing)
        binding.btnTest.isEnabled = !s.testing
        val msg = when {
            s.testOk == true && s.adminAt > 0 -> getString(R.string.srv_relay_ok, com.ilxomjon.dur_novvot_dokon.ui.formatDateTime(s.adminAt))
            s.testOk == true -> getString(R.string.server_test_ok_via, s.via)
            s.testError != null -> requireContext().describeError(s.testError)
            else -> null
        }
        binding.tvResult.text = msg
        binding.tvResult.show(msg != null)
        binding.tvResult.setTextColor(
            ContextCompat.getColor(requireContext(), if (s.testOk == true) R.color.brand_primary else R.color.brand_error),
        )
        if (s.saved) {
            Snackbar.make(binding.root, R.string.server_saved, Snackbar.LENGTH_SHORT).show()
            navigator().pop()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
