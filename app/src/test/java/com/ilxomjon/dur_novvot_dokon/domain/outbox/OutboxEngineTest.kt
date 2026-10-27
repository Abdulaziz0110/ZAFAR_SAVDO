package com.ilxomjon.dur_novvot_dokon.domain.outbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class OutboxEngineTest {

    private fun payload(i: Int) = "7^S$i^26.10.2026 14:05:${(i % 60).toString().padStart(2, '0')}^0^1^0^0^0^0^12650^1^1~T$i^1.0^100@"

    /** Bir "jarayon" hayoti: drain, o'ldirilsa — qayta tug'iladi (yangi engine, o'sha disk). */
    private fun runUntilSettled(store: FakeStore, sinkFactory: () -> FakeSink, clock: FakeClock, maxRounds: Int = 400): Int {
        var kills = 0
        repeat(maxRounds) {
            store.dead = false
            val engine = OutboxEngine(store, sinkFactory(), clock)
            try {
                runBlocking { engine.drain() }
            } catch (k: Killed) {
                kills++
            }
            clock.t += OutboxEngine.LEASE_MS + 1   // lease'lar va backoff'lar tugaydi
            if (store.all().all { it.state.isDone || it.state == OutboxState.NEEDS_CHECK }) return kills
        }
        return kills
    }

    private fun assertInvariants(store: FakeStore, server: FakeServer, tag: String) {
        assertEquals("$tag: serverda takroriy so'rov", 0, server.duplicates())
        for (e in store.all()) {
            val n = server.count(e.payload)
            when (e.state) {
                OutboxState.SENT -> assertNotNull("$tag: SENT ID'siz", e.serverRowId)
                OutboxState.NEEDS_CHECK -> assertTrue("$tag: NEEDS_CHECK da ko'p nusxa", n <= 1)
                else -> throw AssertionError("$tag: yakunlanmagan holat ${e.state} (id=${e.id})")
            }
            if (!server.deletesProcessed && e.state == OutboxState.SENT) assertEquals("$tag: SENT bo'lsa serverda aynan 1 ta", 1, n)
        }
    }

    @Test
    fun faultInjection_300Scenarios_noDuplicatesNoLoss() {
        var totalKills = 0
        var needsCheck = 0
        for (seed in 0 until 300) {
            val rnd = Random(seed.toLong())
            val store = FakeStore()
            val server = FakeServer(rnd)
            val clock = FakeClock()
            val n = 1 + rnd.nextInt(6)
            repeat(n) { store.add(if (it % 2 == 0) OutboxKind.ORDER else OutboxKind.PAYMENT, "7", payload(seed * 10 + it)) }
            var round = 0
            totalKills += runUntilSettled(store, {
                round++
                // Avval ko'p nosozlik, oxirida tarmoq tiklanadi (aks holda hech qachon tugamaydi).
                FakeSink(server, rnd, failRate = if (round < 25) 0.35 else 0.0, store = store).also { it.killRate = if (round < 25) 0.15 else 0.0 }
            }, clock)
            assertInvariants(store, server, "seed=$seed")
            needsCheck += store.all().count { it.state == OutboxState.NEEDS_CHECK }
        }
        assertTrue("o'ldirish ssenariylari haqiqatan sinaldi", totalKills > 100)
        println("faultInjection: kills=$totalKills needsCheck=$needsCheck")
    }

    @Test
    fun serverThatDeletesRows_neverDuplicates() {
        for (seed in 0 until 150) {
            val rnd = Random(10_000L + seed)
            val store = FakeStore()
            val server = FakeServer(rnd).apply { deletesProcessed = true }
            val clock = FakeClock()
            repeat(1 + rnd.nextInt(4)) { store.add(OutboxKind.ORDER, "7", payload(seed * 10 + it)) }
            var round = 0
            runUntilSettled(store, { round++; FakeSink(server, rnd, if (round < 20) 0.4 else 0.0, store).also { it.killRate = if (round < 20) 0.1 else 0.0 } }, clock)
            assertInvariants(store, server, "del seed=$seed")
        }
    }

    @Test
    fun concurrentWorkers_sameStore_noDuplicates() = runBlocking {
        val rnd = Random(42)
        val store = FakeStore()
        val server = FakeServer(rnd)
        val clock = FakeClock()
        repeat(40) { store.add(OutboxKind.ORDER, "7", payload(it)) }
        val slowSink = object : OutboxSink {
            val inner = FakeSink(server, rnd, 0.0)
            override suspend fun maxRequestId(userId: String) = inner.maxRequestId(userId).also { delay(1) }
            override suspend fun insert(userId: String, type: String, payload: String) = inner.insert(userId, type, payload).also { delay(2) }
            override suspend fun find(userId: String, type: String, payload: String, afterId: Long?) = inner.find(userId, type, payload, afterId)
            override suspend fun exists(requestId: String) = inner.exists(requestId)
        }
        // Ikki xil engine (alohida mutex) — faqat bazadagi atomik egallash himoya qiladi.
        val a = OutboxEngine(store, slowSink, clock)
        val b = OutboxEngine(store, slowSink, clock)
        listOf(async(Dispatchers.Default) { a.drain() }, async(Dispatchers.Default) { b.drain() }, async(Dispatchers.Default) { a.drain() }).awaitAll()
        assertEquals(0, server.duplicates())
        assertTrue(store.all().all { it.state == OutboxState.SENT })
        assertEquals(40, (0 until 40).count { server.count(payload(it)) == 1 })
    }

    // ---------------- aniq ssenariylar ----------------

    @Test
    fun insertDoneButResponseLost_probeFindsIt_noResend() = runBlocking {
        val rnd = Random(1); val server = FakeServer(rnd); val store = FakeStore(); val clock = FakeClock()
        val id = store.add(OutboxKind.ORDER, "7", payload(1))
        server.insertRaw("7", "1", "eski")            // watermark ishonchliligi uchun guvoh
        val lossy = object : OutboxSink {
            val inner = FakeSink(server, rnd, 0.0)
            override suspend fun maxRequestId(userId: String) = inner.maxRequestId(userId)
            override suspend fun insert(userId: String, type: String, payload: String): String { inner.insert(userId, type, payload); throw java.io.IOException("Read timed out") }
            override suspend fun find(userId: String, type: String, payload: String, afterId: Long?) = inner.find(userId, type, payload, afterId)
            override suspend fun exists(requestId: String) = inner.exists(requestId)
        }
        OutboxEngine(store, lossy, clock).drain()
        assertEquals(OutboxState.UNCERTAIN, store.get(id).state)
        clock.t += 10_000
        OutboxEngine(store, FakeSink(server, rnd, 0.0), clock).drain()
        assertEquals(OutboxState.SENT, store.get(id).state)
        assertEquals(1, server.count(payload(1)))
    }

    @Test
    fun noConnection_nothingInserted_retryLater() = runBlocking {
        val rnd = Random(2); val server = FakeServer(rnd); val store = FakeStore(); val clock = FakeClock()
        val id = store.add(OutboxKind.PAYMENT, "7", payload(2))
        val r = OutboxEngine(store, FakeSink(server, rnd, 1.0), clock).drain()
        assertTrue(r.retryLater)
        assertEquals(OutboxState.RETRY_WAIT, store.get(id).state)
        assertEquals(0, server.count(payload(2)))
    }

    @Test
    fun probeCannotRun_nothingSent() = runBlocking {
        val rnd = Random(3); val server = FakeServer(rnd); val store = FakeStore(); val clock = FakeClock()
        val id = store.add(OutboxKind.ORDER, "7", payload(3), state = OutboxState.UNCERTAIN, watermark = 5, phase = 1)
        OutboxEngine(store, FakeSink(server, rnd, 1.0), clock).drain()
        assertEquals(OutboxState.UNCERTAIN, store.get(id).state)
        assertEquals(0, server.count(payload(3)))
    }

    @Test
    fun legacyRowWithoutWitness_goesToNeedsCheck_notResent() = runBlocking {
        val rnd = Random(4); val server = FakeServer(rnd); val store = FakeStore(); val clock = FakeClock()
        val id = store.add(OutboxKind.ORDER, "7", payload(4), state = OutboxState.UNCERTAIN)
        OutboxEngine(store, FakeSink(server, rnd, 0.0), clock).drain()
        assertEquals(OutboxState.NEEDS_CHECK, store.get(id).state)
        assertEquals(CheckReason.PROBE_UNRELIABLE, store.get(id).checkReason)
        assertEquals(0, server.count(payload(4)))
    }

    @Test
    fun rejectedItem_doesNotBlockOthers() = runBlocking {
        val rnd = Random(5); val server = FakeServer(rnd); val store = FakeStore(); val clock = FakeClock()
        val bad = store.add(OutboxKind.ORDER, "7", "BAD")
        val good = store.add(OutboxKind.ORDER, "7", payload(5))
        val sink = object : OutboxSink {
            val inner = FakeSink(server, rnd, 0.0)
            override suspend fun maxRequestId(userId: String) = inner.maxRequestId(userId)
            override suspend fun insert(userId: String, type: String, payload: String) =
                if (payload == "BAD") throw RejectedException("String or binary data would be truncated") else inner.insert(userId, type, payload)
            override suspend fun find(userId: String, type: String, payload: String, afterId: Long?) = inner.find(userId, type, payload, afterId)
            override suspend fun exists(requestId: String) = inner.exists(requestId)
        }
        repeat(10) { OutboxEngine(store, sink, clock).drain(); clock.t += 700_000 }
        assertEquals(OutboxState.SENT, store.get(good).state)
        assertEquals(OutboxState.NEEDS_CHECK, store.get(bad).state)
        assertEquals(CheckReason.REJECTED, store.get(bad).checkReason)
    }

    @Test
    fun forceSend_skipsProbeOnce_thenProbesAgain() = runBlocking {
        val rnd = Random(6); val server = FakeServer(rnd); val store = FakeStore(); val clock = FakeClock()
        val id = store.add(OutboxKind.ORDER, "7", payload(6), state = OutboxState.READY)
        store.update(id) { it.copy(forceSend = true) }
        OutboxEngine(store, FakeSink(server, rnd, 0.0), clock).drain()
        assertEquals(OutboxState.SENT, store.get(id).state)
        assertEquals(false, store.get(id).forceSend)
    }

    @Test
    fun receiptCode() {
        assertEquals("B-0412", OutboxEntry.receiptOf(OutboxKind.ORDER, 412))
        assertEquals("T-0007", OutboxEntry.receiptOf(OutboxKind.PAYMENT, 10007))
    }
}
