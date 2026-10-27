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
}
