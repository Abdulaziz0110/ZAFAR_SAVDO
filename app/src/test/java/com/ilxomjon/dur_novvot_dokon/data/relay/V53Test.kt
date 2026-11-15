package com.ilxomjon.dur_novvot_dokon.data.relay

import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.data.remote.AutoBackend
import com.ilxomjon.dur_novvot_dokon.data.remote.Backend
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerError
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerException
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import com.ilxomjon.dur_novvot_dokon.domain.outbox.NotSentException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.FakeRtdb

/** v5.3: rol kirishdan, yo'lni ilova o'zi tanlaydi, ko'prikni kompyuter yuritadi. */
class V53Test {
    // ---------------- rol ----------------

    @Test fun ownerPhoneInAnyFormat() {
        assertTrue(AppRole.isOwner("+998907788510"))
        assertTrue(AppRole.isOwner("998 90 778-85-10"))
        assertTrue(AppRole.isOwner("907788510"))
        assertTrue(AppRole.isOwner("(90) 778 85 10"))
        assertFalse(AppRole.isOwner("+998907788511"))
        assertFalse(AppRole.isOwner("7788510"))
        assertFalse(AppRole.isOwner(""))
    }

    @Test fun roleDecision() {
        assertTrue(AppRole.decide("admin", legacyInstall = false))
        assertFalse(AppRole.decide("agent", legacyInstall = true))
        // avvalgi Admin ilovasi ustiga yangilanish — Admin bo'lib qoladi; yangi o'rnatish — kirguncha xodim
        assertTrue(AppRole.decide(null, legacyInstall = true))
        assertFalse(AppRole.decide(null, legacyInstall = false))
    }

    // ---------------- yo'l tanlash ----------------

    private class Fake(val name: String) : Backend {
        var down = false
        var unknown = false
        val inserted = ArrayList<String>()
        var calls = 0
        private fun gate() { calls++; if (down) throw ServerException(ServerError.CONNECT, "down") }
        override suspend fun testConnection(cfg: ServerConfig) = run { gate(); name }
        override suspend fun login(phone: String, password: String): RawAgent? = run { gate(); RawAgent(name, "", "", phone) }
        override suspend fun fetchSnapshot(agentId: String): CatalogSnapshot = run { gate(); CatalogSnapshot(emptyList(), emptyList(), emptyList(), listOf(name), emptyList(), null) }
        override suspend fun fetchDebt(agentId: String, storeId: String) = run { gate(); name to "0" }
        override suspend fun fetchReport(agentId: String, payload: String) = run { gate(); name }
        override suspend fun serverTimeMillis() = run { gate(); 1L }
        override suspend fun maxRequestId(userId: String): Long { calls++; if (down) throw NotSentException("down"); return 77L }
        override suspend fun insert(userId: String, type: String, payload: String): String {
            calls++
            if (down) throw NotSentException("down")
            if (unknown) throw ServerException(ServerError.QUERY, "uzildi")
            inserted += payload
            return "$name-1"
        }
        override suspend fun find(userId: String, type: String, payload: String, afterId: Long?): String? {
            calls++
            if (down) throw NotSentException("down")
            return if (payload in inserted) "$name-1" else null
        }
        override suspend fun exists(requestId: String): Boolean { calls++; if (down) throw NotSentException("down"); return true }
    }

    private class Rig {
        val direct = Fake("server")
        val cloud = Fake("bulut")
        var wifi = true
        var directReady = true
        var cloudReady = true
        var clock = 0L
        val api = AutoBackend(direct, cloud, { directReady }, { cloudReady }, { wifi }, { clock })
    }

    @Test fun wifiGoesDirect() = runBlocking {
        val r = Rig()
        assertEquals("server", r.api.fetchDebt("a", "s").first)
        assertEquals(AutoBackend.Route.DIRECT, r.api.lastRoute)
        assertEquals(0, r.cloud.calls)
    }

    @Test fun mobileInternetGoesCloudWithoutWaiting() = runBlocking {
        val r = Rig().apply { wifi = false }
        assertEquals("bulut", r.api.fetchDebt("a", "s").first)
        assertEquals(0, r.direct.calls)
        assertTrue(r.api.usingCloud)
    }

    @Test fun serverDownFallsBackAndBacksOff() = runBlocking {
        val r = Rig().apply { direct.down = true }
        assertEquals("bulut", r.api.fetchSnapshot("a").rates.single())
        assertEquals(1, r.direct.calls)
        // 2 daqiqa ichida serverni qayta sinab o'tirmaydi
        assertEquals("bulut", r.api.fetchSnapshot("a").rates.single())
        assertEquals(1, r.direct.calls)
        r.clock += 121_000
        r.direct.down = false
        assertEquals("server", r.api.fetchSnapshot("a").rates.single())
    }

    @Test fun wifiChangeRetriesDirectAtOnce() = runBlocking {
        val r = Rig().apply { direct.down = true }
        r.api.fetchDebt("a", "s")
        r.direct.down = false
        r.api.retryDirect()
        assertEquals("server", r.api.fetchDebt("a", "s").first)
    }

    @Test fun noCloudMeansErrorsSurface() = runBlocking {
        val r = Rig().apply { cloudReady = false; direct.down = true }
        try { r.api.fetchDebt("a", "s"); fail("xato kutilgan edi") } catch (e: ServerException) { assertEquals(ServerError.CONNECT, e.kind) }
        assertEquals(0, r.cloud.calls)
    }

    @Test fun insertFallsBackOnlyWhenSurelyNotSent() = runBlocking {
        val r = Rig().apply { direct.down = true }
        assertEquals("bulut-1", r.api.insert("u", "1", "p1"))
        assertEquals(listOf("p1"), r.cloud.inserted)
        // natija noma'lum (o'rtada uzildi) — bulutga O'TMAYDI: navbat avval serverdan qidiradi
        val r2 = Rig().apply { direct.unknown = true }
        try { r2.api.insert("u", "1", "p2"); fail("xato kutilgan edi") } catch (e: ServerException) { assertEquals(ServerError.QUERY, e.kind) }
        assertTrue(r2.cloud.inserted.isEmpty())
    }

    @Test fun findLooksInCloudThenServer() = runBlocking {
        val r = Rig()
        r.cloud.inserted += "pc"
        r.direct.inserted += "pd"
        assertEquals("bulut-1", r.api.find("u", "1", "pc", 5L))
        assertEquals("server-1", r.api.find("u", "1", "pd", 0L))
        assertNull(r.api.find("u", "1", "yoq", null))
    }

    @Test fun watermarkIsZeroOnCloud() = runBlocking {
        val r = Rig().apply { wifi = false }
        assertEquals(0L, r.api.maxRequestId("u"))
        r.wifi = true
        assertEquals(77L, r.api.maxRequestId("u"))
    }

    // ---------------- kompyuter ko'prigi ishlab turganda Admin chetda turadi ----------------

    private val secret = "ADMIN-SECRET"
    private val url = "https://test-shop.firebaseio.com"
    private val fake = FakeRtdb(secret)
    private val cfg = RelayCodec.Config(url, CloudDb.newKey())
    private val inbox = CloudDb.newKey()
    private var clock = 5_000_000L
    private val written = ArrayList<String>()
    private val inserted = ArrayList<String>()
    private val barcodes = ArrayList<String>()

    private val server = object : RelayHost.Server {
        override suspend fun agentByPhone(phone: String): Pair<RawAgent, String>? = null
        override suspend fun agents(): List<RawAgent> = emptyList()
        override suspend fun fetchSnapshot(agentId: String) = CatalogSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)
        override suspend fun fetchDebt(agentId: String, storeId: String) = "0" to "0"
        override suspend fun fetchReport(agentId: String, payload: String) = ""
        override suspend fun serverTimeMillis() = 1L
        override suspend fun find(userId: String, type: String, payload: String): String? = null
        override suspend fun insert(userId: String, type: String, payload: String): String { inserted += payload; return "1" }
    }

    private val host = RelayHost(
        { cfg }, { u -> CloudDb(u, secret, fake.http()) }, server,
        object : RelayHost.Hooks {
            override fun adminAgentId() = "A0"
            override fun codes(): Pair<Long, ByteArray>? = null
            override suspend fun printLabels(data: ByteArray) = ""
            override suspend fun mirrorSale(sale: RelayCodec.Sale, key: String) = Unit
            override fun written(agentId: String, type: String, payload: String) { written += payload }
            override suspend fun barcode(productId: String, name: String, code: String): Boolean { barcodes += "$productId=$code"; return true }
            override fun branchStores() = listOf("S1")
            override fun blockedDevices() = listOf("dev9")
            override fun pcLink() = inbox to mapOf("host" to "192.168.1.5", "database" to "baza", "user" to "sa", "password" to "p")
            override fun updateUrl() = "https://x.pages.dev/u/abc/"
        },
        now = { clock },
    )
    private val db = CloudDb(url, secret, fake.http())

    private fun queueSale(payload: String): String {
        val oid = RelayCodec.oid("A7", "1", payload)
        db.put(cfg.path("ob/$oid"), linkedMapOf("at" to clock, "u" to "A7", "t" to "1", "p" to payload))
        return oid
    }

    @Test fun pcAliveRule() {
        assertTrue(host.pcAlive(mapOf("h" to "pc", "at" to clock - 60_000)))
        assertFalse(host.pcAlive(mapOf("h" to "pc", "at" to clock - 10 * 60_000)))
        assertFalse(host.pcAlive(mapOf("at" to clock)))
    }

    @Test fun whenPcIsAliveAdminLeavesQueueToIt() = runBlocking {
        db.put(cfg.path("hb"), linkedMapOf("at" to clock, "h" to "pc", "q" to 0, "e" to "", "v" to "5.3"))
        queueSale("sale-1")
        host.pump()
        assertTrue(host.pcHost)
        assertTrue(inserted.isEmpty())
        // kompyuter holatini Admin ustidan yozib yubormaydi
        @Suppress("UNCHECKED_CAST")
        assertEquals("pc", (db.get(cfg.path("hb")) as Map<String, Any?>)["h"])
    }

    @Test fun whenPcIsSilentAdminTakesOver() = runBlocking {
        db.put(cfg.path("hb"), linkedMapOf("at" to clock - 30 * 60_000, "h" to "pc", "q" to 0, "e" to "", "v" to "5.3"))
        queueSale("sale-2")
        host.pump()
        assertFalse(host.pcHost)
        assertEquals(listOf("sale-2"), inserted)
        assertEquals(listOf("sale-2"), written)
    }

    @Test fun adminFinishesWhatPcWrote() = runBlocking {
        db.put(cfg.path("hb"), linkedMapOf("at" to clock, "h" to "pc", "q" to 0, "e" to "", "v" to "5.3"))
        db.put(cfg.path("mr/" + "a".repeat(40)), linkedMapOf("at" to clock, "u" to "A7", "t" to "1", "p" to "sale-3"))
        db.put(cfg.path("bq/" + CloudDb.newKey()), linkedMapOf("at" to clock, "op" to "bc", "p" to """{"i":"P1","n":"Pista","c":"478000"}"""))
        host.pump()
        assertEquals(listOf("sale-3"), written)
        assertEquals(listOf("P1=478000"), barcodes)
        assertNull(db.get(cfg.path("mr")))
        assertNull(db.get(cfg.path("bq")))
    }

    @Test fun adminPublishesLinkAndExtrasForPc() = runBlocking {
        host.pump()
        @Suppress("UNCHECKED_CAST")
        val rl = db.get("p/$inbox/rl") as Map<String, Any?>
        assertEquals(cfg.key, rl["key"])
        assertEquals("192.168.1.5", rl["host"])
        assertEquals("https://x.pages.dev/u/abc/", db.get("p/$inbox/upd"))
        @Suppress("UNCHECKED_CAST")
        val ax = db.get(cfg.path("ax")) as Map<String, Any?>
        assertEquals(listOf("S1"), ax["br"])
        assertEquals(listOf("dev9"), ax["bl"])
    }
}
