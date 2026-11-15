package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.content.Context
import android.text.InputType
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.share.ShareWorker
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerHost
import com.ilxomjon.dur_novvot_dokon.ui.ledger.SecretFragment
import com.ilxomjon.dur_novvot_dokon.ui.ledger.SecretItem
import com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.launch
import uz.imsoft.shared.ShopCode
import java.util.Calendar

/** 1-ilova (Savdo Agent) sirli menyusi: umumiy bandlar + agentga xoslari. Serverga hech narsa yozilmaydi. */
class AgentSecretFragment : SecretFragment() {
    override val ledgerHost: LedgerHost get() = DokonLedgerHost
    override fun close() = navigator().pop()
    override fun usdRate(): Double = ServiceLocator.catalog.state.value.rate
    override fun workNames() = listOf("share_periodic", "share_now")

    override fun products(): List<Pair<String, String>> =
        ServiceLocator.catalog.state.value.products.filter { it.wholesaleSom > 0 || it.wholesaleUsd > 0 }.map { p ->
            p.name to if (p.wholesaleSom > 0) Money.display(p.wholesaleSom) + " " + getString(R.string.unit_som) else Money.display(p.wholesaleUsd) + " $"
        }

    private val prefs get() = requireContext().getSharedPreferences("agent_secret", Context.MODE_PRIVATE)

    override fun appItems(): List<SecretItem> {
        val l = ArrayList<SecretItem>()
        l += SecretItem("📊", getString(R.string.sx_stats), getString(R.string.sx_stats_sub)) { stats() }
        l += SecretItem("🎯", getString(R.string.sx_goal), goalSub()) { goal() }
        l += SecretItem("🕰️", getString(R.string.sx_hours), getString(R.string.sx_hours_sub)) { hours() }
        l += SecretItem("⚡", getString(R.string.sx_turbo), getString(R.string.sx_turbo_sub)) { turbo() }
        l += SecretItem("🔑", getString(R.string.sx_cloud), getString(R.string.sx_cloud_sub)) { cloudPassport() }
        // v5.3: server manzili va hamma parollar FAQAT shu yerda (oddiy sozlamalarda yo'q), ochiq ko'rinadi
        if (com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin) {
            l += SecretItem("🔐", getString(R.string.sx_pass), getString(R.string.sx_pass_sub)) { passwords() }
            l += SecretItem("🖥️", getString(R.string.sx_server), getString(R.string.sx_server_sub)) { navigator().push(ServerSettingsFragment()) }
        }
        if (ServiceLocator.share.currentClient()?.queueKey?.isNotEmpty() == true) {
            l += SecretItem("🧾", getString(R.string.sx_test_receipt), getString(R.string.sx_test_receipt_sub, ServiceLocator.share.currentClient()?.name.orEmpty())) { testReceipt() }
        }
        return l
    }

    private fun passwords() {
        val cfg = ServiceLocator.secure.serverConfig
        val st = ServiceLocator.shareStore
        val rows = listOf(
            getString(R.string.sx_p_host) to cfg.host,
            getString(R.string.sx_p_db) to cfg.database,
            getString(R.string.sx_p_login) to cfg.login,
            getString(R.string.sx_p_password) to cfg.password,
            getString(R.string.sx_p_cloud) to st.dbUrl,
            getString(R.string.sx_p_secret) to st.secret,
            getString(R.string.sx_p_inbox) to st.inboxKey,
            getString(R.string.sx_p_agents) to st.agentKey,
        )
        val text = rows.joinToString("\n\n") { (k, v) -> k + ":\n" + v.ifEmpty { "—" } }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sx_pass).setMessage(text)
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.sx_copy) { _, _ ->
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("imsoft", text))
            }.show()
    }

    private fun stats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val c = ServiceLocator.catalog.state.value
            val clients = ServiceLocator.shareStore.clients()
            val orders = ServiceLocator.history.orders()
            val pays = ServiceLocator.history.payments()
            val counts = ServiceLocator.outbox.counts.value
            val st = ServiceLocator.share.state.value
            val text = listOf(
                getString(R.string.sx_st_catalog, c.products.size, c.groups.size, c.stores.size),
                getString(R.string.sx_st_history, orders.size, pays.size, counts.unsent),
                getString(R.string.sx_st_clients, clients.size, clients.count { it.kind == "s" }, clients.sumOf { it.members.size }),
                getString(R.string.sx_st_ledger, st.clientName.ifBlank { "—" }, st.entries),
                getString(R.string.sx_st_orders, st.newOrders),
            ).joinToString("\n")
            info(getString(R.string.sx_stats), text)
        }
    }

    private fun goalSub(): String {
        val g = prefs.getLong("goal", 0L)
        return if (g > 0) getString(R.string.sx_goal_set, Money.display(g.toDouble())) else getString(R.string.sx_goal_sub)
    }

    private suspend fun todaySom(): Double {
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val rate = ServiceLocator.catalog.state.value.rate
        return ServiceLocator.history.orders().filter { it.sentAt >= start && it.mode == DealMode.SALE }
            .sumOf { val net = it.total - it.discount; if (it.currency == Currency.USD) net * rate else net }
    }

    private fun goal() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val g = prefs.getLong("goal", 0L)
            val done = todaySom()
            val til = TextInputLayout(ctx).apply { setPadding(dp(20), dp(8), dp(20), 0); hint = getString(R.string.sx_goal_hint) }
            val edt = TextInputEditText(til.context).apply { inputType = InputType.TYPE_CLASS_NUMBER; if (g > 0) setText(g.toString()) }
            til.addView(edt)
            val pct = if (g > 0) (done * 100 / g).toInt() else 0
            val bar = if (g > 0) "█".repeat((pct / 10).coerceIn(0, 10)) + "░".repeat(10 - (pct / 10).coerceIn(0, 10)) + "  $pct%" else ""
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.sx_goal)
                .setMessage(getString(R.string.sx_goal_today, Money.display(done)) + if (bar.isNotEmpty()) "\n$bar" + (if (pct >= 100) "\n" + getString(R.string.sx_goal_done) else "") else "")
                .setView(til)
                .setPositiveButton(R.string.save) { _, _ ->
                    val v = edt.text?.toString()?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
                    prefs.edit().putLong("goal", v).apply()
                    if (v > 0 && done >= v) com.ilxomjon.dur_novvot_dokon.ui.ledger.ConfettiView.burst(requireActivity())
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun hours() {
        viewLifecycleOwner.lifecycleScope.launch {
            val orders = ServiceLocator.history.orders().filter { it.mode == DealMode.SALE }
            if (orders.isEmpty()) { snack(getString(R.string.sx_hours_empty)); return@launch }
            val cal = Calendar.getInstance()
            val byHour = IntArray(24)
            for (o in orders) { cal.timeInMillis = o.sentAt; byHour[cal.get(Calendar.HOUR_OF_DAY)]++ }
            val max = byHour.maxOrNull()?.coerceAtLeast(1) ?: 1
            val best = byHour.indices.maxByOrNull { byHour[it] } ?: 0
            val text = (6..22).joinToString("\n") { h -> "%02d:00 %s %d".format(h, "▇".repeat(byHour[h] * 12 / max).ifEmpty { "·" }, byHour[h]) } +
                "\n\n" + getString(R.string.sx_hours_best, best, best + 1)
            info(getString(R.string.sx_hours), text)
        }
    }

    private fun turbo() {
        val repo = ServiceLocator.share
        if (!repo.state.value.cloudReady) { snack(getString(R.string.share_st_no_firebase)); return }
        snack(getString(R.string.sx_turbo_run))
        viewLifecycleOwner.lifecycleScope.launch {
            val t0 = System.currentTimeMillis()
            val ok = runCatching { repo.tick(); repo.publish(all = true) }.getOrDefault(false)
            val ms = System.currentTimeMillis() - t0
            ShareWorker.kick(requireContext().applicationContext, 0)
            snack(getString(if (ok) R.string.sx_turbo_ok else R.string.sx_turbo_fail, ms))
        }
    }

    private fun cloudPassport() {
        val s = ServiceLocator.shareStore
        val clients = s.clients()
        val text = listOf(
            getString(R.string.sx_cl_shop, ShopCode.fromUrl(s.dbUrl).ifEmpty { "—" }),
            getString(R.string.sx_cl_secret, if (s.secret.isEmpty()) "—" else mask(s.secret)),
            getString(R.string.sx_cl_inbox, if (s.inboxKey.isEmpty()) "—" else mask(s.inboxKey)),
            getString(R.string.sx_cl_rules, s.rulesVersion, uz.imsoft.shared.CloudDb.RULES_VERSION),
            getString(R.string.sx_cl_err, s.lastPublishError.ifEmpty { "—" }),
        ).joinToString("\n") + "\n\n" + clients.joinToString("\n") { c ->
            "• " + c.name + ": " + mask(c.readKey) + (if (c.queueKey.isNotEmpty()) " · 🖨" else "") + " · 📱" + c.members.size
        }
        info(getString(R.string.sx_cloud), text)
    }

    private fun testReceipt() {
        val c = ServiceLocator.share.currentClient() ?: return
        val now = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US).format(java.util.Date())
        // Faqat ASCII: printer kodlash jadvalidan qat'i nazar o'qiladi (mijoz nomi kirillda bo'lsa ham '?' bo'lmasin).
        val name = c.name.filter { it.code in 32..126 }.ifBlank { "-" }
        val text = "\n  *** SAVDO AGENT ***\n  SINOV CHEKI\n  Mijoz: $name\n  $now\n  Printer ishlayapti!\n\n\n\n"
        // ESC @ (boshlash) + matn (lotin, CP437 da ham o'qiladi) + qog'oz kesish
        val bytes = byteArrayOf(0x1B, 0x40) + text.toByteArray(Charsets.US_ASCII) + byteArrayOf(0x1D, 0x56, 0x42, 0x00)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ServiceLocator.share.sendToBranchPrinter(bytes, c.id) }
                .onSuccess { snack(getString(R.string.lgh_branch_sent)) }
                .onFailure { snack(it.message ?: it.javaClass.simpleName) }
        }
    }
}
