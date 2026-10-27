package com.ilxomjon.dur_novvot_dokon.data.repo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.local.OutboxCounts
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEngine
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEntry
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Buyurtma va to'lovlar avval lokal navbatga yoziladi (zumda), keyin [OutboxEngine] orqali yuboriladi.
 * Yuborish ikki yo'l bilan ishga tushadi: ilova ichida (darhol) va WorkManager (ilova yopiq bo'lsa ham, internet paydo bo'lganda).
 * Ikkalasi bitta engine va bazadagi atomik egallashdan foydalanadi — bir qator ikki marta yuborilmaydi.
 */
class OutboxRepository(
    private val context: Context,
    private val db: HistoryDb,
    private val api: ServerApi,
    private val scope: CoroutineScope,
) {
    val engine = OutboxEngine(db, api, { System.currentTimeMillis() })

    private val _counts = MutableStateFlow(OutboxCounts())
    val counts: StateFlow<OutboxCounts> = _counts.asStateFlow()

    /** Eski API bilan moslik: hali serverga yozilmaganlar soni. */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _lastError = MutableStateFlow<Throwable?>(null)
    val lastError: StateFlow<Throwable?> = _lastError.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Zaxira/tiklash paytida yuborish to'xtatiladi (bazalar yopiladi). */
    private val gate = Mutex()

    suspend fun <T> paused(block: suspend () -> T): T = gate.withLock { block() }

    /** Oxirgi muvaffaqiyatli serverga yozish vaqti (diagnostika uchun). */
    @Volatile var lastSuccessAt: Long = 0L
        private set

    suspend fun refreshCount() {
        val c = withContext(Dispatchers.IO) { db.counts() }
        _counts.value = c
        _pending.value = c.unsent
    }

    suspend fun enqueueOrder(agentId: String, payload: String, order: SentOrder): Long {
        val id = withContext(Dispatchers.IO) { db.enqueueOrder(agentId, payload, order) }
        refreshCount()
        return id
    }

    suspend fun enqueuePayment(agentId: String, payload: String, payment: SentPayment): Long {
        val id = withContext(Dispatchers.IO) { db.enqueuePayment(agentId, payload, payment) }
        refreshCount()
        return id
    }

    /** Navbatni darhol yuborishga urinadi va WorkManager'ni ham rejalashtiradi. Ishlayotgan yuborishni TO'XTATMAYDI. */
    fun kick() {
        schedule(context)
        scope.launch { drainNow() }
    }

    /** "Hozir yuborish": kutish vaqtlarini bekor qiladi (RETRY_WAIT/UNCERTAIN darhol), lekin hech narsani yarim yo'lda kesmaydi. */
    fun retryNow() {
        scope.launch {
            withContext(Dispatchers.IO) { db.writableDatabase.execSQL("UPDATE outbox SET next_at = 0 WHERE state IN ('RETRY_WAIT','UNCERTAIN')") }
            drainNow()
        }
        schedule(context)
    }

    /** Engine'ni bajaradi (worker ham shuni chaqiradi). Hech qachon tashqariga xato chiqarmaydi. */
    suspend fun drainNow(): OutboxEngine.Result {
        _sending.value = true
        return try {
            val r = gate.withLock { withContext(Dispatchers.IO) { engine.drain() } }
            if (r.sent > 0) lastSuccessAt = System.currentTimeMillis()
            _lastError.value = r.lastError
            r
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            _lastError.value = t
            OutboxEngine.Result(0, true, t)
        } finally {
            _sending.value = false
            runCatching { refreshCount() }
        }
    }

    // ---------------- qo'lda amallar ----------------

    fun list(): List<OutboxEntry> = db.outboxList()

    /** "Serverda bormi tekshir": butun jadvaldan aynan shu matnni qidiradi (faqat SELECT). */
    suspend fun checkOnServer(id: Long): Boolean {
        val e = withContext(Dispatchers.IO) { db.entry(id) } ?: return false
        if (e.state.isDone) return true
        val found = api.find(e.agentId, e.kind.protocolType, e.payload, null)
        if (found != null) withContext(Dispatchers.IO) { db.markSent(id, found, System.currentTimeMillis()) }
        refreshCount()
        return found != null
    }

    /** Agent roziligi bilan: probe'siz bir marta qayta yuborish (dublikat xavfi agentga aytilgan). */
    suspend fun forceResend(id: Long) {
        withContext(Dispatchers.IO) { db.markForceSend(id) }
        refreshCount()
        kick()
    }

    /** Agent ofis bilan tekshirib "serverda bor" dedi. */
    suspend fun markSentManually(id: Long) {
        withContext(Dispatchers.IO) { db.markManualSent(id) }
        refreshCount()
    }

    fun statesFor(ids: Collection<Long>): Map<Long, OutboxState> = db.statesFor(ids)

    companion object {
        private const val UNIQUE_NOW = "outbox_now"
        private const val UNIQUE_PERIODIC = "outbox_periodic"

        /** Internet paydo bo'lganda yuborish + har 15 daqiqada zaxira tekshiruv. Ikkalasi ham unique — bir nechta nusxa bo'lmaydi. */
        fun schedule(context: Context) {
            runCatching {
                val wm = WorkManager.getInstance(context)
                val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                wm.enqueueUniqueWork(
                    UNIQUE_NOW, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<OutboxWorker>().setConstraints(net)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
                )
                wm.enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<OutboxWorker>(15, TimeUnit.MINUTES).setConstraints(net).build(),
                )
            }
        }
    }
}

/** Fon ishchisi: ilova yopiq bo'lsa ham navbatni yuboradi. */
class OutboxWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.ensureInit(applicationContext)
        val outbox = ServiceLocator.outbox
        val r = outbox.drainNow()
        outbox.refreshCount()
        val c = outbox.counts.value
        // NEEDS_CHECK qatorlari avtomatik yuborilmaydi — ular uchun qayta urinish shart emas.
        val waiting = c.ready + c.retry + c.uncertain + c.sending
        return if (r.retryLater && waiting > 0 && runAttemptCount < 50) Result.retry() else Result.success()
    }
}
