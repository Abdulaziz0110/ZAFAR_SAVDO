package uz.imsoft.mijoz.ui.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uz.imsoft.mijoz.Locator
import uz.imsoft.shared.SearchIndex
import uz.imsoft.shared.SearchOptions
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.ClientSnapshot
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.PriceItem

class PriceRow(val item: PriceItem, val key: String, val last: ClientStats.Purchase?, val ranges: List<IntRange>)

data class PricesState(
    val rows: List<PriceRow> = emptyList(),
    val groups: List<String> = emptyList(),
    val group: String = "",
    val searching: Boolean = false,
    val approximate: Boolean = false,
    val hasData: Boolean = false,
    val pricesAt: Long = 0L,
)

@OptIn(FlowPreview::class)
class PricesViewModel : ViewModel() {
    val query = MutableStateFlow("")
    val group = MutableStateFlow("")

    private class Prepared(val snap: ClientSnapshot?, val index: SearchIndex<PriceItem>, val last: Map<String, ClientStats.Purchase>, val groups: List<String>)

    private val prepared = Locator.data.state.map { it.snapshot }.distinctUntilChanged { a, b -> a === b }.map { snap ->
        val items = snap?.products.orEmpty()
        Prepared(snap, SearchIndex(items, { it.name }, { it.id }, { if (it.plu.isEmpty()) it.group else it.group + " " + it.plu }), ClientStats.lastPurchases(snap?.ledger.orEmpty()),
            items.map { it.group }.filter { it.isNotEmpty() }.distinct().sorted())
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<PricesState> = combine(prepared, query.debounce { if (it.isEmpty()) 0L else 120L }, group) { p, q, g ->
        val validGroup = if (g.isNotEmpty() && g !in p.groups) "" else g
        val hits = p.index.search(q, SearchOptions(fuzzy = Locator.prefs.fuzzy, layoutFix = true))
        val rows = hits.filter { validGroup.isEmpty() || it.item.group == validGroup }.map {
            val k = LedgerMerge.nameKey(it.item.name)
            PriceRow(it.item, k, p.last[k], it.ranges)
        }
        val sorted = if (q.isBlank()) rows.sortedBy { it.item.name.lowercase() } else rows
        PricesState(sorted, p.groups, validGroup, q.isNotBlank(), q.isNotBlank() && hits.isNotEmpty() && hits.all { it.approximate }, p.snap != null, p.snap?.pricesAt ?: 0L)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, PricesState())
}
