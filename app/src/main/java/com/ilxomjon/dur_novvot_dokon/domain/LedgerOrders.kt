package com.ilxomjon.dur_novvot_dokon.domain

import uz.imsoft.shared.Fold
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.LedgerOwner
import uz.imsoft.shared.report.PendingMerge

/**
 * v5.0: 1C tarixidagi savdolarni taklif algoritmi tushunadigan ko'rinishga o'tkazadi.
 *
 * Avval tavsiya FAQAT shu telefonda qilingan savdolardan o'rganardi — do'kon kassasidagi yoki boshqa agentning
 * savdosi hisobga olinmasdi ("mijoz hamma olgan narsasi ko'rinmaydi" muammosi). Endi mijozning butun tarixi
 * (1C hujjatlari + telefon) birga o'rganiladi.
 */
object LedgerOrders {
    /** Tovar nomi bo'yicha katalogdagi kodni topish uchun kalit. */
    fun key(name: String): String = Fold.key(name).replace(" ", "")

    /**
     * [entries] — butun tarix (LedgerOwner bilan belgilangan), [storeId] — mijoz. Har hujjat bitta buyurtma bo'ladi.
     * [idOf] — tovar nomi -> katalogdagi kod (topilmasa qator tashlanadi, chunki taklif kod bo'yicha ishlaydi).
     */
    fun of(entries: List<LedgerEntry>, storeId: String, idOf: (String) -> String?, currency: Currency = Currency.SOM): List<SentOrder> {
        if (storeId.isEmpty()) return emptyList()
        class Acc(val stamp: Long) {
            val lines = ArrayList<SentLine>()
            var total = 0.0
        }
        val docs = LinkedHashMap<String, Acc>()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.product.isBlank() || e.qty == 0.0) continue
            if (LedgerOwner.of(e.kindText)?.first != storeId) continue
            val id = idOf(e.product) ?: continue
            val docKey = "${e.stamp}|${LedgerOwner.plain(e.kindText)}"
            val a = docs.getOrPut(docKey) { Acc(e.stamp) }
            val price = e.v(Col.PRICE).let { if (it.som != 0.0) it.som else it.usd }
            val qty = kotlin.math.abs(e.qty)
            a.lines += SentLine(e.product, qty, price, id)
            a.total += qty * price
        }
        var n = 0L
        return docs.values.map { a ->
            SentOrder(
                id = --n, sentAt = PendingMerge.millisOf(a.stamp), storeId = storeId, storeName = "",
                mode = DealMode.SALE, priceType = PriceType.WHOLESALE, currency = currency,
                total = a.total, discount = 0.0, paid = 0.0, lines = a.lines,
            )
        }.sortedByDescending { it.sentAt }
    }

    /** Mijoz shu tovarni qachon, qancha va qaysi narxda olgan — eng yangisi birinchi (savdo oynasidagi kichik tarix). */
    class Buy(val stamp: Long, val qty: Double, val price: Double)

    fun buys(entries: List<LedgerEntry>, storeId: String, product: String, limit: Int = 5): List<Buy> {
        if (storeId.isEmpty() || product.isBlank()) return emptyList()
        val want = key(product)
        val out = ArrayList<Buy>()
        for (e in entries) {
            if (e.kind != EntryKind.SALE || e.qty == 0.0 || key(e.product) != want) continue
            if (LedgerOwner.of(e.kindText)?.first != storeId) continue
            val p = e.v(Col.PRICE).let { if (it.som != 0.0) it.som else it.usd }
            out += Buy(e.stamp, kotlin.math.abs(e.qty), p)
        }
        return out.sortedByDescending { it.stamp }.take(limit)
    }

    /** Telefon va 1C tarixini birlashtiradi: bir xil vaqt va summadagi hujjat ikki marta hisoblanmasin. */
    fun merge(own: List<SentOrder>, fromLedger: List<SentOrder>): List<SentOrder> {
        if (fromLedger.isEmpty()) return own
        if (own.isEmpty()) return fromLedger
        val out = ArrayList<SentOrder>(own)
        for (o in fromLedger) {
            val dup = own.any { s ->
                s.mode == DealMode.SALE && kotlin.math.abs(s.sentAt - o.sentAt) < 6 * 60 * 60_000L &&
                    kotlin.math.abs(s.total - o.total) <= maxOf(1.0, kotlin.math.abs(o.total) * 0.005)
            }
            if (!dup) out += o
        }
        return out.sortedByDescending { it.sentAt }
    }
}
