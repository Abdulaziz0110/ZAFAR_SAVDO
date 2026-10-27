package com.ilxomjon.dur_novvot_dokon.data.repo

import android.content.Context
import android.os.PowerManager
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.NetState
import com.ilxomjon.dur_novvot_dokon.data.NetworkMonitor
import com.ilxomjon.dur_novvot_dokon.data.SecureStore
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.local.LocalDb
import com.ilxomjon.dur_novvot_dokon.data.local.OutboxCounts
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/** Diagnostika natijasi. Matnga UI aylantiradi (lotin/kirill). */
data class Diagnosis(
    val net: NetState,
    val configured: Boolean,
    /** Do'kon manzili porti ochiqmi: true/false, null — tekshirib bo'lmadi (masalan nomli instance). */
    val localPort: Boolean?,
    val remoteConfigured: Boolean,
    val remotePort: Boolean?,
    /** SQL login va SELECT o'tdimi. */
    val sqlOk: Boolean,
    val sqlError: String?,
    /** Qurilma soati serverdan farqi (ms), null — aniqlanmadi. */
    val clockSkewMs: Long?,
    val counts: OutboxCounts,
    val lastOutboxError: String?,
    val lastSuccessAt: Long,
    val batteryRestricted: Boolean,
    val dbOk: Boolean?,
    val writeError: String?,
    val backgroundError: String?,
    val legacyUnsentRows: Int,
)

class DiagnosticsRepository(
    private val context: Context,
    private val secure: SecureStore,
    private val api: ServerApi,
    private val network: NetworkMonitor,
    private val outbox: OutboxRepository,
    private val history: HistoryDb,
    private val local: LocalDb,
) {
    /** Hammasi parallel, har qadam vaqt bilan cheklangan (UI'ni bloklamaydi, bekor qilinadi). */
    suspend fun run(): Diagnosis = coroutineScope {
        val net = network.refresh()
        val cfg = withContext(Dispatchers.IO) { secure.serverConfig }
        val local = async(Dispatchers.IO) { withTimeoutOrNull(4_000) { tcp(cfg.host) } }
        val remote = async(Dispatchers.IO) { if (cfg.remoteHost.isBlank()) null else withTimeoutOrNull(4_000) { tcp(cfg.remoteHost) } }
        val sql = async {
            withTimeoutOrNull(12_000) {
                runCatching { api.serverTimeMillis() }
            } ?: Result.failure(IllegalStateException("timeout"))
        }
        outbox.refreshCount()
        val legacy = withContext(Dispatchers.IO) {
            runCatching { this@DiagnosticsRepository.local.readableDatabase.rawQuery("SELECT COUNT(*) FROM zakaz_jonatishga", null).use { if (it.moveToFirst()) it.getInt(0) else 0 } }.getOrDefault(0)
        }
        val battery = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName).not()
        }.getOrDefault(false)
        val s = sql.await()
        val serverTime = s.getOrNull()
        Diagnosis(
            net = net,
            configured = cfg.isComplete,
            localPort = local.await(),
            remoteConfigured = cfg.remoteHost.isNotBlank(),
            remotePort = remote.await(),
            sqlOk = s.isSuccess,
            sqlError = s.exceptionOrNull()?.message?.take(160),
            clockSkewMs = serverTime?.let { System.currentTimeMillis() - it },
            counts = outbox.counts.value,
            lastOutboxError = outbox.lastError.value?.message?.take(160),
            lastSuccessAt = outbox.lastSuccessAt,
            batteryRestricted = battery,
            dbOk = ServiceLocator.dbHealthy,
            writeError = ServiceLocator.draft.lastWriteError?.message?.take(160),
            backgroundError = ServiceLocator.lastBackgroundError?.message?.take(160),
            legacyUnsentRows = legacy,
        )
    }

    /** "host", "host:port" yoki "host,port". Nomli instance ("host\\NOM") uchun port aniqlanmaydi -> null. */
    private fun tcp(hostSpec: String): Boolean? {
        if (hostSpec.isBlank() || hostSpec.contains('\\')) return null
        val (host, port) = parse(hostSpec)
        return runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 3_000); true } }.getOrDefault(false)
    }

    private fun parse(spec: String): Pair<String, Int> {
        val s = spec.trim()
        val sep = s.lastIndexOfAny(charArrayOf(':', ','))
        if (sep > 0) s.substring(sep + 1).toIntOrNull()?.let { return s.substring(0, sep) to it }
        return s to 1433
    }
}
