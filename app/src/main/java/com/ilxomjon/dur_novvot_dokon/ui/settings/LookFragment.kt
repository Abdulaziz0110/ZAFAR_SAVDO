package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.view.LayoutInflater
import android.view.View
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.UiLanguage
import com.ilxomjon.dur_novvot_dokon.data.UiTheme
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import uz.imsoft.shared.android.Ui

/** v5.2 (A9): "Til, mavzu va yozuv o'lchami" — avval Sozlamalar tepasida edi, endi alohida bo'lim. */
class LookFragment : CodeFragment() {
    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.sg_look)
        val c = p.content
        val prefs = ServiceLocator.appPrefs
        val admin = AppRole.isAdmin

        val (card, look) = Ui.card(ctx, 4)
        look.addView(Ui.text(ctx, getString(R.string.settings_language), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge))
        look.addView(
            Ui.toggle(ctx, listOf(getString(R.string.settings_lang_latin), getString(R.string.settings_lang_cyrillic)), if (prefs.language == UiLanguage.CYRILLIC) 1 else 0, 4) { i ->
                val l = if (i == 1) UiLanguage.CYRILLIC else UiLanguage.LATIN
                if (prefs.language != l) { prefs.language = l; ServiceLocator.applyUiPrefs() }
            },
        )
        // Admin mavzusi har doim qorong'i (terminal ko'rinish) — tanlov faqat Agentda
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
        c.addView(card)
        return p.root
    }
}
