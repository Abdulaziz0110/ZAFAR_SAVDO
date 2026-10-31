package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig

/** v3.0: sozlamaga qarab to'g'ridan-to'g'ri server yoki "Admin orqali" ([useRelay]). */
class SwitchingBackend(
    private val direct: Backend,
    private val relay: Backend,
    private val useRelay: () -> Boolean,
) : Backend {
    private fun b(): Backend = if (useRelay()) relay else direct

    override suspend fun testConnection(cfg: ServerConfig): String = b().testConnection(cfg)
    override suspend fun login(phone: String, password: String): RawAgent? = b().login(phone, password)
    override suspend fun fetchSnapshot(agentId: String): CatalogSnapshot = b().fetchSnapshot(agentId)
    override suspend fun fetchDebt(agentId: String, storeId: String): Pair<String, String> = b().fetchDebt(agentId, storeId)
    override suspend fun fetchReport(agentId: String, payload: String): String = b().fetchReport(agentId, payload)
    override suspend fun serverTimeMillis(): Long = b().serverTimeMillis()
    override suspend fun maxRequestId(userId: String): Long = b().maxRequestId(userId)
    override suspend fun insert(userId: String, type: String, payload: String): String = b().insert(userId, type, payload)
    override suspend fun find(userId: String, type: String, payload: String, afterId: Long?): String? = b().find(userId, type, payload, afterId)
    override suspend fun exists(requestId: String): Boolean = b().exists(requestId)
}
