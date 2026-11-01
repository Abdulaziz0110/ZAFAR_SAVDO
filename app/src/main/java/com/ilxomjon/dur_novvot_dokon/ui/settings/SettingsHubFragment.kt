package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.view.LayoutInflater
import android.view.View
import com.ilxomjon.dur_novvot_dokon.BuildConfig
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.UiLanguage
import com.ilxomjon.dur_novvot_dokon.data.UiTheme
import com.ilxomjon.dur_novvot_dokon.ui.MainActivity
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui

/**
 * v4.0 "Sozlamalar" qaytadan: bo'limlarga ajratilgan, har qatorda ikonka va qisqa izoh. Agentda — faqat kerakli narsa
 * (ko'rinish, navbat, yangilanish); Admin'da — tizimning hamma sozlamasi o'z bo'limida. Eski to'liq ekran
 * "Boshqa sozlamalar"da qoladi.
 */
class SettingsHubFragment : CodeFragment() {
    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.settings_title)
        val c = p.content
        val prefs = ServiceLocator.appPrefs
        val admin = AppRole.isAdmin

        // Kim
        val agent = ServiceLocator.catalog.state.value.agent
        val (cWho, who) = Ui.card(ctx, 4)
        who.addView(Ui.item(ctx, agent?.fullName?.ifBlank { null } ?: getString(if (admin) R.string.sg_admin else R.string.sg_agent),
            listOfNotNull(agent?.phone?.takeIf { it.isNotBlank() }, getString(R.string.sg_version, BuildConfig.VERSION_NAME)).joinToString(" · "),
            icon = R.drawable.ic_person, tone = 1))
        c.addView(cWho)

        // Ko'rinish
        c.addView(Ui.section(ctx, getString(R.string.settings_look)))
        val (cLook, look) = Ui.card(ctx)
        look.addView(Ui.text(ctx, getString(R.string.settings_language), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge))
        look.addView(Ui.toggle(ctx, listOf(getString(R.string.settings_lang_latin), getString(R.string.settings_lang_cyrillic)), if (prefs.language == UiLanguage.CYRILLIC) 1 else 0, 4) { i ->
            val l = if (i == 1) UiLanguage.CYRILLIC else UiLanguage.LATIN
            if (prefs.language != l) { prefs.language = l; ServiceLocator.applyUiPrefs() }
        })
        look.addView(Ui.text(ctx, getString(R.string.settings_theme), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 12))
        look.addView(Ui.toggle(ctx, listOf(getString(R.string.settings_theme_system), getString(R.string.settings_theme_light), getString(R.string.settings_theme_dark)), prefs.theme.ordinal, 4) { i ->
            val t = UiTheme.entries[i]
            if (prefs.theme != t) { prefs.theme = t; ServiceLocator.applyUiPrefs() }
        })
        look.addView(Ui.text(ctx, getString(R.string.sg_font), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 12))
        val fonts = listOf(0.9f, 1.0f, 1.15f)
        look.addView(Ui.toggle(ctx, listOf(getString(R.string.sg_font_s), getString(R.string.sg_font_m), getString(R.string.sg_font_l)), fonts.indexOf(prefs.fontScale).coerceAtLeast(0), 4) { i ->
            if (prefs.fontScale != fonts[i]) { prefs.fontScale = fonts[i]; activity?.recreate() }
        })
        look.addView(Ui.switch(ctx, getString(R.string.settings_keep_screen), prefs.keepScreenOn, 12) { on ->
            prefs.keepScreenOn = on
            if (on) requireActivity().window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else requireActivity().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        })
        c.addView(cLook)

        // Ish
        c.addView(Ui.section(ctx, getString(R.string.sg_work)))
        val (cWork, work) = Ui.card(ctx)
        fun item(col: android.widget.LinearLayout, title: Int, sub: Int, icon: Int, go: () -> Unit) {
            if (col.childCount > 0) col.addView(Ui.divider(ctx))
            col.addView(Ui.item(ctx, getString(title), getString(sub), icon = icon) { go() })
        }
        item(work, R.string.sg_queue, R.string.settings_queue_sum, R.drawable.ic_sync_alt) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxFragment()) }
        item(work, R.string.bb_title, R.string.bb_sub, R.drawable.ic_barcode) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment()) }
        item(work, R.string.stt_title, R.string.sg_stats_sub, R.drawable.ic_trending) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment()) }
        item(work, R.string.sg_scanner, R.string.sg_scanner_sub, R.drawable.ic_qr) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodesFragment()) }
        c.addView(cWork)

        if (admin) {
            c.addView(Ui.section(ctx, getString(R.string.sg_system)))
            val (cSys, sys) = Ui.card(ctx)
            item(sys, R.string.ad_title, R.string.ad_card_sub, R.drawable.ic_tune) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.AdminHubFragment()) }
            item(sys, R.string.dv_title, R.string.dv_sub, R.drawable.ic_groups) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.DevicesFragment()) }
            item(sys, R.string.settings_server, R.string.sg_server_sub, R.drawable.ic_server) { navigator().push(ServerSettingsFragment()) }
            item(sys, R.string.ad_share, R.string.ad_share_sub, R.drawable.ic_storefront) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.share.ShareFragment()) }
            item(sys, R.string.au_title, R.string.au_sub, R.drawable.ic_backup) { navigator().push(AutoUpdateFragment()) }
            item(sys, R.string.ad_labels, R.string.ad_labels_sub, R.drawable.ic_label) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.labels.LabelsFragment()) }
            c.addView(cSys)
        }

        // Yordam
        c.addView(Ui.section(ctx, getString(R.string.sg_help)))
        val (cHelp, help) = Ui.card(ctx)
        item(help, R.string.up_check, R.string.sg_update_sub, R.drawable.ic_download) { (activity as? MainActivity)?.checkUpdate(true) }
        item(help, R.string.sg_diag, R.string.settings_diag_sum, R.drawable.ic_info) { com.ilxomjon.dur_novvot_dokon.ui.diag.DiagnosticsSheet.show(childFragmentManager) }
        item(help, R.string.sg_more, R.string.sg_more_sub, R.drawable.ic_settings) { navigator().push(SettingsFragment()) }
        c.addView(cHelp)
        return p.root
    }
}
