package com.ilxomjon.dur_novvot_dokon.domain.outbox

import kotlinx.coroutines.CancellationException
import java.util.Random
import java.util.UUID

/** Jarayon "o'ldirildi": shu ondan keyin eski jarayonning hech bir amali bajarilmaydi. */
class Killed : CancellationException("process killed")

class FakeClock(var t: Long = 1_000_000L) : Clock { override fun now() = t }

/** Bir nechta "jarayon" ulashadigan disk (SQLite o'rnida). Hamma amallar sinxron va atomik. */
class FakeStore : OutboxStore {
    private val rows = LinkedHashMap<Long, OutboxEntry>()
    private var seq = 0L
    @Volatile var dead = false
    /** Har store amalidan oldin chaqiriladi (o'ldirish nuqtasini tanlash uchun). */
    var hook: (String) -> Unit = {}

    private fun guard(op: String) { if (dead) throw Killed(); hook(op); if (dead) throw Killed() }

    @Synchronized fun add(kind: OutboxKind, agent: String, payload: String, state: OutboxState = OutboxState.READY, watermark: Long? = null, phase: Int = 0): Long {
        val id = ++seq
        rows[id] = OutboxEntry(
            id, kind, agent, payload, UUID.randomUUID().toString(), 0, state, 0, watermark, null, 0, 0, phase, null,
            null, null, false, "S", "S", 0.0, 0,
        )
        return id
    }

    @Synchronized fun all(): List<OutboxEntry> = rows.values.toList()
    @Synchronized fun get(id: Long): OutboxEntry = rows.getValue(id)
    @Synchronized fun update(id: Long, f: (OutboxEntry) -> OutboxEntry) { rows[id] = f(rows.getValue(id)) }

    @Synchronized override fun claimNext(now: Long, leaseMs: Long): OutboxEntry? {
        guard("claim")
        val e = rows.values.firstOrNull {
            it.state == OutboxState.READY ||
                ((it.state == OutboxState.RETRY_WAIT || it.state == OutboxState.UNCERTAIN) && it.nextAt <= now) ||
                (it.state == OutboxState.SENDING && it.leaseUntil < now)
        } ?: return null
        val c = e.copy(state = OutboxState.SENDING, prevState = e.state, leaseUntil = now + leaseMs, attempts = e.attempts + 1)
        rows[e.id] = c
        return c
    }

    @Synchronized override fun setWatermark(id: Long, watermark: Long, now: Long) { guard("wm"); update(id) { it.copy(watermark = watermark) } }
    @Synchronized override fun markInserting(id: Long, now: Long) { guard("inserting"); update(id) { it.copy(phase = 1, forceSend = false) } }
    @Synchronized override fun markSent(id: Long, serverRowId: String, now: Long) {
        guard("sent"); update(id) { it.copy(state = OutboxState.SENT, serverRowId = serverRowId, phase = 0, sentAt = now) }
    }
    @Synchronized override fun markRetry(id: Long, error: String?, nextAt: Long, now: Long) {
        guard("retry"); update(id) { it.copy(state = OutboxState.RETRY_WAIT, phase = 0, nextAt = nextAt, lastError = error) }
    }
    @Synchronized override fun markUncertain(id: Long, error: String?, nextAt: Long, now: Long) {
        guard("uncertain"); update(id) { it.copy(state = OutboxState.UNCERTAIN, nextAt = nextAt, lastError = error, forceSend = false) }
    }
    @Synchronized override fun markNeedsCheck(id: Long, reason: String, error: String?, now: Long) {
        guard("needs"); update(id) { it.copy(state = OutboxState.NEEDS_CHECK, checkReason = reason, lastError = error, forceSend = false) }
    }
    @Synchronized override fun lastServerRowId(agentId: String): String? {
        guard("last")
        return rows.values.filter { it.agentId == agentId && it.state == OutboxState.SENT && it.serverRowId != null }.maxByOrNull { it.sentAt }?.serverRowId
    }
}

/** "savollar" jadvali o'rnidagi soxta server. */
class FakeServer(private val rnd: Random) {
    data class Row(val id: Long, val user: String, val type: String, val payload: String)
    private val rows = ArrayList<Row>()
    private var nextId = 1000L
    /** Server dasturi qayta ishlangan qatorlarni o'chirsa. */
    var deletesProcessed = false

    @Synchronized fun count(payload: String) = rows.count { it.payload == payload }
    @Synchronized fun insertRaw(user: String, type: String, payload: String): Long { val id = nextId++; rows += Row(id, user, type, payload); return id }
    @Synchronized fun maxId(user: String) = rows.filter { it.user == user }.maxOfOrNull { it.id } ?: 0L
    @Synchronized fun find(user: String, type: String, payload: String, after: Long?) =
        rows.firstOrNull { it.user == user && it.type == type && it.payload == payload && (after == null || it.id > after) }?.id
    @Synchronized fun exists(id: Long) = rows.any { it.id == id }
    @Synchronized fun processAndMaybeDelete() { if (deletesProcessed && rows.isNotEmpty() && rnd.nextBoolean()) rows.removeAt(0) }
    @Synchronized fun duplicates(): Int = rows.groupBy { it.payload }.values.count { it.size > 1 }
}

/**
 * Nosozliklar: ulanish yo'q, INSERT serverda bajarilgan-u javob yo'qolgan, probe xatosi, jarayon o'limi.
 * [failRate] — har amalda nosozlik ehtimoli.
 */
class FakeSink(private val server: FakeServer, private val rnd: Random, var failRate: Double, private val store: FakeStore? = null) : OutboxSink {
    var killRate = 0.0
    private fun maybeKill() { if (store != null && rnd.nextDouble() < killRate) { store.dead = true; throw Killed() } }
    private fun fail() = rnd.nextDouble() < failRate

    override suspend fun maxRequestId(userId: String): Long {
        maybeKill()
        if (fail()) throw NotSentException("no connection")
        return server.maxId(userId)
    }

    override suspend fun insert(userId: String, type: String, payload: String): String {
        maybeKill()
        when {
            fail() && rnd.nextBoolean() -> throw NotSentException("no connection")
            fail() && rnd.nextInt(4) == 0 -> throw RejectedException("rejected")
        }
        val id = server.insertRaw(userId, type, payload)
        server.processAndMaybeDelete()
        maybeKill()                                   // yozildi, lekin jarayon javobni olmasdan o'ldi
        if (fail()) throw java.io.IOException("Read timed out")   // yozildi, javob yo'qoldi
        return id.toString()
    }

    override suspend fun find(userId: String, type: String, payload: String, afterId: Long?): String? {
        maybeKill()
        if (fail()) throw java.io.IOException("probe failed")
        return server.find(userId, type, payload, afterId)?.toString()
    }

    override suspend fun exists(requestId: String): Boolean {
        maybeKill()
        if (fail()) throw java.io.IOException("exists failed")
        return server.exists(requestId.toLong())
    }
}
