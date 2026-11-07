package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
 * v5.0 "Sozlamalar": tepada ko'rinish (til, mavzu, yozuv o'lchami) — ixcham; pastida plitkalar.
 * Har plitkani uzoq bossa — nima qilishi to'liq yoziladi. Agentda faqat oddiy sozlamalar qoladi,
 * do'konni boshqarish ishlari Admin'da (Boshqaruv).
 */
class SettingsHubFragment : CodeFragment() {
    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.settings_title)
        val c = p.content
        // v5.0.3: keng ekranda 3 ustun, aks holda 2
        val columns = if (resources.configuration.screenWidthDp >= 420) 3 else 2
        val prefs = ServiceLocator.appPrefs
        val admin = AppRole.isAdmin

        // Ko'rinish — ixcham, bitta kartada
        val (cLook, look) = Ui.card(ctx, 4)
        look.addView(Ui.text(ctx, getString(R.string.settings_language), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge))
        look.addView(
            Ui.toggle(ctx, listOf(getString(R.string.settings_lang_latin), getString(R.string.settings_lang_cyrillic)), if (prefs.language == UiLanguage.CYRILLIC) 1 else 0, 4) { i ->
                val l = if (i == 1) UiLanguage.CYRILLIC else UiLanguage.LATIN
                if (prefs.language != l) { prefs.language = l; ServiceLocator.applyUiPrefs() }
            },
        )
        // v5.0: Admin mavzusi har doim qorong'i (terminal ko'rinish) — tanlov faqat Agentda
        if (!admin) {
            look.addView(Ui.text(ctx, getString(R.string.settings_theme), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 10))
            look.addView(
                Ui.toggle(ctx, listOf(getString(R.string.settings_theme_system), getString(R.string.settings_theme_light), getString(R.string.settings_theme_dark)), prefs.theme.ordinal, 4) { i ->
                    val t = UiTheme.entries[i]
                    if (prefs.theme != t) { prefs.theme = t; ServiceLocator.applyUiPrefs() }
                },
            )
        } else {
            look.addView(Ui.hint(ctx, getString(R.string.settings_theme_admin), 10))
        }
        look.addView(Ui.text(ctx, getString(R.string.sg_font), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 10))
        val fonts = listOf(0.9f, 1.0f, 1.15f)
        look.addView(
            Ui.toggle(ctx, listOf(getString(R.string.sg_font_s), getString(R.string.sg_font_m), getString(R.string.sg_font_l)), fonts.indexOf(prefs.fontScale).coerceAtLeast(0), 4) { i ->
                if (prefs.fontScale != fonts[i]) { prefs.fontScale = fonts[i]; activity?.recreate() }
            },
        )
        look.addView(
            Ui.switch(ctx, getString(R.string.settings_keep_screen), prefs.keepScreenOn, 10) { on ->
                prefs.keepScreenOn = on
                if (on) requireActivity().window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else requireActivity().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            },
        )
        c.addView(cLook)

        // Ish plitkalari
        c.addView(Ui.section(ctx, getString(R.string.sg_work)))
        val work = ArrayList<Ui.Tile>()
        work += Ui.Tile(getString(R.string.sg_queue), R.drawable.ic_sync_alt, getString(R.string.sg_h_queue), 1) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxFragment())
        }
        work += Ui.Tile(getString(R.string.sg_scanner), R.drawable.ic_qr, getString(R.string.sg_h_scanner)) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodesFragment())
        }
        work += Ui.Tile(getString(R.string.sg_fav), R.drawable.ic_store, getString(R.string.sg_h_fav)) {
            navigator().push(SettingsFragment())
        }
        work += Ui.Tile(getString(R.string.wgs_title), R.drawable.ic_widgets, getString(R.string.wgs_hint)) {
            navigator().push(WidgetFragment())
        }
        work += Ui.Tile(getString(R.string.sg_extras), R.drawable.ic_tune, getString(R.string.sg_h_extras)) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.extras.ExtrasFragment())
        }
        if (admin) {
            work += Ui.Tile(getString(R.string.bb_title), R.drawable.ic_barcode, getString(R.string.sg_h_batch)) {
                navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment())
            }
            work += Ui.Tile(getString(R.string.stt_title), R.drawable.ic_trending, getString(R.string.sg_h_stats)) {
                navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment())
            }
        }
        c.addView(Ui.tiles(ctx, work, columns, onHelp = ::help))

        // Ulanish va yordam
        c.addView(Ui.section(ctx, getString(R.string.sg_help)))
        val help = ArrayList<Ui.Tile>()
        help += Ui.Tile(getString(R.string.up_check), R.drawable.ic_download, getString(R.string.sg_h_update), 1) {
            (activity as? MainActivity)?.checkUpdate(true)
        }
        help += Ui.Tile(getString(R.string.settings_server), R.drawable.ic_server, getString(R.string.sg_h_server)) {
            navigator().push(ServerSettingsFragment())
        }
        help += Ui.Tile(getString(R.string.sg_diag), R.drawable.ic_info, getString(R.string.sg_h_diag)) {
            com.ilxomjon.dur_novvot_dokon.ui.diag.DiagnosticsSheet.show(childFragmentManager)
        }
        help += Ui.Tile(getString(R.string.pf_title), R.drawable.ic_person, getString(R.string.sg_h_profile)) {
            navigator().push(ProfileFragment())
        }
        c.addView(Ui.tiles(ctx, help, columns, onHelp = ::help))

        c.addView(Ui.hint(ctx, getString(R.string.sg_long_press), 12))
        return p.root
    }

    private fun help(t: Ui.Tile) {
        MaterialAlertDialogBuilder(ctx).setTitle(t.title.toString()).setMessage(t.help).setPositiveButton(R.string.ok, null).show()
    }
}
