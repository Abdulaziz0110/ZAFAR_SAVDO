package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxSink

/**
 * Ilova serverdan nima oladi va nima yuboradi (v3.0). Ikki yo'l:
 *  - [ServerApi] — to'g'ridan-to'g'ri SQL serverga (do'kon Wi-Fi'si yoki Tailscale);
 *  - [com.ilxomjon.dur_novvot_dokon.data.relay.RelayClient] — "Admin orqali": faqat internet, server ishini Admin bajaradi.
 */
interface Backend : OutboxSink {
    suspend fun testConnection(cfg: ServerConfig): String
    suspend fun login(phone: String, password: String): RawAgent?
    suspend fun fetchSnapshot(agentId: String): CatalogSnapshot
    suspend fun fetchDebt(agentId: String, storeId: String): Pair<String, String>
    suspend fun fetchReport(agentId: String, payload: String): String
    suspend fun serverTimeMillis(): Long
}
