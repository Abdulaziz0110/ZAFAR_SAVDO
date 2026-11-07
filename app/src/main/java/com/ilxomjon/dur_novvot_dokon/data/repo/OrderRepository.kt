package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.remote.Protocol
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.SentLine
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEntry
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

class CheckoutInput(val discount: Double, val pay: Protocol.Payments, val printer: String)

/**
 * Buyurtma va to'lovni navbatga qo'yadi (zumda qaytadi). Serverga yuborish orqa fonda, [OutboxRepository] orqali.
 * Vaqt muhri va matn shu yerda BIR MARTA yaratiladi va o'zgarmaydi (qayta urinishda ham).
 */
class OrderRepository(
    private val catalog: CatalogRepository,
    private val draft: DraftRepository,
    private val outbox: OutboxRepository,
    private val kpi: KpiTracker,
) {
    /** v2.6: savdo navbatga qo'yilgach (mijozning 2-ilovasiga ko'zgu). Xatosi savdoga ta'sir qilmaydi. */
    var onOrderQueued: ((SentOrder, Long, String) -> Unit)? = null

    /** v3.0: savdo matni bilan (Admin orqali ishlaganda 2-filial cheki ko'zgusi uchun). Sinxron, tez. */
    var onOrderPayload: ((String, String, SentOrder, String) -> Unit)? = null
    /** v3.2: to'lov navbatga qo'yildi (to'lov, navbat id, kvitansiya kodi) — 2-ilovada darhol ko'rinishi uchun. */
    var onPaymentQueued: ((SentPayment, Long, String) -> Unit)? = null

    /** Ikki marta bosish / ikki ekrandan bir vaqtda yuborishdan himoya. */
    private val sendLock = Mutex()

    /** @return kvitansiya kodi (masalan "B-0412") */
    suspend fun sendOrder(input: CheckoutInput): String = sendLock.withLock {
        val d = draft.state.value
        val store = d.store ?: throw IllegalStateException("Do'kon tanlanmagan")
        val agent = catalog.state.value.agent ?: throw IllegalStateException("Agent topilmadi")
        if (d.lines.isEmpty()) throw IllegalStateException("Savatcha bo'sh")
        val now = Date()
        val payload = Protocol.order(
            agentId = agent.id, storeId = store.id, time = now, currency = d.currency, priceType = d.priceType,
            pay = input.pay, discount = input.discount, rate = catalog.state.value.rateText, mode = d.mode,
            printer = input.printer, lines = d.lines,
        )
        val paid = if (d.currency == Currency.SOM) input.pay.cashSom + input.pay.card else input.pay.cashUsd
        val sent = SentOrder(
            id = 0, sentAt = now.time, storeId = store.id, storeName = store.name, mode = d.mode, priceType = d.priceType,
            currency = d.currency, total = d.total, discount = input.discount, paid = paid,
            lines = d.lines.map { SentLine(it.name, it.qty, it.price, it.productId) }, pending = true,
            cash = if (d.currency == Currency.SOM) input.pay.cashSom else input.pay.cashUsd, card = input.pay.card,
        )
        val id = outbox.enqueueOrder(agent.id, payload, sent)
        // Savatcha darhol (sinxron) tozalanadi: jarayon shu yerda o'lsa ham, yuborilgan savatcha qayta paydo bo'lmasin.
        draft.finishNow()
        kpi.finish(sent.lines.size)
        val code = OutboxEntry.receiptOf(OutboxKind.ORDER, id)
        // v3.0 "Admin orqali": chek ko'zgusi savdo bilan birga Admin'ga borishi uchun yuborishdan OLDIN yoziladi
        runCatching { onOrderPayload?.invoke(agent.id, payload, sent, code) }
        outbox.kick()
        runCatching { onOrderQueued?.invoke(sent, id, code) }
        code
    }

    /** @return kvitansiya kodi (masalan "T-0013") */
    suspend fun sendPayment(storeId: String, storeName: String, cash: Double, transfer: Double, click: Double, note: String, printer: String): String =
        OutboxEntry.receiptOf(OutboxKind.PAYMENT, sendPaymentId(storeId, storeName, cash, transfer, click, note, printer))

    /** v3.2: navbat id'si bilan (2-ilova to'lov so'rovi holatini kuzatish uchun). */
    suspend fun sendPaymentId(storeId: String, storeName: String, cash: Double, transfer: Double, click: Double, note: String, printer: String): Long =
        sendLock.withLock {
            val agent = catalog.state.value.agent ?: throw IllegalStateException("Agent topilmadi")
            val now = Date()
            val payload = Protocol.payment(agent.id, storeId, now, catalog.state.value.rateText, note, cash, transfer, click, printer)
            val sent = SentPayment(0, now.time, storeId, storeName, cash, transfer, click, Protocol.cleanText(note), pending = true)
            val id = outbox.enqueuePayment(agent.id, payload, sent)
            outbox.kick()
            runCatching { onPaymentQueued?.invoke(sent, id, OutboxEntry.receiptOf(OutboxKind.PAYMENT, id)) }
            id
        }
}
