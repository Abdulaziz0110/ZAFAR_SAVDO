package com.ilxomjon.dur_novvot_dokon.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.LocalReportRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.ReportRepository
import com.ilxomjon.dur_novvot_dokon.domain.LocalReport
import com.ilxomjon.dur_novvot_dokon.domain.Store
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

enum class ReportSource { SERVER, LOCAL }

data class ReportState(
    val source: ReportSource = ReportSource.SERVER,
    val store: Store? = null,
    val fromMillis: Long = startOfToday(),
    val toMillis: Long = startOfToday(),
    val loading: Boolean = false,
    val html: String? = null,
    /** Serverdan olingan bo'lsa — qachon. */
    val loadedAt: Long = 0L,
    val noStore: Boolean = false,
    val error: Throwable? = null,
    val local: LocalReportRepository.Data? = null,
)

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

class ReportViewModel(
    private val catalog: CatalogRepository,
    private val reports: ReportRepository,
    private val localReports: LocalReportRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()
    private var job: Job? = null

    fun selectStore(id: String) {
        val s = catalog.storeById(id) ?: return
        _state.update { it.copy(store = s, noStore = false) }
    }

    fun clearStore() = _state.update { it.copy(store = null) }

    fun setPeriod(from: Long, to: Long) = _state.update { it.copy(fromMillis = from, toMillis = to) }

    fun setSource(s: ReportSource) {
        if (s == _state.value.source) return
        cancel()
        _state.update { it.copy(source = s, html = null, error = null, local = null, noStore = false) }
    }

    /** Server hisobotini kutishni to'xtatish (60 s gacha kutishi mumkin). */
    fun cancel() {
        job?.cancel()
        _state.update { it.copy(loading = false) }
    }

    fun load(labels: LocalReport.Labels, title: String) {
        val s = _state.value
        if (s.loading) return
        val to = Calendar.getInstance().apply { timeInMillis = s.toMillis; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        if (s.source == ReportSource.LOCAL) {
            _state.update { it.copy(loading = true, error = null) }
            job = viewModelScope.launch {
                try {
                    val d = localReports.load(s.fromMillis, to, s.store?.id)
                    _state.update { it.copy(loading = false, local = d, html = LocalReport.html(d.summary, title, labels)) }
                } catch (e: Exception) {
                    _state.update { it.copy(loading = false, error = e, html = null) }
                }
            }
            return
        }
        val store = s.store ?: return _state.update { it.copy(noStore = true) }
        _state.update { it.copy(loading = true, error = null, noStore = false) }
        job = viewModelScope.launch {
            try {
                val html = reports.load(Date(s.fromMillis), Date(s.toMillis), store.id)
                _state.update { it.copy(loading = false, html = html, loadedAt = System.currentTimeMillis()) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e, html = null) }
            }
        }
    }
}
