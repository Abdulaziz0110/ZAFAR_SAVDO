package com.ilxomjon.dur_novvot_dokon.data.relay

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi

/** Admin: ko'prik serverga Admin'ning o'z (sinalgan) ulanishi orqali yozadi — protokol va jadvallar o'zgarmaydi. */
class RelayServerAdapter(private val api: ServerApi) : RelayHost.Server {
    override suspend fun agentByPhone(phone: String): Pair<RawAgent, String>? = api.agentByPhone(phone)
    override suspend fun agents(): List<RawAgent> = api.agents()
    override suspend fun fetchSnapshot(agentId: String): CatalogSnapshot = api.fetchSnapshot(agentId)
    override suspend fun fetchDebt(agentId: String, storeId: String): Pair<String, String> = api.fetchDebt(agentId, storeId)
    override suspend fun fetchReport(agentId: String, payload: String): String = api.fetchReport(agentId, payload)
    override suspend fun serverTimeMillis(): Long = api.serverTimeMillis()
    override suspend fun find(userId: String, type: String, payload: String): String? = api.find(userId, type, payload, null)
    override suspend fun insert(userId: String, type: String, payload: String): String = api.insert(userId, type, payload)
}
