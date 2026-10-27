package com.ilxomjon.dur_novvot_dokon.domain.outbox

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Ishonchli yuborish yadrosi (best-effort dedup — KAFOLAT EMAS, README'ga qarang).
 *
 * Qoidalar:
 *  1. Har qator bir vaqtda faqat bitta ishchi tomonidan yuboriladi: bazada atomik "egallash" + lease,
 *     jarayon ichida esa [mutex].
 *  2. INSERT'dan OLDIN bazaga "INSERT boshlandi" (phase = 1) va watermark yoziladi. Shunday qilib jarayon
 *     har qanday joyda o'lsa ham, keyingi safar natija noma'lum ekanini bilamiz.
 *  3. Natija noma'lum bo'lsa — qayta yuborishdan oldin serverda FAQAT SELECT bilan qidiriladi (probe).
 *     Topilsa: SENT. Topilmasa va qidiruvga ishonsa bo'lsa: qayta yuboriladi. Ishonib bo'lmasa: NEEDS_CHECK.
 *  4. Probe'ning o'zi bajarilmasa — hech narsa yuborilmaydi (UNCERTAIN qoladi, keyin yana urinadi).
 *  5. INSERT va natijani yozish bekor qilinmaydi ([NonCancellable]) — "Hozir yuborish" yarim yo'lda to'xtatmaydi.
 */
class OutboxEngine(
    private val store: OutboxStore,
    private val sink: OutboxSink,
    private val clock: Clock,
    private val leaseMs: Long = LEASE_MS,
) {
    private val mutex = Mutex()

    /** Natija: nechta yuborildi, qolganlari bormi va oxirgi xato. */
    data class Result(val sent: Int, val retryLater: Boolean, val lastError: Throwable?)

    /** Navbatdagi barcha tayyor qatorlarni ketma-ket yuboradi. Allaqachon ishlayotgan bo'lsa, kutib, keyin o'zi ham o'tadi. */
    suspend fun drain(maxItems: Int = 500): Result = mutex.withLock {
        var sent = 0
        var retry = false
        var lastError: Throwable? = null
        var processed = 0
        var connectionDown = false
        while (processed < maxItems && !connectionDown) {
            val e = store.claimNext(clock.now(), leaseMs) ?: break
            processed++
            when (val r = process(e)) {
                Outcome.Sent -> sent++
                is Outcome.Later -> {
                    retry = true; lastError = r.error
                    // Ulanish yo'q bo'lsa, qolgan qatorlarni ham sinab o'tirmaymiz (ular ham egallanmay qoladi).
                    if (r.connectionDown) connectionDown = true
                }
                Outcome.Parked -> Unit
            }
        }
        Result(sent, retry, lastError)
    }

    private sealed interface Outcome {
        data object Sent : Outcome
        data class Later(val error: Throwable?, val connectionDown: Boolean) : Outcome
        data object Parked : Outcome
    }

    private suspend fun process(e: OutboxEntry): Outcome {
        val now = clock.now()
        val type = e.kind.protocolType
        val unknownResult = !e.forceSend && (e.phase == 1 || e.prevState == OutboxState.UNCERTAIN || e.prevState == OutboxState.SENDING)

        if (unknownResult) {
            val found: String? = try {
                sink.find(e.agentId, type, e.payload, e.watermark)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                // Tekshirib bo'lmadi — HECH NARSA yubormaymiz.
                // Ulanish yo'qligi (NotSent) hisobga olinmaydi: internet kelganda tekshiruv davom etadi.
                if (t !is NotSentException && e.attempts >= MAX_PROBE_FAILURES) store.markNeedsCheck(e.id, CheckReason.PROBE_FAILED, t.message, now)
                else store.markUncertain(e.id, t.message, now + backoff(e.attempts), now)
                return Outcome.Later(t, connectionDown = t is NotSentException)
            }
            if (found != null) {
                store.markSent(e.id, found, now)
                return Outcome.Sent
            }
            // Topilmadi. Server eski so'rovlarni o'chirib yuboradigan bo'lsa, "topilmadi" hech narsani anglatmaydi.
            val reliable = try {
                probeIsReliable(e)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                store.markUncertain(e.id, t.message, now + backoff(e.attempts), now)
                return Outcome.Later(t, connectionDown = t is NotSentException)
            }
            if (!reliable) {
                store.markNeedsCheck(e.id, CheckReason.PROBE_UNRELIABLE, null, now)
                return Outcome.Parked
            }
            // Ishonchli tekshiruv: serverda yo'q -> xavfsiz qayta yuborish.
        }
        return send(e, type)
    }

    /**
     * Probe'ga ishonsa bo'ladimi: avval yozilgani ANIQ bo'lgan so'rov (watermark yoki oxirgi SENT ID) serverda hali
     * turibdimi. Turgan bo'lsa — server so'rovlarni o'chirmaydi deb hisoblaymiz. Tekshiradigan narsa yo'q bo'lsa — ishonmaymiz.
     */
    private suspend fun probeIsReliable(e: OutboxEntry): Boolean {
        val witness = e.watermark?.takeIf { it > 0 }?.toString() ?: store.lastServerRowId(e.agentId) ?: return false
        return sink.exists(witness)
    }

    private suspend fun send(e: OutboxEntry, type: String): Outcome {
        val now = clock.now()
        val wm = try {
            sink.maxRequestId(e.agentId)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // Hali hech narsa yozilmadi.
            store.markRetry(e.id, t.message, now + backoff(e.attempts), now)
            return Outcome.Later(t, connectionDown = true)
        }
        return withContext(NonCancellable) {
            store.setWatermark(e.id, wm, clock.now())
            store.markInserting(e.id, clock.now())
            try {
                val id = sink.insert(e.agentId, type, e.payload)
                store.markSent(e.id, id, clock.now())
                Outcome.Sent
            } catch (t: NotSentException) {
                store.markRetry(e.id, t.message, clock.now() + backoff(e.attempts), clock.now())
                Outcome.Later(t, connectionDown = true)
            } catch (t: RejectedException) {
                if (e.attempts >= MAX_REJECTS) store.markNeedsCheck(e.id, CheckReason.REJECTED, t.message, clock.now())
                else store.markRetry(e.id, t.message, clock.now() + backoff(e.attempts + 2), clock.now())
                Outcome.Later(t, connectionDown = false)
            } catch (t: Throwable) {
                // INSERT yuborilgan bo'lishi mumkin: keyingi safar avval qidiramiz.
                store.markUncertain(e.id, t.message, clock.now() + UNCERTAIN_RECHECK_MS, clock.now())
                Outcome.Later(t, connectionDown = false)
            }
        }
    }

    companion object {
        const val LEASE_MS = 120_000L
        const val UNCERTAIN_RECHECK_MS = 5_000L
        const val MAX_PROBE_FAILURES = 40
        const val MAX_REJECTS = 5

        /** 10 s, 20 s, 40 s ... eng ko'pi 10 daqiqa. */
        fun backoff(attempts: Int): Long {
            val shift = attempts.coerceIn(0, 6)
            return minOf(10_000L shl shift, 600_000L)
        }
    }
}
