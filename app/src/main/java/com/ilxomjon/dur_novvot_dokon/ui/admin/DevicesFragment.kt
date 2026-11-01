package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.BuildConfig
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.DeviceCodes
import uz.imsoft.shared.DeviceInfo
import uz.imsoft.shared.android.Ui

/**
 * v4.0 Admin ▸ Qurilmalar: Agent va Mijoz telefonlari o'zi shu yerga tushadi. Admin kimligini belgilaydi (agent yoki
 * mijoz), xohlagan paytda bloklaydi. Telefonlarda kod, parol yoki sozlama yo'q.
 */
class DevicesFragment : CodeFragment() {
    private lateinit var box: LinearLayout
    private lateinit var tvInfo: TextView
    private val admin get() = ServiceLocator.deviceAdmin

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.dv_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.dv_hint), 4))
        val (card, col) = Ui.card(ctx)
        col.addView(Ui.item(ctx, getString(R.string.dv_shop_code), getString(R.string.dv_shop_code_sub), icon = R.drawable.ic_qr, tone = 1) { showShopCode() })
        col.addView(Ui.divider(ctx))
        col.addView(Ui.item(ctx, getString(R.string.dv_iphone), getString(R.string.dv_iphone_sub), icon = R.drawable.ic_share) { shareIphoneLink() })
        c.addView(card)
        tvInfo = Ui.hint(ctx, "", 12).also { c.addView(it) }
        box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(box)
        c.addView(Ui.button(ctx, getString(R.string.sp_refresh), outlined = true, icon = R.drawable.ic_sync, top = 16) { launchUi { admin.refresh() } })
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchOnStarted { admin.devices.collect { render(it) } }
        launchOnStarted {
            while (isActive) {
                if (admin.ready) runCatching { admin.refresh() }.onFailure { tvInfo.text = getString(R.string.dv_offline) }
                delay(10_000)
            }
        }
        if (!admin.ready) tvInfo.text = getString(R.string.oc_no_cloud)
    }

    private fun roleIcon(d: DeviceInfo): Int = when (d.role) {
        DeviceCodes.ROLE_MIJOZ -> R.drawable.ic_storefront
        DeviceCodes.ROLE_ADMIN -> R.drawable.ic_tune
        else -> R.drawable.ic_person
    }

    private fun render(list: List<DeviceInfo>) {
        if (!::box.isInitialized) return
        box.removeAllViews()
        val groups = listOf(
            R.string.dv_new to list.filter { it.assignment == null && it.role != DeviceCodes.ROLE_ADMIN },
            R.string.dv_agents to list.filter { it.assignment?.blocked == false && it.assignment.agent.isNotEmpty() },
            R.string.dv_clients to list.filter { it.assignment?.blocked == false && it.assignment.link.isNotEmpty() },
            R.string.dv_blocked to list.filter { it.assignment?.blocked == true },
            R.string.dv_admins to list.filter { it.role == DeviceCodes.ROLE_ADMIN && it.assignment == null },
        )
        if (list.isEmpty()) box.addView(Ui.hint(ctx, getString(R.string.dv_empty), 8))
        for ((title, items) in groups) {
            if (items.isEmpty()) continue
            box.addView(Ui.section(ctx, getString(title) + " · " + items.size))
            val (card, col) = Ui.card(ctx)
            items.forEachIndexed { i, d ->
                if (i > 0) col.addView(Ui.divider(ctx))
                val a = d.assignment
                val name = a?.label?.takeIf { it.isNotBlank() } ?: d.model.ifEmpty { d.id.take(6) }
                val sub = listOfNotNull(
                    if (a?.label?.isNotBlank() == true) d.model else null,
                    getString(R.string.dv_code, d.id.take(6).uppercase()),
                    d.version.takeIf { it.isNotEmpty() }?.let { "v$it" },
                    if (d.seenAt > 0) getString(R.string.dv_seen, formatDateTime(d.seenAt)) else null,
                    if (d.crashAt > 0) getString(R.string.dv_crash) else null,
                ).joinToString(" · ")
                val tone = when {
                    a?.blocked == true -> 3
                    a == null && d.role != DeviceCodes.ROLE_ADMIN -> 2
                    d.crashAt > 0 -> 3
                    else -> 0
                }
                col.addView(Ui.item(ctx, name, sub, icon = roleIcon(d), tone = tone) { menu(d) })
            }
            box.addView(card)
        }
        tvInfo.text = if (admin.loadedAt > 0) getString(R.string.dv_updated, formatDateTime(admin.loadedAt)) else ""
    }

    private fun menu(d: DeviceInfo) {
        val a = d.assignment
        val acts = ArrayList<Pair<String, () -> Unit>>()
        if (d.role != DeviceCodes.ROLE_MIJOZ && d.role != DeviceCodes.ROLE_ADMIN) acts += getString(R.string.dv_as_agent) to { pickAgent(d) }
        if (d.role == DeviceCodes.ROLE_MIJOZ) acts += getString(R.string.dv_as_client) to { pickClient(d) }
        if (a?.blocked == true) acts += getString(R.string.dv_unblock) to { launchUi { admin.setBlocked(d.id, false); snack(getString(R.string.dv_done)) } }
        else if (d.role != DeviceCodes.ROLE_ADMIN) acts += getString(R.string.dv_block) to { confirmBlock(d) }
        if (d.crashAt > 0) acts += getString(R.string.dv_show_crash) to { showCrash(d) }
        acts += getString(R.string.dv_remove) to { launchUi { admin.remove(d.id) } }
        MaterialAlertDialogBuilder(ctx).setTitle(a?.label?.takeIf { it.isNotBlank() } ?: d.model)
            .setItems(acts.map { it.first }.toTypedArray()) { _, i -> acts[i].second() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmBlock(d: DeviceInfo) {
        MaterialAlertDialogBuilder(ctx).setMessage(R.string.dv_block_q)
            .setPositiveButton(R.string.dv_block) { _, _ -> launchUi { admin.setBlocked(d.id, true); snack(getString(R.string.dv_done)) } }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun pickAgent(d: DeviceInfo) {
        launchUi {
            val agents = try { admin.agentList() } catch (e: Exception) {
                snack(getString(R.string.dv_agents_fail)); return@launchUi
            }
            if (agents.isEmpty()) { snack(getString(R.string.dv_agents_fail)); return@launchUi }
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.dv_as_agent)
                .setItems(agents.map { it.fullName.ifBlank { it.phone } }.toTypedArray()) { _, i ->
                    launchUi { admin.assignAgent(d.id, agents[i], agents[i].fullName); snack(getString(R.string.dv_done)) }
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun pickClient(d: DeviceInfo) {
        val clients = ServiceLocator.shareStore.clients().filter { it.kind != "s" }
        if (clients.isEmpty()) { snack(getString(R.string.dv_no_clients)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.dv_as_client)
            .setItems(clients.map { it.name }.toTypedArray()) { _, i ->
                val c = clients[i]
                val (til, e) = Ui.field(ctx, getString(R.string.dv_label), value = c.name)
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), 0, Ui.dp(ctx, 20), 0); addView(til) }
                MaterialAlertDialogBuilder(ctx).setTitle(c.name).setMessage(R.string.dv_label_hint).setView(col)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        launchUi { admin.assignClient(d.id, c.id, e.text?.toString()?.trim().orEmpty().ifEmpty { c.name }); snack(getString(R.string.dv_done)) }
                    }.setNegativeButton(R.string.cancel, null).show()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showCrash(d: DeviceInfo) {
        MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.dv_crash_title, formatDateTime(d.crashAt))).setMessage(d.crash.take(3000))
            .setPositiveButton(R.string.dv_copy) { _, _ -> copy(d.crash) }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", text))
        snack(getString(R.string.dv_copied))
    }

    private fun showShopCode() {
        val code = admin.shopCode()
        if (code.isEmpty()) { snack(getString(R.string.oc_no_cloud)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.dv_shop_code).setMessage(getString(R.string.dv_shop_code_how, code))
            .setPositiveButton(R.string.dv_copy) { _, _ -> copy(code) }
            .setNegativeButton(R.string.close, null).show()
    }

    /** iPhone (veb) uchun havola: bosilsa ulanish kodi o'zi qo'yiladi (kod havolaning # qismida — serverga bormaydi). */
    private fun shareIphoneLink() {
        val st = ServiceLocator.shareStore
        if (!st.isCloudReady) { snack(getString(R.string.oc_no_cloud)); return }
        if (st.agentKey.isEmpty()) st.agentKey = CloudDb.newKey()
        val code = RelayCodec.encodeCode(RelayCodec.Config(CloudDb.normalizeUrl(st.dbUrl), st.agentKey))
        val host = Regex("^(https://[^/]+)/").find(BuildConfig.UPDATE_URL)?.groupValues?.get(1) ?: WEB_DEFAULT
        val link = "$host/#c=$code"
        startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.dv_iphone_text, link))
        }, getString(R.string.dv_iphone)))
    }

    private companion object {
        const val WEB_DEFAULT = "https://zafar-savdo-agent.pages.dev"
    }
}
