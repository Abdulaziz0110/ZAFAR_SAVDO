package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.data.local.RawProduct
import com.ilxomjon.dur_novvot_dokon.data.local.RawStore
import com.ilxomjon.dur_novvot_dokon.domain.Group
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

/** Serverdagi jadvallar bilan ishlash. Barcha so'rovlar PreparedStatement orqali (SQL injection yo'q). */
class ServerApi(private val server: SqlServer) {

    /** Ulanishni tekshiradi va qaysi manzil orqali ulanganini qaytaradi. */
    suspend fun testConnection(cfg: ServerConfig): String {
        server.withConnection(cfg) { conn -> withContext(Dispatchers.IO) { conn.createStatement().use { it.execute("SELECT 1") } } }
        return server.lastHost.orEmpty()
    }

    suspend fun login(phone: String, password: String): RawAgent? = server.withConnection { conn ->
        withContext(Dispatchers.IO) {
            wrap {
                conn.prepareStatement("SELECT agent_id, fio, boshagan, telefon, parol FROM agentlar WHERE telefon = ? AND parol = ?").use { ps ->
                    ps.setString(1, phone)
                    ps.setString(2, password)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) RawAgent(rs.s(1), rs.s(2), rs.s(3), rs.s(4)) else null
                    }
                }
            }
        }
    }

    suspend fun fetchSnapshot(agentId: String): CatalogSnapshot = server.withConnection { conn ->
        withContext(Dispatchers.IO) {
            wrap {
                val groups = query(conn, "SELECT gruppa_id, gruppa_nomi FROM gruppa") { Group(it.s(1), it.s(2)) }
                val regions = query(conn, "SELECT region_id, region_nomi FROM region") { it.s(1) to it.s(2) }
                val stores = query(conn, "SELECT region_id, kontr_id, kontr_nomi, qarzi_som, qarzi_val, time FROM kontragentlar") {
                    RawStore(it.s(1), it.s(2), it.s(3), it.s(4), it.s(5), it.s(6))
                }
                val rates = query(conn, "SELECT xozirgi_kurs FROM xozirgi_kurs") { it.s(1) }
                val products = query(
                    conn,
                    "SELECT gruppa_id, tovar_id, nomi, valyuta_turi, sotish_narxi_optom_som, sotish_narxi_optom_val, blokdagi_soni, qoldigi, time, " +
                        "sotish_narxi_chakana_som, sotish_narxi_chakana_val, kirim_narxi_som, kirim_narxi_val FROM tovar",
                ) {
                    RawProduct(
                        it.s(1), it.s(2), it.s(3), it.s(4), it.s(5), it.s(6), it.s(7), it.s(8), it.s(9),
                        it.s(10), it.s(11), it.s(12), it.s(13),
                    )
                }
                val agent = conn.prepareStatement("SELECT agent_id, fio, boshagan, telefon, parol FROM agentlar WHERE agent_id = ?").use { ps ->
                    ps.setString(1, agentId)
                    ps.executeQuery().use { rs -> if (rs.next()) RawAgent(rs.s(1), rs.s(2), rs.s(3), rs.s(4)) else null }
                }
                CatalogSnapshot(groups, regions, stores, rates, products, agent)
            }
        }
    }

    /** Buyurtmani serverga yozadi (javob kutilmaydi, avvalgi ilovadagidek). */
    suspend fun sendOrder(agentId: String, payload: String, checkDuplicate: Boolean = false) =
        sendRequest(agentId, Protocol.TYPE_ORDER, payload, checkDuplicate)

    suspend fun sendPayment(agentId: String, payload: String, checkDuplicate: Boolean = false) =
        sendRequest(agentId, Protocol.TYPE_PAYMENT, payload, checkDuplicate)

    /** Do'kon qarzini so'raydi: javob "so'm:dollar". */
    suspend fun fetchDebt(agentId: String, storeId: String): Pair<String, String> {
        val answer = request(agentId, Protocol.TYPE_DEBT, storeId, timeoutMs = 20_000)
        return Protocol.parseDebt(answer)
    }

    suspend fun fetchReport(agentId: String, payload: String): String =
        Protocol.reportHtml(request(agentId, Protocol.TYPE_REPORT, payload, timeoutMs = 60_000))

    /**
     * [checkDuplicate] - qayta urinishda: oldingi urinish serverga yetib borgan, faqat javob yo'qolgan bo'lishi mumkin.
     * Shunda oxirgi so'rovlar orasida aynan shu matn bor-yo'qligi tekshiriladi (buyurtma ikki marta tushmasligi uchun).
     */
    private suspend fun sendRequest(agentId: String, type: String, payload: String, checkDuplicate: Boolean) {
        server.withConnection { conn ->
            withContext(Dispatchers.IO) {
                wrap {
                    if (!(checkDuplicate && alreadySent(conn, agentId, type, payload))) insertRequest(conn, agentId, type, payload)
                }
            }
        }
    }

    private fun alreadySent(conn: Connection, userId: String, type: String, payload: String): Boolean {
        conn.prepareStatement("SELECT TOP 30 sorov_izox FROM savollar WHERE user_id = ? AND sorov_turi = ? ORDER BY ID DESC").use { ps ->
            ps.setString(1, userId)
            ps.setString(2, type)
            ps.executeQuery().use { rs ->
                while (rs.next()) if (rs.getString(1) == payload) return true
            }
        }
        return false
    }

    private suspend fun request(agentId: String, type: String, payload: String, timeoutMs: Long): String =
        server.withConnection { conn ->
            val id = withContext(Dispatchers.IO) { wrap { insertRequest(conn, agentId, type, payload) } }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val answer = withContext(Dispatchers.IO) { wrap { readAnswer(conn, id) } }
                if (answer != null) return@withConnection answer
                delay(POLL_MS)
            }
            throw ServerException(ServerError.TIMEOUT, "Server javob bermadi")
        }

    private fun insertRequest(conn: Connection, userId: String, type: String, payload: String): String {
        conn.prepareStatement("INSERT INTO savollar (user_id, sorov_turi, sorov_izox) output INSERTED.ID VALUES (?, ?, ?)").use { ps ->
            ps.setString(1, userId)
            ps.setString(2, type)
            ps.setString(3, payload)
            ps.executeQuery().use { rs ->
                if (!rs.next()) throw ServerException(ServerError.QUERY, "So'rov ID olinmadi")
                return rs.getString(1)
            }
        }
    }

    private fun readAnswer(conn: Connection, requestId: String): String? {
        conn.prepareStatement("SELECT javob FROM javoblar WHERE sorov_id = ?").use { ps ->
            ps.setString(1, requestId)
            ps.executeQuery().use { rs ->
                var v: String? = null
                while (rs.next()) v = rs.getString(1)
                return v?.takeIf { it.isNotBlank() && it != "no" }
            }
        }
    }

    private fun <T> query(conn: Connection, sql: String, map: (ResultSet) -> T): List<T> {
        val out = ArrayList<T>()
        conn.createStatement().use { st -> st.executeQuery(sql).use { rs -> while (rs.next()) out += map(rs) } }
        return out
    }

    private fun ResultSet.s(i: Int): String = getString(i) ?: ""

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: SQLException) {
        throw ServerException(ServerError.QUERY, e.message, e)
    }

    private companion object { const val POLL_MS = 500L }
}
