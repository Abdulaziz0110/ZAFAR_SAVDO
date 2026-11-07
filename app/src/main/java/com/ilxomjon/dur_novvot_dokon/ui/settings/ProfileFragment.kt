package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.BuildConfig
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import uz.imsoft.shared.android.Ui

/**
 * v5.0 "Men": kim kirgan, ilova haqida qisqacha va chiqish. Rasmiyatchilik bosh sahifadan shu yerga ko'chirildi —
 * bosh sahifada faqat ish qoladi. Versiya butun ilovada faqat shu yerda ko'rsatiladi.
 */
class ProfileFragment : CodeFragment() {
    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.pf_title)
        val c = p.content
        val agent = ServiceLocator.catalog.state.value.agent
        val (cWho, who) = Ui.card(ctx, 4)
        who.addView(
            Ui.item(
                ctx,
                agent?.fullName?.ifBlank { null } ?: getString(if (AppRole.isAdmin) R.string.pf_admin else R.string.pf_agent),
                listOfNotNull(
                    agent?.phone?.takeIf { it.isNotBlank() },
                    getString(if (AppRole.isAdmin) R.string.pf_role_admin else R.string.pf_role_agent),
                ).joinToString(" · "),
                icon = R.drawable.ic_person, tone = 1,
            ),
        )
        c.addView(cWho)

        c.addView(Ui.section(ctx, getString(R.string.pf_about)))
        val (cAbout, about) = Ui.card(ctx)
        about.addView(Ui.text(ctx, getString(if (AppRole.isAdmin) R.string.pf_about_admin else R.string.pf_about_agent)))
        about.addView(Ui.hint(ctx, getString(R.string.pf_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), 10))
        c.addView(cAbout)

        c.addView(Ui.section(ctx, getString(R.string.pf_exit_sec)))
        val (cOut, out) = Ui.card(ctx)
        out.addView(
            Ui.item(ctx, getString(R.string.settings_logout), getString(R.string.pf_exit_sub), icon = R.drawable.ic_logout, tone = 2) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.settings_logout_title)
                    .setMessage(R.string.settings_logout_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.settings_logout) { _, _ -> launchUi { ServiceLocator.auth.logout() } }
                    .show()
            },
        )
        c.addView(cOut)
        return p.root
    }
}
