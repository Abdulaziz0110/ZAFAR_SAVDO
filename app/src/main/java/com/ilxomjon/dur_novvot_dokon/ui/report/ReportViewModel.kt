package com.ilxomjon.dur_novvot_dokon.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.ReportRepository
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

data class ReportState(
    val store: Store? = null,
    val fromMillis: Long = startOfToday(),
    val toMillis: Long = startOfToday(),
    val loading: Boolean = false,
    val html: String? = null,
    val noStore: Boolean = false,
    val error: Throwable? = null,
)

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

class ReportViewModel(
    private val catalog: CatalogRepository,
    private val reports: ReportRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    fun selectStore(id: String) {
        val s = catalog.storeById(id) ?: return
        _state.update { it.copy(store = s, noStore = false) }
    }

    fun setPeriod(from: Long, to: Long) = _state.update { it.copy(fromMillis = from, toMillis = to) }

    fun load() {
        val s = _state.value
        if (s.loading) return
        val store = s.store ?: return _state.update { it.copy(noStore = true) }
        _state.update { it.copy(loading = true, error = null, noStore = false) }
        viewModelScope.launch {
            try {
                val html = reports.load(Date(s.fromMillis), Date(s.toMillis), store.id)
                _state.update { it.copy(loading = false, html = html) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e, html = null) }
            }
        }
    }
}
