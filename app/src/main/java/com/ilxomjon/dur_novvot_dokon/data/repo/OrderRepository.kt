package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.remote.Protocol
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.SentLine
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import java.util.Date

class CheckoutInput(val discount: Double, val pay: Protocol.Payments, val printer: String)

/**
 * Buyurtma va to'lovni navbatga qo'yadi (zumda qaytadi). Serverga yuborish orqa fonda, [OutboxRepository] orqali.
 */
class OrderRepository(
    private val catalog: CatalogRepository,
    private val draft: DraftRepository,
    private val outbox: OutboxRepository,
) {
    suspend fun sendOrder(input: CheckoutInput) {
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
        )
        outbox.enqueueOrder(agent.id, payload, sent)
        draft.finish()
        outbox.kick()
    }

    suspend fun sendPayment(storeId: String, storeName: String, cash: Double, transfer: Double, click: Double, note: String, printer: String) {
        val agent = catalog.state.value.agent ?: throw IllegalStateException("Agent topilmadi")
        val now = Date()
        val payload = Protocol.payment(agent.id, storeId, now, catalog.state.value.rateText, note, cash, transfer, click, printer)
        val sent = SentPayment(0, now.time, storeId, storeName, cash, transfer, click, Protocol.cleanText(note), pending = true)
        outbox.enqueuePayment(agent.id, payload, sent)
        outbox.kick()
    }
}
