package com.ilxomjon.dur_novvot_dokon.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.DraftRepository
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.CartWarning
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class CartViewModel(private val draft: DraftRepository) : ViewModel() {
    val state: StateFlow<DraftState> = draft.state
    private val _warnings = MutableStateFlow<List<CartWarning>>(emptyList())
    val warnings: StateFlow<List<CartWarning>> = _warnings.asStateFlow()
    private var ctx: CheckerSupport.Ctx? = null

    init {
        viewModelScope.launch {
            draft.state.map { it.store?.id.orEmpty() }.distinctUntilChanged().collect { id ->
                ctx = if (id.isEmpty()) null else CheckerSupport.load(id)
                recheck()
            }
        }
        viewModelScope.launch { draft.state.collect { recheck() } }
    }

    private fun recheck() { _warnings.value = CheckerSupport.warnings(draft.state.value, ctx, includeMissing = false) }

    fun keepAsIs() { CheckerSupport.dismiss(_warnings.value.map { it.key }); recheck() }

    fun remove(line: CartLine) { viewModelScope.launch { draft.removeLine(line.productId) } }
    fun restore(line: CartLine) { viewModelScope.launch { draft.restore(line) } }

    /** @return "Bekor qilish" uchun oldingi qatorlar */
    fun clear(): List<CartLine> { val before = draft.state.value.lines; draft.clearLines(); return before }
    fun undo(before: List<CartLine>) = draft.replaceLines(before)
}
