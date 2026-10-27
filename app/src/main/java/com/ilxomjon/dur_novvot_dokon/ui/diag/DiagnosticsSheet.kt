package com.ilxomjon.dur_novvot_dokon.ui.diag

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.repo.Diagnosis
import com.ilxomjon.dur_novvot_dokon.databinding.SheetDiagnosticsBinding
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxFragment
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** "Nima uchun yuborilmayapti?" — bitta tugma bilan oddiy o'zbekcha javob. UI'ni bloklamaydi, yopilsa bekor bo'ladi. */
class DiagnosticsSheet : BottomSheetDialogFragment() {
    private var _binding: SheetDiagnosticsBinding? = null
    private val binding get() = _binding!!
    private var job: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnAgain.setOnClickListener { runCheck() }
        binding.btnQueue.setOnClickListener {
            val nav = navigator()
            dismissAllowingStateLoss()
            nav.push(OutboxFragment())
        }
        binding.btnBattery.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        runCheck()
    }

    private fun runCheck() {
        job?.cancel()
        binding.progress.show(true)
        binding.btnAgain.isEnabled = false
        job = viewLifecycleOwner.lifecycleScope.launch {
            val d = runCatching { ServiceLocator.diagnostics.run() }.getOrNull()
            if (_binding == null) return@launch
            binding.progress.show(false)
            binding.btnAgain.isEnabled = true
            binding.tvResult.text = if (d == null) getString(R.string.err_unknown) else render(d)
            binding.btnBattery.show(d?.batteryRestricted == true)
        }
    }

    private fun ok(b: Boolean?) = when (b) { true -> "✅"; false -> "❌"; null -> "➖" }

    private fun render(d: Diagnosis): String {
        val sb = StringBuilder()
        // 1) asosiy xulosa — bitta oddiy gap
        val verdict = when {
            !d.net.online -> R.string.diag_v_no_internet
            !d.configured -> R.string.diag_v_not_configured
            d.sqlOk -> R.string.diag_v_ok
            d.localPort == false && (d.remotePort == false || !d.remoteConfigured) && !d.net.vpn && d.remoteConfigured -> R.string.diag_v_vpn_off
            d.localPort == false && (d.remotePort == false || !d.remoteConfigured) -> R.string.diag_v_server_off
            else -> R.string.diag_v_login_failed
        }
        sb.append(getString(verdict)).append("\n\n")
        sb.append(ok(d.net.online)).append(' ').append(getString(R.string.diag_internet)).append(if (d.net.wifi) " (Wi-Fi)" else "").append('\n')
        sb.append(ok(if (d.remoteConfigured) d.net.vpn else null)).append(' ').append(getString(R.string.diag_vpn)).append('\n')
        sb.append(ok(d.localPort)).append(' ').append(getString(R.string.diag_local_port)).append('\n')
        if (d.remoteConfigured) sb.append(ok(d.remotePort)).append(' ').append(getString(R.string.diag_remote_port)).append('\n')
        sb.append(ok(d.sqlOk)).append(' ').append(getString(R.string.diag_sql))
        if (!d.sqlOk && d.sqlError != null) sb.append("\n    ").append(d.sqlError)
        sb.append('\n')
        d.clockSkewMs?.let { skew ->
            val bad = abs(skew) > 5 * 60_000
            sb.append(ok(!bad)).append(' ').append(getString(R.string.diag_clock, (skew / 60_000).toString())).append('\n')
        }
        sb.append('\n').append(getString(R.string.diag_queue, d.counts.unsent, d.counts.needsCheck, d.counts.retry + d.counts.uncertain)).append('\n')
        if (d.lastSuccessAt > 0) sb.append(getString(R.string.diag_last_sent, formatDateTime(d.lastSuccessAt))).append('\n')
        d.lastOutboxError?.let { sb.append(getString(R.string.diag_last_error, it)).append('\n') }
        if (d.batteryRestricted) sb.append("⚠️ ").append(getString(R.string.diag_battery)).append('\n')
        if (d.dbOk == false) sb.append("❌ ").append(getString(R.string.diag_db_bad)).append('\n')
        d.writeError?.let { sb.append("❌ ").append(getString(R.string.diag_write_error, it)).append('\n') }
        d.backgroundError?.let { sb.append("⚠️ ").append(getString(R.string.diag_bg_error, it)).append('\n') }
        if (d.legacyUnsentRows > 0) sb.append("⚠️ ").append(getString(R.string.diag_legacy, d.legacyUnsentRows)).append('\n')
        return sb.toString().trimEnd()
    }

    override fun onDestroyView() { job?.cancel(); super.onDestroyView(); _binding = null }

    companion object {
        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag("diag") == null) DiagnosticsSheet().show(fm, "diag")
        }
    }
}
