package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistoryRepository(private val db: HistoryDb) {
    suspend fun orders(): List<SentOrder> = withContext(Dispatchers.IO) { db.orders() }
    suspend fun payments(): List<SentPayment> = withContext(Dispatchers.IO) { db.payments() }
    suspend fun recentStoreIds(): List<String> = withContext(Dispatchers.IO) { db.recentStoreIds() }
    suspend fun storeOrders(storeId: String): List<SentOrder> = withContext(Dispatchers.IO) { db.ordersForStore(storeId) }
    suspend fun recentSaleOrders(): List<SentOrder> = withContext(Dispatchers.IO) { db.recentSaleOrders() }
    suspend fun feedback(storeId: String): Map<String, Pair<String, Long>> = withContext(Dispatchers.IO) { db.feedback(storeId) }
    suspend fun addFeedback(storeId: String, productId: String, reason: String) = withContext(Dispatchers.IO) { db.addFeedback(storeId, productId, reason) }
    /** outbox_id -> holat (Tarix ekrani uchun). */
    suspend fun states(ids: Collection<Long>) = withContext(Dispatchers.IO) { db.statesFor(ids) }
}
