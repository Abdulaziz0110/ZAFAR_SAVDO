package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.remote.Protocol
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi
import java.util.Date

class ReportRepository(private val api: ServerApi, private val catalog: CatalogRepository) {
    suspend fun load(from: Date, to: Date, storeId: String): String {
        val agent = catalog.state.value.agent ?: throw IllegalStateException("Agent topilmadi")
        return api.fetchReport(agent.id, Protocol.report(from, to, storeId))
    }
}
