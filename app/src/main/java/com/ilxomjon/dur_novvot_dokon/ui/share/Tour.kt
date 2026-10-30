package com.ilxomjon.dur_novvot_dokon.ui.share

import androidx.fragment.app.Fragment
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.ui.ledger.CoachMarks
import com.ilxomjon.dur_novvot_dokon.ui.ledger.CoachMarks.Step

/** 1-ilova ekranlaridagi "ishlatayotganda o'rgatish" qadamlari (dvigatel — [CoachMarks]). */
object Tour {
    const val HOME = "home"
    const val SALE = "sale"
    const val HISTORY = "history"
    const val SHARE = "share"
    const val CLIENT = "client"
    const val SETTINGS = "settings"

    private val steps: Map<String, List<Step>> = mapOf(
        HOME to listOf(
            Step(R.id.cardSale, R.string.tour_home_sale_t, R.string.tour_home_sale),
            Step(R.id.cardLedger, R.string.tour_home_ledger_t, R.string.tour_home_ledger),
            Step(R.id.btnDayEnd, R.string.tour_home_dayend_t, R.string.tour_home_dayend),
        ),
        SALE to listOf(
            Step(R.id.rowStore, R.string.tour_sale_store_t, R.string.tour_sale_store),
            Step(R.id.tilSearch, R.string.tour_sale_search_t, R.string.tour_sale_search),
            Step(R.id.btnScan, R.string.tour_sale_scan_t, R.string.tour_sale_scan),
            Step(R.id.recycler, R.string.tour_sale_plus_t, R.string.tour_sale_plus),
            Step(R.id.btnCart, R.string.tour_sale_cart_t, R.string.tour_sale_cart),
        ),
        HISTORY to listOf(
            Step(R.id.tabs, R.string.tour_hist_tabs_t, R.string.tour_hist_tabs),
        ),
        SHARE to listOf(
            Step(R.id.btnAddClient, R.string.tour_share_add_t, R.string.tour_share_add),
            Step(R.id.btnImportFile, R.string.tour_share_import_t, R.string.tour_share_import),
            Step(R.id.tgPc, R.string.tour_share_pc_t, R.string.tour_share_pc),
            Step(R.id.btnOrders, R.string.tour_share_orders_t, R.string.tour_share_orders),
        ),
        CLIENT to listOf(
            Step(R.id.btnAddMember, R.string.tour_client_member_t, R.string.tour_client_member),
            Step(R.id.btnImport, R.string.tour_client_import_t, R.string.tour_client_import),
            Step(R.id.btnPrinter, R.string.tour_client_printer_t, R.string.tour_client_printer),
        ),
        SETTINGS to listOf(
            Step(R.id.btnShare, R.string.tour_set_share_t, R.string.tour_set_share),
            Step(R.id.toggleTheme, R.string.tour_set_theme_t, R.string.tour_set_theme),
        ),
    )

    fun show(f: Fragment, key: String, force: Boolean = false) = CoachMarks.show(f, key, steps[key].orEmpty(), force)
}
