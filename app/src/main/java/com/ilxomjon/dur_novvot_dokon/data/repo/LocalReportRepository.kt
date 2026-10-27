package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.domain.LocalReport
import com.ilxomjon.dur_novvot_dokon.domain.LocalSummary
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEntry
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxKind
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lokal (telefondagi) hisobot va CSV eksport. Serverga hech narsa so'ralmaydi. */
class LocalReportRepository(private val db: HistoryDb) {

    class Data(val orders: List<SentOrder>, val payments: List<SentPayment>, val states: Map<Long, OutboxState>, val summary: LocalSummary)

    /** [to] — kiritilmaydi (masalan ertangi kun boshi). */
    suspend fun load(from: Long, to: Long, storeId: String?): Data = withContext(Dispatchers.IO) {
        val orders = db.ordersBetween(from, to, storeId)
        val payments = db.paymentsBetween(from, to, storeId)
        val states = db.statesFor((orders.map { it.outboxId } + payments.map { it.outboxId }).filter { it > 0 })
        Data(orders, payments, states, LocalReport.summarize(from, to, orders, payments))
    }

    fun receipt(o: SentOrder) = if (o.outboxId > 0) OutboxEntry.receiptOf(OutboxKind.ORDER, o.outboxId) else "—"
    fun receipt(p: SentPayment) = if (p.outboxId > 0) OutboxEntry.receiptOf(OutboxKind.PAYMENT, p.outboxId) else "—"
}
