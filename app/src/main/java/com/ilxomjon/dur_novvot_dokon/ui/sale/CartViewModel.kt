package com.ilxomjon.dur_novvot_dokon.ui.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilxomjon.dur_novvot_dokon.data.repo.DraftRepository
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CartViewModel(private val draft: DraftRepository) : ViewModel() {
    val state: StateFlow<DraftState> = draft.state

    fun remove(line: CartLine) { viewModelScope.launch { draft.removeLine(line.productId) } }
    fun restore(line: CartLine) { viewModelScope.launch { draft.restore(line) } }
    fun clear() { viewModelScope.launch { draft.clearLines() } }
}
