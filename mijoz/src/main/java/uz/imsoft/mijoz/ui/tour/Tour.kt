package uz.imsoft.mijoz.ui.tour

import androidx.fragment.app.Fragment
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.ledger.CoachMarks
import uz.imsoft.mijoz.ui.ledger.CoachMarks.Step

/** 2-ilova ekranlaridagi "ishlatayotganda o'rgatish" qadamlari (dvigatel — [CoachMarks], 1-ilova bilan umumiy). */
object Tour {
    const val HOME = "m_home"
    const val PRICES = "m_prices"
    const val SETTINGS = "m_settings"

    private val steps: Map<String, List<Step>> = mapOf(
        HOME to listOf(
            Step(R.id.tvDebt, R.string.tour_m_debt_t, R.string.tour_m_debt),
            Step(R.id.btnRefresh, R.string.tour_m_refresh_t, R.string.tour_m_refresh),
            Step(R.id.btnOrder, R.string.tour_m_order_t, R.string.tour_m_order),
            Step(R.id.btnReport, R.string.tour_m_report_t, R.string.tour_m_report),
        ),
        PRICES to listOf(
            Step(R.id.tilSearch, R.string.tour_m_search_t, R.string.tour_m_search),
            Step(R.id.toggleType, R.string.tour_m_type_t, R.string.tour_m_type),
            Step(R.id.recycler, R.string.tour_m_add_t, R.string.tour_m_add),
        ),
        SETTINGS to listOf(
            Step(R.id.tgAuto, R.string.tour_m_auto_t, R.string.tour_m_auto),
            Step(R.id.tgTheme, R.string.tour_m_theme_t, R.string.tour_m_theme),
        ),
    )

    fun show(f: Fragment, key: String, force: Boolean = false) = CoachMarks.show(f, key, steps[key].orEmpty(), force)
}
