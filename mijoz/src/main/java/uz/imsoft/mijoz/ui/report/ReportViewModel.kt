package uz.imsoft.mijoz.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import uz.imsoft.mijoz.Locator
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Stamp
import java.util.Calendar

enum class Period { TODAY, WEEK, MONTH, LAST_MONTH, DAYS90, ALL, CUSTOM }

sealed interface ReportItem {
    class DayHeader(val day: ClientStats.Day) : ReportItem
    class Row(val e: LedgerEntry) : ReportItem
}

data class ReportState(
    val items: List<ReportItem> = emptyList(),
    val totals: ClientStats.Totals? = null,
    val from: Long = 0,
    val to: Long = 0,
    val tab: Int = 0,
    val period: Period = Period.MONTH,
    val hasData: Boolean = false,
    val entries: List<LedgerEntry> = emptyList(),
)

class ReportViewModel : ViewModel() {
    val tab = MutableStateFlow(0)
    val period = MutableStateFlow(Period.MONTH)
    val custom = MutableStateFlow(0L to 0L)

    val state: StateFlow<ReportState> = combine(Locator.data.state, tab, period, custom) { d, t, p, c ->
        val all = d.snapshot?.ledger.orEmpty()
        val today = todayStamp()
        val (from, to) = when (p) {
            Period.TODAY -> ClientStats.Period.today(today)
            Period.WEEK -> ClientStats.Period.thisWeek(today, mondayIndex())
            Period.MONTH -> ClientStats.Period.thisMonth(today)
            Period.LAST_MONTH -> ClientStats.Period.lastMonth(today)
            Period.DAYS90 -> ClientStats.Period.lastDays(today, 90)
            Period.ALL -> 0L to Long.MAX_VALUE
            Period.CUSTOM -> c
        }
        val inPeriod = all.filter { it.stamp in from..to }
        val shown = inPeriod.filter { e ->
            when (t) {
                0 -> e.kind == EntryKind.SALE || e.kind == EntryKind.RETURN || e.kind == EntryKind.DISCOUNT
                1 -> e.kind == EntryKind.PAYMENT || e.kind == EntryKind.PAYMENT_RETURN
                else -> true
            }
        }
        val items = ArrayList<ReportItem>()
        for (day in ClientStats.byDay(shown)) {
            items += ReportItem.DayHeader(day)
            day.entries.forEach { items += ReportItem.Row(it) }
        }
        ReportState(items, ClientStats.totals(inPeriod), from, to, t, p, d.snapshot != null, shown)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, ReportState())

    private fun todayStamp(): Long {
        val c = Calendar.getInstance()
        return Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Dushanba = 1 ... Yakshanba = 7. */
    private fun mondayIndex(): Int {
        val d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (d == Calendar.SUNDAY) 7 else d - 1
    }
}
