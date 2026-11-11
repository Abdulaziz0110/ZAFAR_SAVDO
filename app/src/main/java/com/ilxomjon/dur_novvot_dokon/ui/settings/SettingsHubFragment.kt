package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.view.LayoutInflater
import android.view.View
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.ui.MainActivity
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui

/**
 * v5.2 (A9) "Sozlamalar": faqat BO'LIMLAR ro'yxati, guruhlarga ajratilgan. Har qatorda ikonka,
 * tushunarli nom va bir qatorlik izoh — nima ekanini o'qimasdan ham bilish uchun.
 * Boshqarish ishlari eng pastda ("Do'konni boshqarish"), til va o'lchovlar alohida bo'limda.
 */
class SettingsHubFragment : CodeFragment() {
    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.settings_title)
        val c = p.content
        val admin = AppRole.isAdmin

        fun row(title: Int, sub: Int, icon: Int, tone: Int = 0, go: () -> Unit) {
            c.addView(Ui.item(ctx, getString(title), getString(sub), icon = icon, tone = tone) { go() })
        }

        c.addView(Ui.section(ctx, getString(R.string.sg_g_work)))
        // v5.2 (A7): mahsulotlar ro'yxati va ularning ma'lumoti (internetsiz ham ochiladi)
        row(R.string.pb_title, R.string.pb_sub, R.drawable.ic_shopping_bag, 1) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.ProductsFragment())
        }
        row(R.string.sg_queue, R.string.sg_r_queue_sub, R.drawable.ic_sync_alt, 1) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxFragment())
        }
        row(R.string.sg_scanner, R.string.sg_r_scanner_sub, R.drawable.ic_qr) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodesFragment())
        }
        row(R.string.sg_fav, R.string.sg_r_fav_sub, R.drawable.ic_store) { navigator().push(SettingsFragment()) }
        row(R.string.wgs_title, R.string.sg_r_widget_sub, R.drawable.ic_widgets) { navigator().push(WidgetFragment()) }
        row(R.string.sg_extras, R.string.sg_r_extras_sub, R.drawable.ic_tune) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.extras.ExtrasFragment())
        }
        if (admin) {
            row(R.string.bb_title, R.string.sg_r_batch_sub, R.drawable.ic_barcode) {
                navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment())
            }
            row(R.string.stt_title, R.string.sg_r_stats_sub, R.drawable.ic_trending) {
                navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment())
            }
        }

        c.addView(Ui.section(ctx, getString(R.string.sg_g_look)))
        row(R.string.sg_look, R.string.sg_look_sub, R.drawable.ic_palette) { navigator().push(LookFragment()) }

        c.addView(Ui.section(ctx, getString(R.string.sg_g_link)))
        // v5.2 (A12): Agent ko'prik orqali ishlaydi — 1C server manzili Adminning ishi, Agentga ko'rsatilmaydi.
        if (admin || !com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.secure.relayMode) {
            row(R.string.settings_server, R.string.sg_r_server_sub, R.drawable.ic_server) { navigator().push(ServerSettingsFragment()) }
        }
        row(R.string.sg_diag, R.string.sg_r_diag_sub, R.drawable.ic_info) {
            com.ilxomjon.dur_novvot_dokon.ui.diag.DiagnosticsSheet.show(childFragmentManager)
        }
        row(R.string.up_check, R.string.sg_r_update_sub, R.drawable.ic_download) { (activity as? MainActivity)?.checkUpdate(true) }

        c.addView(Ui.section(ctx, getString(R.string.sg_g_me)))
        row(R.string.pf_title, R.string.sg_r_profile_sub, R.drawable.ic_person) { navigator().push(ProfileFragment()) }

        if (admin) {
            c.addView(Ui.section(ctx, getString(R.string.sg_g_admin)))
            row(R.string.ad_title, R.string.sg_r_admin_sub, R.drawable.ic_tune, 1) {
                navigator().push(com.ilxomjon.dur_novvot_dokon.ui.admin.AdminHubFragment())
            }
        }
        return p.root
    }
}
