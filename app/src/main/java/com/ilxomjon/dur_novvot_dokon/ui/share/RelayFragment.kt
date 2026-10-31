package com.ilxomjon.dur_novvot_dokon.ui.share

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec
import com.ilxomjon.dur_novvot_dokon.data.relay.RelayService
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentRelayBinding
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.imsoft.shared.CloudDb

/**
 * Admin: agentlar ko'prigi. Yoqilganda Admin telefoni agentlar uchun serverga ulanadi (fonda), agentlarga esa faqat
 * internet kerak bo'ladi. Agentga "ulanish kodi" beriladi (Server sozlamalari ▸ Admin orqali).
 */
class RelayFragment : Fragment() {
    private var _b: FragmentRelayBinding? = null
    private val b get() = _b!!
    private val store get() = ServiceLocator.shareStore
    private var rendering = false

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { enable() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentRelayBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.toolbar.setNavigationOnClickListener { navigator().pop() }
        b.swRelay.setOnCheckedChangeListener { _, on ->
            if (rendering) return@setOnCheckedChangeListener
            if (on) askNotificationsThenEnable() else { RelayService.stop(requireContext().applicationContext); render() }
        }
        b.btnPumpNow.setOnClickListener { run(force = false) }
        b.btnPublishCat.setOnClickListener { run(force = true) }
        b.btnCodeShare.setOnClickListener {
            val code = code() ?: return@setOnClickListener
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, code) }
            startActivity(Intent.createChooser(send, getString(R.string.rl_code_share)))
        }
        b.btnCodeCopy.setOnClickListener {
            val code = code() ?: return@setOnClickListener
            requireContext().getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("kod", code))
            snack(getString(R.string.rl_code_copied))
        }
        render()
        // Holat har 5 soniyada yangilanadi (ekran ochiq paytida)
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) { delay(5_000L); if (_b != null) render() }
        }
    }

    private fun code(): String? {
        val c = config() ?: run { snack(getString(R.string.rl_need_cloud)); return null }
        return RelayCodec.encodeCode(c)
    }

    private fun config(): RelayCodec.Config? {
        if (!store.isCloudReady) return null
        if (store.agentKey.isEmpty()) store.agentKey = CloudDb.newKey()
        return RelayCodec.Config(CloudDb.normalizeUrl(store.dbUrl), store.agentKey).takeIf { it.isValid }
    }

    private fun askNotificationsThenEnable() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else enable()
    }

    private fun enable() {
        if (config() == null) { snack(getString(R.string.rl_need_cloud)); render(); return }
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            // Yangi Firebase qoidalari (agentlar yo'li) — o'rnatilmasa agentlar yoza olmaydi
            val rules = runCatching { ServiceLocator.share.ensureRulesNow() }
            if (rules.isFailure) { snack(getString(R.string.rl_rules_failed)); render(); return@launch }
            RelayService.start(app)
            render()
            run(force = true)
        }
    }

    private fun run(force: Boolean) {
        if (config() == null) { snack(getString(R.string.rl_need_cloud)); return }
        b.btnPumpNow.isEnabled = false
        b.btnPublishCat.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val r = runCatching { ServiceLocator.relayHost.pump(forceCatalog = force) }
            val bb = _b ?: return@launch
            bb.btnPumpNow.isEnabled = true
            bb.btnPublishCat.isEnabled = true
            r.onFailure { e -> snack(e.message ?: e.javaClass.simpleName) }
            render()
        }
    }

    private fun render() {
        val bb = _b ?: return
        rendering = true
        val ready = store.isCloudReady
        bb.swRelay.isChecked = store.relayOn
        bb.swRelay.isEnabled = ready
        val s = ServiceLocator.relayHost.stats
        bb.tvRelayState.text = when {
            !ready -> getString(R.string.rl_need_cloud)
            !store.relayOn -> getString(R.string.rl_state_off)
            s.at == 0L -> getString(R.string.rl_state_starting)
            else -> getString(
                R.string.rl_state, formatDateTime(s.at), s.written, s.requests, s.labels,
                if (s.catalogAt > 0) formatDateTime(s.catalogAt) else "—",
            )
        }
        bb.tvRelayError.show(s.error.isNotEmpty())
        bb.tvRelayError.text = getString(R.string.rl_state_error, s.error)
        bb.tvRelayCode.text = if (ready) RelayCodec.encodeCode(config() ?: RelayCodec.Config("", "")) else "—"
        rendering = false
    }

    private fun snack(t: String) {
        val bb = _b ?: return
        Snackbar.make(bb.root, t, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
