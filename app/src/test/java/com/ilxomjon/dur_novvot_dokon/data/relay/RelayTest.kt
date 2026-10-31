package com.ilxomjon.dur_novvot_dokon.data.relay

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.data.local.RawProduct
import com.ilxomjon.dur_novvot_dokon.data.local.RawStore
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerException
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.Group
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.SentLine
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.outbox.FakeClock
import com.ilxomjon.dur_novvot_dokon.domain.outbox.FakeStore
import com.ilxomjon.dur_novvot_dokon.domain.outbox.NotSentException
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEngine
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxKind
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState
import com.ilxomjon.dur_novvot_dokon.domain.outbox.RejectedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.FakeRtdb
import uz.imsoft.shared.Json
import java.nio.file.Files

/**
 * "Admin orqali" ko'prik: Agent (RelayClient) <-> soxta Firebase (qoidalari bilan) <-> Admin (RelayHost) <-> soxta server.
 */
class RelayTest {
    private val secret = "ADMIN-SECRET"
    private val url = "https://test-shop.firebaseio.com"
    private val fake = FakeRtdb(secret)
    private val cfg = RelayCodec.Config(url, CloudDb.newKey())
    private var clock = 1_000_000L

    /** Serverning soxtasi: savollar jadvali (ID, agent, tur, matn). */
    inner class Server : RelayHost.Server {
        val rows = ArrayList<List<String>>()
        var down = false
        var reject = false
        val agentsList = listOf(RawAgent("A7", "Aziz", "Boss", "901234567") to "parol123", RawAgent("A8", "Bek", "Boss", "907654321") to "x")
        private fun check() { if (down) throw ServerException(com.ilxomjon.dur_novvot_dokon.data.remote.ServerError.CONNECT, "down") }
        override suspend fun agentByPhone(phone: String) = run { check(); agentsList.firstOrNull { it.first.phone == phone } }
        override suspend fun agents() = run { check(); agentsList.map { it.first } }
        override suspend fun fetchSnapshot(agentId: String) = run {
            check()
            CatalogSnapshot(
                listOf(Group("G1", "Yong'oq")), listOf("R1" to "Chorsu"),
                listOf(RawStore("R1", "S1", "Dadaxon Aka", "1500000", "0", "")), listOf("12650"),
                listOf(RawProduct("G1", "P1", "Pista", "0", "180000", "0", "1", "40", "", "185000", "0", "150000", "0")), null,
            )
        }
        override suspend fun fetchDebt(agentId: String, storeId: String) = run { check(); "1500000" to "12.5" }
        override suspend fun fetchReport(agentId: String, payload: String) = run { check(); "<b>$agentId $payload</b>" }
        override suspend fun serverTimeMillis() = run { check(); 42L }
        override suspend fun find(userId: String, type: String, payload: String) = run {
            check(); rows.firstOrNull { it[1] == userId && it[2] == type && it[3] == payload }?.get(0)
        }
        override suspend fun insert(userId: String, type: String, payload: String): String {
            check()
            if (reject) throw RejectedException("text too long")
            val id = (1000 + rows.size).toString()
            rows += listOf(id, userId, type, payload)
            return id
        }
    }

    private val server = Server()
    val printed = ArrayList<ByteArray>()
    val mirrored = ArrayList<Pair<RelayCodec.Sale, String>>()
    private var codes: Pair<Long, ByteArray>? = 5L to """{"items":[]}""".toByteArray()

    private val host = RelayHost(
        { cfg }, { u -> CloudDb(u, secret, fake.http()) }, server,
        object : RelayHost.Hooks {
            override fun adminAgentId() = "A0"
            override fun codes() = codes
            override suspend fun printLabels(data: ByteArray): String { printed += data; return "xprinter" }
            override suspend fun mirrorSale(sale: RelayCodec.Sale, key: String) { mirrored += sale to key }
        },
        now = { clock },
    )

    /** Agent kutayotganda Admin "ishlaydi" ([adminOnline]); soat oldinga yuradi. */
    private var adminOnline = true
    private val saleDir = Files.createTempDirectory("relay").toFile()
    private val agent = RelayClient(
        { cfg }, { u -> CloudDb(u, "", fake.http()) }, saleDir,
        sleep = { ms -> clock += ms; if (adminOnline) host.pump() },
        now = { clock },
    )
    private val agentDb = CloudDb(url, "", fake.http())

    private fun order(i: Int) = "A7^S1^31.10.2026 16:0$i:00^0^1^0^0^0^0^12650^1^1~P1^2^180000@"

    @Test fun connectionCode() {
        val code = RelayCodec.encodeCode(cfg)
        assertTrue(code.startsWith("IMSAG1:"))
        assertEquals(cfg, RelayCodec.decodeCode(code))
        // Telegram'dan nusxalashda bo'shliq/qator ko'chishi qo'shilsa ham
        assertEquals(cfg, RelayCodec.decodeCode("  Kod:\n" + code.chunked(10).joinToString(" \n") + "\n"))
        assertNull(RelayCodec.decodeCode("IMSAG1:buzuq"))
        assertNull(RelayCodec.decodeCode("salom"))
        assertNull(RelayCodec.decodeCode(RelayCodec.encodeCode(RelayCodec.Config(url, "qisqa"))))
    }

    @Test fun loginWithoutSendingPassword() = runBlocking {
        val a = agent.login("901234567", "parol123")
        assertNotNull(a)
        assertEquals("A7", a?.id)
        assertNull(agent.login("901234567", "noto'g'ri"))
        assertNull(agent.login("999999999", "parol123"))
        // Parol Firebase'ga hech qachon yozilmaydi
        assertFalse(Json.write(fake.root).contains("parol123"))
        // Javoblar o'qib bo'lingach o'chiriladi, so'rovlar ham qolmaydi
        assertTrue(Json.write(fake.root["g"]).let { !it.contains("\"rq\"") })
    }

    @Test fun catalogFromAdmin() = runBlocking {
        var got: ByteArray? = null
        agent.onCodes = { got = it }
        val s = agent.fetchSnapshot("A7")
        assertEquals("A7", s.agent?.id)
        assertEquals(1, s.products.size)
        assertEquals("185000", s.products[0].cashSom)
        assertEquals("Dadaxon Aka", s.stores[0].name)
        assertEquals(listOf("12650"), s.rates)
        assertTrue(s.fetchedAt > 0)
        assertEquals("""{"items":[]}""", got?.toString(Charsets.UTF_8))
        // Admin hech qachon katalog joylamagan va oflayn — tushunarli xato
        val fake2 = FakeRtdb(secret)
        val lonely = RelayClient({ cfg }, { u -> CloudDb(u, "", fake2.http()) }, null, sleep = { clock += it }, now = { clock })
        try { lonely.fetchSnapshot("A7"); fail() } catch (e: ServerException) { assertEquals("admin_no_catalog", e.message) }
    }

    @Test fun orderWrittenOnceThroughAdmin() = runBlocking {
        val id = agent.insert("A7", "1", order(1))
        assertEquals("1000", id)
        // Qayta yuborilsa (masalan javob yo'qolgan) — o'sha ID, serverda bitta qator
        assertEquals("1000", agent.insert("A7", "1", order(1)))
        assertEquals("1000", agent.find("A7", "1", order(1), null))
        assertEquals(1, server.rows.size)
        assertEquals(order(1), server.rows[0][3])
        assertEquals("1001", agent.insert("A7", "4", "A7^S1^50000^0^31.10.2026 16:05:00^12650^^50000^0^0^1~"))
        assertEquals(2, server.rows.size)
        // Navbat bo'shatildi, natijalar qoldi
        assertNull(fake.root.let { (it["g"] as Map<*, *>)[cfg.key] as Map<*, *> }["ob"])
    }

    @Test fun adminOfflineThenLater() = runBlocking {
        adminOnline = false
        try { agent.insert("A7", "1", order(2)); fail() } catch (e: NotSentException) { assertEquals("admin_wait", e.message) }
        assertNull(agent.find("A7", "1", order(2), null))
        assertEquals(0, server.rows.size)
        // Admin keyinroq ulanadi — agent internetda bo'lmasa ham yozadi
        host.pump()
        assertEquals(1, server.rows.size)
        assertEquals("1000", agent.find("A7", "1", order(2), null))
        // Server ko'rinmasa (Admin ishlayapti, lekin server yo'q) — yozilmaydi, xato holatda ko'rinadi, navbat saqlanadi
        adminOnline = true
        server.down = true
        try { agent.insert("A7", "1", order(3)); fail() } catch (e: NotSentException) { }
        assertTrue(host.stats.error.isNotEmpty())
        server.down = false
        host.pump()
        assertEquals(2, server.rows.size)
    }

    @Test fun adminCrashedAfterInsertNoDuplicate() = runBlocking {
        // Admin serverga yozgan, lekin natijani joylashga ulgurmagan: navbatda turibdi, serverda bor
        server.rows += listOf("777", "A7", "1", order(4))
        adminOnline = false
        runCatching { agent.insert("A7", "1", order(4)) }
        host.pump()
        assertEquals(1, server.rows.size)
        assertEquals("777", agent.find("A7", "1", order(4), null))
    }

    @Test fun rejectedAndTampered() = runBlocking {
        server.reject = true
        try { agent.insert("A7", "1", order(5)); fail() } catch (e: RejectedException) { assertEquals("text too long", e.message) }
        server.reject = false
        // Kalit matnga mos kelmasa — Admin yozmaydi
        val bad = "0".repeat(40)
        agentDb.put(cfg.path("ob/$bad"), linkedMapOf("at" to CloudDb.SERVER_TIME, "u" to "A7", "t" to "1", "p" to order(6)))
        host.pump()
        assertEquals(0, server.rows.size)
        assertEquals("rej", (agentDb.get(cfg.path("obr/$bad")) as Map<*, *>)["st"])
    }

    @Test fun outboxEngineThroughRelay() = runBlocking {
        val store = FakeStore()
        val clk = FakeClock()
        val ids = (1..5).map { store.add(OutboxKind.ORDER, "A7", order(it)) }
        adminOnline = false
        val engine = OutboxEngine(store, agent, clk)
        engine.drain()
        // Admin yo'q: hech biri "yuborildi" emas
        assertTrue(store.all().none { it.state.isDone })
        // Navbatdagilar oldindan Admin'ga joylanadi, Admin keyin yozadi
        agent.pushAll(store.all().map { Triple(it.agentId, it.kind.protocolType, it.payload) })
        host.pump()
        assertEquals(5, server.rows.size)
        repeat(10) { clk.t += OutboxEngine.LEASE_MS + 700_000; engine.drain() }
        for (id in ids) assertEquals(OutboxState.SENT, store.get(id).state)
        assertEquals(5, server.rows.size) // dublikat yo'q
        assertEquals(server.rows.map { it[0] }.toSet(), store.all().mapNotNull { it.serverRowId }.toSet())
    }

    @Test fun requestsDebtReportTime() = runBlocking {
        assertEquals("1500000" to "12.5", agent.fetchDebt("A7", "S1"))
        assertEquals("<b>A7 01.10.2026#31.10.2026#S1</b>", agent.fetchReport("A7", "01.10.2026#31.10.2026#S1"))
        assertEquals(42L, agent.serverTimeMillis())
        adminOnline = false
        try { agent.fetchDebt("A7", "S1"); fail() } catch (e: ServerException) { assertEquals("admin_offline", e.message) }
        // Admin holati (heartbeat)
        adminOnline = true
        host.pump()
        assertNotNull(agent.status())
    }

    @Test fun labelsAndBranchReceipt() = runBlocking {
        assertEquals("xprinter", agent.printLabels(byteArrayOf(1, 2, 3), 2))
        assertEquals(listOf(1, 2, 3), printed.single().map { it.toInt() })
        val sale = SentOrder(0, 1_700_000_000_000L, "S1", "Dadaxon Aka", DealMode.SALE, PriceType.WHOLESALE, Currency.SOM, 360000.0, 0.0, 0.0,
            listOf(SentLine("Pista", 2.0, 180000.0, "P1")))
        agent.rememberSale("A7", order(7), sale, "B-0007")
        agent.insert("A7", "1", order(7))
        val (m, key) = mirrored.single()
        assertEquals("B-0007", m.code)
        assertEquals("S1", m.order.storeId)
        assertEquals(2.0, m.order.lines.single().qty, 0.0)
        assertTrue(key.startsWith("r"))
    }

    @Test fun rulesKeepAgentsInTheirLane() = runBlocking {
        agent.insert("A7", "1", order(8))
        fun denied(block: () -> Unit) = try { block(); false } catch (e: CloudDb.CloudException) { e.denied }
        assertTrue(denied { agentDb.get("g/${cfg.key}") })
        assertTrue(denied { agentDb.get(cfg.path("rq")) })
        assertTrue(denied { agentDb.get(cfg.path("ob")) })
        assertTrue(denied { agentDb.get(cfg.path("obr")) })
        assertTrue(denied { agentDb.put(cfg.path("cat"), "x") })
        assertTrue(denied { agentDb.put(cfg.path("obr/" + "a".repeat(40)), mapOf("st" to "ok", "id" to "1")) })
        // ko'prik kaliti qisqa bo'lsa hech narsa yo'q
        assertTrue(denied { agentDb.get("g/qisqa/cat") })
        // Qoidalar JSON'i to'g'ri va g/ bor
        assertTrue(CloudDb.RULES.contains("\"g\""))
        assertTrue(CloudDb.RULES_VERSION >= 4)
    }
}
