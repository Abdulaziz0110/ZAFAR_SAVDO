package com.ilxomjon.dur_novvot_dokon.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.DocHit
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerFilter
import uz.imsoft.shared.report.LedgerIndex
import uz.imsoft.shared.report.LedgerResult
import uz.imsoft.shared.report.Stamp
import java.util.Calendar

enum class KindFilter(val kinds: Set<EntryKind>?) {
    ALL(null),
    SALES(setOf(EntryKind.SALE)),
    RETURNS(setOf(EntryKind.RETURN)),
    PAYMENTS(setOf(EntryKind.PAYMENT, EntryKind.PAYMENT_RETURN)),
}

enum class PeriodFilter { ALL, TODAY, DAYS7, MONTH, LAST_MONTH, DAYS90, CUSTOM }

sealed class LedgerRow {
    data class Day(val day: Long, val count: Int) : LedgerRow()
    class Doc(val hit: DocHit) : LedgerRow()
}

class LedgerUiState(
    val rows: List<LedgerRow> = emptyList(),
    val result: LedgerResult? = null,
    val query: String = "",
    val kind: KindFilter = KindFilter.ALL,
    val period: PeriodFilter = PeriodFilter.ALL,
    val from: Long = 0L,
    val to: Long = Long.MAX_VALUE,
    val hasData: Boolean = false,
    val loaded: Boolean = false,
    val clientName: String = "",
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LedgerViewModel : ViewModel() {
    val query = MutableStateFlow("")
    val kind = MutableStateFlow(KindFilter.ALL)
    val period = MutableStateFlow(PeriodFilter.ALL)
    val custom = MutableStateFlow(0L to Long.MAX_VALUE)
    val fuzzy = MutableStateFlow(true)
    private val data = MutableStateFlow(LedgerData(emptyList(), "", -1L, false))

    private var index: LedgerIndex? = null
    private var indexVersion = Long.MIN_VALUE

    fun setData(d: LedgerData) { data.value = d }

    private data class Input(val d: LedgerData, val q: String, val k: KindFilter, val p: PeriodFilter, val c: Pair<Long, Long>, val fz: Boolean)

    val state: StateFlow<LedgerUiState> = combine(data, query.debounce(120), kind, period, custom) { d, q, k, p, c -> Input(d, q, k, p, c, true) }
        .combine(fuzzy) { i, fz -> i.copy(fz = fz) }
        .mapLatest { compute(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState())

    private fun today(): Long {
        val c = Calendar.getInstance()
        return Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun range(p: PeriodFilter, custom: Pair<Long, Long>, today: Long): Pair<Long, Long> = when (p) {
        PeriodFilter.ALL -> 0L to Long.MAX_VALUE
        PeriodFilter.TODAY -> ClientStats.Period.today(today)
        PeriodFilter.DAYS7 -> ClientStats.Period.lastDays(today, 7)
        PeriodFilter.MONTH -> ClientStats.Period.thisMonth(today)
        PeriodFilter.LAST_MONTH -> ClientStats.Period.lastMonth(today)
        PeriodFilter.DAYS90 -> ClientStats.Period.lastDays(today, 90)
        PeriodFilter.CUSTOM -> custom
    }

    private fun compute(i: Input): LedgerUiState {
        val cached = index
        val idx = if (cached == null || indexVersion != i.d.version) {
            LedgerIndex(i.d.entries).also { index = it; indexVersion = i.d.version }
        } else cached
        val today = today()
        val (from, to) = range(i.p, i.c, today)
        val res = idx.search(i.q, today, LedgerFilter(i.k.kinds, from, to), i.fz)
        val rows = ArrayList<LedgerRow>(res.hits.size + 32)
        var curDay = -1L
        var dayStart = 0
        for (h in res.hits) {
            val day = Stamp.dayStart(h.doc.stamp)
            if (day != curDay) {
                if (curDay >= 0) rows[dayStart] = LedgerRow.Day(curDay, rows.size - dayStart - 1)
                curDay = day
                dayStart = rows.size
                rows += LedgerRow.Day(day, 0)
            }
            rows += LedgerRow.Doc(h)
        }
        if (curDay >= 0) rows[dayStart] = LedgerRow.Day(curDay, rows.size - dayStart - 1)
        return LedgerUiState(rows, res, i.q, i.k, i.p, from, to, idx.docs.isNotEmpty(), i.d.loaded, i.d.clientName)
    }

    fun docByKey(key: String) = index?.byKey(key)
    fun entries() = data.value.entries
}
