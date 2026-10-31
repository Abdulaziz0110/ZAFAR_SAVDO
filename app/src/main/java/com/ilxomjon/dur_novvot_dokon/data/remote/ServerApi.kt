package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.data.local.RawProduct
import com.ilxomjon.dur_novvot_dokon.data.local.RawStore
import com.ilxomjon.dur_novvot_dokon.domain.Group
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import com.ilxomjon.dur_novvot_dokon.domain.outbox.NotSentException
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxSink
import com.ilxomjon.dur_novvot_dokon.domain.outbox.RejectedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

/** Serverdagi jadvallar bilan ishlash. Barcha so'rovlar PreparedStatement orqali (SQL injection yo'q). */
class ServerApi(private val server: SqlServer) : Backend {

    /** Ulanishni tekshiradi va qaysi manzil orqali ulanganini qaytaradi. */
    override suspend fun testConnection(cfg: ServerConfig): String {
        server.withConnection(cfg) { conn -> withContext(Dispatchers.IO) { conn.createStatement().use { it.execute("SELECT 1") } } }
        return server.lastHost.orEmpty()
    }

    override suspend fun login(phone: String, password: String): RawAgent? = server.withConnection { conn ->
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

    override suspend fun fetchSnapshot(agentId: String): CatalogSnapshot = server.withConnection { conn ->
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

    /**
     * v3.0 Admin ko'prigi uchun: telefon bo'yicha agent va uning serverdagi paroli (faqat Admin ichida, isbot hisoblash uchun —
     * parol hech qayerga yuborilmaydi). Faqat o'qish.
     */
    suspend fun agentByPhone(phone: String): Pair<RawAgent, String>? = server.withConnection { conn ->
        withContext(Dispatchers.IO) {
            wrap {
                conn.prepareStatement("SELECT agent_id, fio, boshagan, telefon, parol FROM agentlar WHERE telefon = ?").use { ps ->
                    ps.setString(1, phone)
                    ps.executeQuery().use { rs -> if (rs.next()) RawAgent(rs.s(1), rs.s(2), rs.s(3), rs.s(4)) to rs.s(5) else null }
                }
            }
        }
    }

    /** v3.0: hamma agentlar (parolsiz) — Admin katalogga qo'shadi, agent o'z qatorini topadi. */
    suspend fun agents(): List<RawAgent> = server.withConnection { conn ->
        withContext(Dispatchers.IO) {
            wrap { query(conn, "SELECT agent_id, fio, boshagan, telefon FROM agentlar") { RawAgent(it.s(1), it.s(2), it.s(3), it.s(4)) } }
        }
    }

    /** Do'kon qarzini so'raydi: javob "so'm:dollar". */
    override suspend fun fetchDebt(agentId: String, storeId: String): Pair<String, String> {
        val answer = request(agentId, Protocol.TYPE_DEBT, storeId, timeoutMs = 20_000)
        return Protocol.parseDebt(answer)
    }

    override suspend fun fetchReport(agentId: String, payload: String): String =
        Protocol.reportHtml(request(agentId, Protocol.TYPE_REPORT, payload, timeoutMs = 60_000))

    /** Server vaqti (diagnostika va qurilma soatini tekshirish uchun). Faqat o'qish. */
    override suspend fun serverTimeMillis(): Long = server.withConnection { conn ->
        withContext(Dispatchers.IO) {
            wrap { conn.createStatement().use { st -> st.executeQuery("SELECT GETDATE()").use { rs -> rs.next(); rs.getTimestamp(1).time } } }
        }
    }

    // ---------------- OutboxSink: navbat yadrosi uchun (faqat mavjud savollar jadvali) ----------------

    override suspend fun maxRequestId(userId: String): Long = sinkRead { conn ->
        conn.prepareStatement("SELECT ISNULL(MAX(ID), 0) FROM savollar WHERE user_id = ?").use { ps ->
            ps.setString(1, userId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
        }
    }

    override suspend fun insert(userId: String, type: String, payload: String): String {
        var started = false
        try {
            return server.withConnection { conn ->
                withContext(Dispatchers.IO) {
                    started = true
                    insertRequest(conn, userId, type, payload)
                }
            }
        } catch (e: ServerException) {
            // Ulanishning o'zi ochilmadi -> so'rov serverga yetib bormagani aniq.
            if (!started) throw NotSentException(e.message, e)
            throw e
        } catch (e: SQLException) {
            if (!started) throw NotSentException(e.message, e)
            if (isRejected(e)) throw RejectedException(e.message, e)
            throw e
        }
    }

    override suspend fun find(userId: String, type: String, payload: String, afterId: Long?): String? = sinkRead { conn ->
        if (afterId != null) {
            scan(conn, "SELECT TOP 5000 ID, sorov_izox FROM savollar WHERE user_id = ? AND sorov_turi = ? AND ID > ? ORDER BY ID", userId, type, afterId, payload)
        } else {
            val exact = try {
                conn.prepareStatement("SELECT TOP 1 ID FROM savollar WHERE user_id = ? AND sorov_turi = ? AND sorov_izox = ? ORDER BY ID DESC").use { ps ->
                    ps.setString(1, userId); ps.setString(2, type); ps.setString(3, payload)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
                }
            } catch (e: SQLException) {
                null // ustun turi '=' ni qo'llamasa (masalan text) — pastdagi qidiruv
            }
            exact ?: scan(conn, "SELECT TOP 2000 ID, sorov_izox FROM savollar WHERE user_id = ? AND sorov_turi = ? ORDER BY ID DESC", userId, type, null, payload)
        }
    }

    override suspend fun exists(requestId: String): Boolean = sinkRead { conn ->
        conn.prepareStatement("SELECT 1 FROM savollar WHERE ID = ?").use { ps ->
            ps.setString(1, requestId)
            ps.executeQuery().use { it.next() }
        }
    }

    private fun scan(conn: Connection, sql: String, userId: String, type: String, afterId: Long?, payload: String): String? {
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, userId); ps.setString(2, type)
            if (afterId != null) ps.setLong(3, afterId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val text = rs.getString(2) ?: continue
                    if (PayloadMatch.same(payload, text)) return rs.getString(1)
                }
            }
        }
        return null
    }

    /** Faqat o'qish: ulanish ochilmasa [NotSentException] (ishchi navbatni to'xtatadi), boshqa xatolar o'zicha. */
    private suspend fun <T> sinkRead(block: (Connection) -> T): T {
        var opened = false
        try {
            return server.withConnection { conn -> opened = true; withContext(Dispatchers.IO) { block(conn) } }
        } catch (e: ServerException) {
            if (!opened) throw NotSentException(e.message, e)
            throw e
        }
    }

    /**
     * Server so'rovni bajarmasdan rad etdimi (matn uzunligi, huquq va h.k.)? Aloqa uzilishi (08xxx, I/O, timeout) — rad etish EMAS:
     * u holda INSERT bajarilgan bo'lishi mumkin.
     */
    private fun isRejected(e: SQLException): Boolean {
        val state = e.sqlState.orEmpty()
        val msg = e.message.orEmpty()
        if (state.startsWith("08") || state.startsWith("HYT") || msg.contains("I/O Error", true) || msg.contains("timed out", true) ||
            msg.contains("reset", true) || msg.contains("closed", true)
        ) return false
        return e.errorCode != 0
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
