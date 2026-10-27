package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerError
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerException
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Buyurtma va to'lovlar avval lokal navbatga yoziladi (zumda), keyin orqa fonda serverga yuboriladi.
 * Internet yo'q bo'lsa navbatda turadi va avtomatik qayta uriniladi: mijoz hech narsani kutmaydi, ma'lumot yo'qolmaydi.
 */
class OutboxRepository(
    private val db: HistoryDb,
    private val api: ServerApi,
    private val scope: CoroutineScope,
) {
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()
    private val _lastError = MutableStateFlow<Throwable?>(null)
    val lastError: StateFlow<Throwable?> = _lastError.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private var job: Job? = null

    suspend fun refreshCount() {
        _pending.value = withContext(Dispatchers.IO) { db.pendingCount() }
    }

    suspend fun enqueueOrder(agentId: String, payload: String, order: SentOrder) {
        withContext(Dispatchers.IO) { db.enqueueOrder(agentId, payload, order) }
        refreshCount()
    }

    suspend fun enqueuePayment(agentId: String, payload: String, payment: SentPayment) {
        withContext(Dispatchers.IO) { db.enqueuePayment(agentId, payload, payment) }
        refreshCount()
    }

    /** Navbatni yuborishni boshlaydi (allaqachon ishlayotgan bo'lsa hech narsa qilmaydi). */
    fun kick() {
        if (job?.isActive == true) return
        job = scope.launch { drain() }
    }

    /** "Hozir yuborish": kutish vaqtini bekor qilib darhol qayta urinadi. */
    fun retryNow() {
        job?.cancel()
        job = scope.launch { drain() }
    }

    private suspend fun drain() {
        _sending.value = true
        try {
            while (true) {
                val item = withContext(Dispatchers.IO) { db.nextPending() }
                if (item == null) { _lastError.value = null; break }
                try {
                    val retry = item.attempts > 0   // qayta urinishda takroriy yozuv tekshiriladi
                    if (item.kind == "order") api.sendOrder(item.agentId, item.payload, retry)
                    else api.sendPayment(item.agentId, item.payload, retry)
                    withContext(Dispatchers.IO) { db.complete(item.id) }
                    _lastError.value = null
                    refreshCount()
                } catch (e: ServerException) {
                    withContext(Dispatchers.IO) { db.markAttempt(item.id, e.message) }
                    _lastError.value = e
                    if (e.kind == ServerError.NOT_CONFIGURED) break
                    delay(RETRY_MS)
                }
            }
        } finally {
            _sending.value = false
            refreshCount()
        }
    }

    private companion object { const val RETRY_MS = 20_000L }
}
