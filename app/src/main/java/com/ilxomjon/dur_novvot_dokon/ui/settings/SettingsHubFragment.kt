package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import com.ilxomjon.dur_novvot_dokon.BuildConfig
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.MainActivity
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui

/**
 * v5.3 "Boshqaruv / Sozlamalar" — bitta qoida:
 *   * BO'LIM sahifasi — har doim KATTA PLITKALAR panjarasi (ikonka, yirik nom, bir qator izoh);
 *   * plitka bosilsa — yo ish ekrani (ro'yxat/shakl), yo yana shunday plitkali bo'lim ochiladi.
 * Ro'yxat ichida "ba'zisi sozlama, ba'zisi yana ro'yxat" degan chalkashlik yo'q.
 *
 * Bir xil ishlar bitta joyga yig'ilgan: Mahsulotlar (yaratish, narx, ma'lumot, rasm, yorliq), Mijozlar va
 * Yuk beruvchilar (to'lov, qaytarish, tarix — qatorning o'zida). Olib tashlandi: dollar kursi, kassa kirim/chiqim,
 * chek ko'rinishi, server va ko'prik sozlamalari (server — faqat sirli menyuda).
 */
class SettingsHubFragment : CodeFragment() {
    override fun build(inflater: LayoutInflater): View {
        val admin = AppRole.isAdmin
        val p = page(if (admin) R.string.nav_manage else R.string.settings_title)
        val c = p.content
        val cols = HubTiles.columns(this)
        for ((title, tiles) in HubTiles.root(this, admin)) {
            c.addView(Ui.section(ctx, getString(title)))
            c.addView(Ui.bigTiles(ctx, tiles, cols, 4))
        }
        // Versiya: uzoq bosilsa — sirli menyu (server manzili va parollar faqat o'sha yerda)
        val ver = Ui.hint(ctx, getString(R.string.sg_version, BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"), 20).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 24))
            setOnLongClickListener { navigator().push(AgentSecretFragment()); true }
        }
        c.addView(ver)
        return p.root
    }
}

/** Ichki bo'lim: yana plitkalar (Hisobotlar, Yorliq va tarozi, Qulayliklar, Ilova). */
class SubHubFragment : CodeFragment() {
    private val key: String get() = arguments?.getString(ARG).orEmpty()

    override fun build(inflater: LayoutInflater): View {
        val (title, tiles) = HubTiles.sub(this, key)
        val p = page(title)
        p.content.addView(Ui.bigTiles(ctx, tiles, HubTiles.columns(this), 8))
        if (key == HubTiles.REPORTS) writesInfo(p)
        return p.root
    }

    private fun writesInfo(p: Ui.Page) {
        val tv = Ui.hint(ctx, "", 8)
        p.content.addView(tv)
        launchOnStarted {
            ServiceLocator.onec.state.collect { o ->
                tv.text = o.sales?.let { getString(R.string.ad_sales_st, it.docs.size, com.ilxomjon.dur_novvot_dokon.ui.formatDateTime(it.at)) } ?: getString(R.string.ad_none_yet)
            }
        }
    }

    companion object {
        private const val ARG = "hub"
        fun of(key: String) = SubHubFragment().apply { arguments = Bundle().apply { putString(ARG, key) } }
    }
}

/** Plitkalar ro'yxati — bitta joyda (har ish faqat bir marta uchraydi). */
object HubTiles {
    const val REPORTS = "reports"
    const val LABELS = "labels"
    const val COMFORT = "comfort"
    const val APP = "app"

    /** Tor telefonda 2, keng ekranda (planshet, yotiq) 3 ustun. */
    fun columns(f: Fragment): Int = if (f.resources.configuration.screenWidthDp >= 600) 3 else 2

    private fun tile(f: CodeFragment, title: Int, sub: Int, icon: Int, tone: Int = 0, go: () -> Unit) =
        Ui.Tile(f.getString(title), icon, f.getString(sub), tone, go)

    private fun open(f: CodeFragment, make: () -> Fragment): () -> Unit = { f.navigator().push(make()) }

    fun root(f: CodeFragment, admin: Boolean): List<Pair<Int, List<Ui.Tile>>> {
        val shop = ArrayList<Ui.Tile>()
        shop += tile(f, R.string.pb_title, if (admin) R.string.hub_products_sub else R.string.hub_products_sub_staff, R.drawable.ic_shopping_bag, 1, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.ProductsFragment() })
        if (admin) {
            shop += tile(f, R.string.hub_clients, R.string.hub_clients_sub, R.drawable.ic_person, 1, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.PartiesFragment.of(com.ilxomjon.dur_novvot_dokon.ui.admin.PartiesFragment.CLIENTS) })
            shop += tile(f, R.string.sp_title, R.string.hub_suppliers_sub, R.drawable.ic_truck, 1, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.PartiesFragment.of(com.ilxomjon.dur_novvot_dokon.ui.admin.PartiesFragment.SUPPLIERS) })
            shop += tile(f, R.string.wr_title, R.string.hub_writes_sub, R.drawable.ic_computer, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.WritesFragment() })
            shop += tile(f, R.string.hub_reports, R.string.hub_reports_sub, R.drawable.ic_trending, 0, open(f) { SubHubFragment.of(REPORTS) })
            shop += tile(f, R.string.hub_labels, R.string.hub_labels_sub, R.drawable.ic_label, 0, open(f) { SubHubFragment.of(LABELS) })
            shop += tile(f, R.string.pn_title, R.string.hub_panel_sub, R.drawable.ic_widgets, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.PanelFragment() })
        } else {
            shop += tile(f, R.string.hub_clients, R.string.hub_clients_sub_staff, R.drawable.ic_person, 1, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.PartiesFragment.of(com.ilxomjon.dur_novvot_dokon.ui.admin.PartiesFragment.CLIENTS) })
            shop += tile(f, R.string.sg_queue, R.string.hub_queue_sub, R.drawable.ic_sync_alt, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxFragment() })
            shop += tile(f, R.string.hub_scanner, R.string.hub_scanner_sub, R.drawable.ic_qr, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodesFragment() })
        }
        val out = ArrayList<Pair<Int, List<Ui.Tile>>>()
        out += R.string.hub_g_shop to shop
        if (admin) {
            out += R.string.hub_g_people to listOf(
                tile(f, R.string.hub_staff, R.string.hub_staff_sub, R.drawable.ic_groups, 2, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.DevicesFragment() }),
                tile(f, R.string.hub_client_app, R.string.hub_client_app_sub, R.drawable.ic_storefront, 2, open(f) { com.ilxomjon.dur_novvot_dokon.ui.share.ShareFragment() }),
                tile(f, R.string.ad_requests, R.string.hub_requests_sub, R.drawable.ic_notifications, 2, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.ClientRequestsFragment() }),
                tile(f, R.string.ad_pc, R.string.hub_pc_sub, R.drawable.ic_computer, 2, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.PcFragment() }),
            )
        }
        out += R.string.hub_g_app to listOf(
            tile(f, R.string.hub_look, R.string.hub_look_sub, R.drawable.ic_palette, 0, open(f) { LookFragment() }),
            tile(f, R.string.sg_extras, R.string.hub_comfort_sub, R.drawable.ic_tune, 0, open(f) { SubHubFragment.of(COMFORT) }),
            tile(f, R.string.hub_app, R.string.hub_app_sub, R.drawable.ic_download, 0, open(f) { SubHubFragment.of(APP) }),
            tile(f, R.string.pf_title, R.string.hub_me_sub, R.drawable.ic_person, 0, open(f) { ProfileFragment() }),
        )
        return out
    }

    fun sub(f: CodeFragment, key: String): Pair<Int, List<Ui.Tile>> = when (key) {
        REPORTS -> R.string.hub_reports to listOf(
            tile(f, R.string.boss_title, R.string.hub_boss_sub, R.drawable.ic_crown, 1, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.BossFragment() }),
            tile(f, R.string.stt_title, R.string.hub_stats_sub, R.drawable.ic_trending, 1, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.StatsFragment() }),
            tile(f, R.string.hub_client_stats, R.string.hub_client_stats_sub, R.drawable.ic_groups, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.ClientsStatsFragment() }),
            tile(f, R.string.rp_title, R.string.hub_1c_reports_sub, R.drawable.ic_summarize, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.ReportsFragment() }),
            tile(f, R.string.ad_sales, R.string.hub_1c_sales_sub, R.drawable.ic_receipt, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.OneCSalesFragment() }),
            tile(f, R.string.dayend_title, R.string.hub_dayend_sub, R.drawable.ic_report, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.report.DayEndFragment() }),
        )
        LABELS -> R.string.hub_labels to listOf(
            tile(f, R.string.ad_labels, R.string.hub_price_labels_sub, R.drawable.ic_label, 1, open(f) { com.ilxomjon.dur_novvot_dokon.ui.labels.LabelsFragment() }),
            tile(f, R.string.sc_title, R.string.hub_scale_sub, R.drawable.ic_scale, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.admin.ScaleFragment() }),
            tile(f, R.string.bb_title, R.string.hub_batch_sub, R.drawable.ic_barcode, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment() }),
            tile(f, R.string.hub_scanner, R.string.hub_scanner_sub, R.drawable.ic_qr, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodesFragment() }),
        )
        COMFORT -> R.string.sg_extras to listOf(
            tile(f, R.string.hub_sale_comfort, R.string.hub_sale_comfort_sub, R.drawable.ic_tune, 1, open(f) { com.ilxomjon.dur_novvot_dokon.ui.extras.ExtrasFragment() }),
            tile(f, R.string.sg_fav, R.string.hub_fav_sub, R.drawable.ic_store, 0, open(f) { SettingsFragment() }),
            tile(f, R.string.wgs_title, R.string.hub_widget_sub, R.drawable.ic_widgets, 0, open(f) { WidgetFragment() }),
        )
        else -> R.string.hub_app to buildList {
            add(tile(f, R.string.up_check, R.string.hub_update_sub, R.drawable.ic_download, 1) { (f.activity as? MainActivity)?.checkUpdate(true) })
            add(tile(f, R.string.sg_diag, R.string.hub_diag_sub, R.drawable.ic_info, 0) { com.ilxomjon.dur_novvot_dokon.ui.diag.DiagnosticsSheet.show(f.childFragmentManager) })
            if (AppRole.isAdmin) {
                add(tile(f, R.string.au_title, R.string.hub_backup_sub, R.drawable.ic_backup, 0, open(f) { AutoUpdateFragment() }))
                add(tile(f, R.string.sg_queue, R.string.hub_queue_sub, R.drawable.ic_sync_alt, 0, open(f) { com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxFragment() }))
            }
        }
    }
}
