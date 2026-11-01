package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.data.relay.RelayCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.report.HistoryPack
import uz.imsoft.shared.report.LedgerEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v3.7: Admin'ning "1C tarixi" (kompyuterdagi barcha savdolar + server kartochkalari, 1 yil) agentlarga.
 * Bulutda: g/{agent kaliti}/hist/meta = {at, n, b: {bo'lak: xesh}}, g/{kalit}/hist/m/{bo'lak} = gzip+base64.
 * Admin yozadi (secret bilan), agent "Admin orqali" kaliti bilan o'qiydi; faqat o'zgargan bo'laklar yuklanadi.
 */
class HistoryShare(private val context: Context) {
    // ---------------- Admin: yuborish ----------------

    private val pub by lazy { context.getSharedPreferences("hist_pub", Context.MODE_PRIVATE) }
    private val pubLock = Mutex()
    private var pubVersion = -1L
    private var pubAt = 0L

    private fun readMap(text: String): Map<String, String> =
        runCatching { JObj.parse(text).map.mapValues { it.value?.toString().orEmpty() } }.getOrDefault(emptyMap())

    /** @return yuborilgan bo'laklar soni (o'zgarmagan bo'lsa 0). */
    suspend fun publish(cfg: RelayCodec.Config?, secret: String, entries: List<LedgerEntry>, version: Long, loaded: Boolean): Int = pubLock.withLock {
        if (cfg == null || secret.isBlank() || !loaded || version < 0) return@withLock 0
        val now = System.currentTimeMillis()
        if (version == pubVersion || now - pubAt < 60_000L) return@withLock 0
        val parts = withContext(Dispatchers.Default) {
            HistoryPack.split(entries).mapValues { (_, l) -> HistoryPack.json(l).let { j -> HistoryPack.hash(j) to j } }
        }
        val old = readMap(pub.getString("h", "").orEmpty())
        val hashes = parts.mapValues { it.value.first }
        val db = CloudDb(cfg.dbUrl, secret)
        var n = 0
        withContext(Dispatchers.IO) {
            for ((k, v) in parts) {
                if (old[k] == v.first) continue
                db.put(cfg.path("hist/m/$k"), HistoryPack.pack(v.second), timeoutMs = 120_000)
                n++
            }
            for (k in old.keys - parts.keys) runCatching { db.delete(cfg.path("hist/m/$k")) }
            if (n > 0 || old.keys != parts.keys || pub.getLong("meta_at", 0L) == 0L) {
                db.put(cfg.path("hist/meta"), linkedMapOf("at" to CloudDb.SERVER_TIME, "n" to entries.size, "b" to hashes))
            }
        }
        pub.edit().putString("h", Json.write(hashes)).putLong("meta_at", now).apply()
        pubVersion = version
        pubAt = now
        n
    }

    // ---------------- Agent: o'qish ----------------

    data class AgentState(
        val entries: List<LedgerEntry> = emptyList(),
        val version: Long = 0L,
        val loaded: Boolean = false,
        val status: String = "",
    )

    private val ag by lazy { context.getSharedPreferences("hist_agent", Context.MODE_PRIVATE) }
    private val agLock = Mutex()
    private val parts = HashMap<String, List<LedgerEntry>>()
    private var agLoaded = false
    private var agVersion = 0L
    private var lastSync = 0L
    private val _agent = MutableStateFlow(AgentState())
    val agent: StateFlow<AgentState> = _agent.asStateFlow()

    private fun agDir() = File(context.filesDir, "hist_agent").apply { mkdirs() }
    private fun hm(ms: Long) = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(ms))

    private suspend fun agLoad() {
        if (agLoaded) return
        withContext(Dispatchers.IO) {
            for (f in agDir().listFiles().orEmpty()) {
                if (!f.name.endsWith(".blob")) continue
                runCatching { parts[f.name.removeSuffix(".blob")] = HistoryPack.unpack(f.readText()) }
            }
        }
        agLoaded = true
        agPublish(statusFor(null, ""))
    }

    private suspend fun agPublish(status: String) {
        val snapshot = parts.values.toList()
        val all = withContext(Dispatchers.Default) { snapshot.flatten().sortedBy { it.stamp } }
        agVersion++
        _agent.value = AgentState(all, agVersion, true, status)
    }

    private fun statusFor(cfg: RelayCodec.Config?, err: String): String {
        val at = ag.getLong("at", 0L)
        val n = parts.values.sumOf { it.size }
        val parts2 = ArrayList<String>()
        parts2 += when {
            at > 0 -> context.getString(R.string.ah_agent_status, hm(at), n)
            cfg == null && !ag.contains("at") -> context.getString(R.string.ah_agent_no_link)
            else -> context.getString(R.string.ah_agent_wait)
        }
        if (err.isNotEmpty()) parts2 += context.getString(R.string.sh_status_err, hm(System.currentTimeMillis()), err)
        return parts2.joinToString("\n")
    }

    /** Agent: Admin yuborgan tarixni yangilaydi ([force] bo'lmasa 2 daqiqada ko'pi bilan bir). */
    suspend fun sync(cfg: RelayCodec.Config?, force: Boolean) = agLock.withLock {
        agLoad()
        if (cfg == null) { _agent.value = _agent.value.copy(status = statusFor(null, "")); return@withLock }
        val now = System.currentTimeMillis()
        if (!force && now - lastSync < 2 * 60_000L) return@withLock
        lastSync = now
        try {
            val db = CloudDb(cfg.dbUrl)
            @Suppress("UNCHECKED_CAST")
            val meta = withContext(Dispatchers.IO) { db.get(cfg.path("hist/meta")) as? Map<String, Any?> }
            if (meta == null) { _agent.value = _agent.value.copy(status = statusFor(cfg, "")); return@withLock }
            val o = JObj(meta)
            @Suppress("UNCHECKED_CAST")
            val remote = ((o.map["b"] as? Map<String, Any?>) ?: emptyMap()).mapValues { it.value?.toString().orEmpty() }
            val local = readMap(ag.getString("h", "").orEmpty()).toMutableMap()
            var changed = false
            withContext(Dispatchers.IO) {
                for ((k, h) in remote) {
                    if (local[k] == h && parts.containsKey(k)) continue
                    val raw = db.getRaw(cfg.path("hist/m/$k"), timeoutMs = 120_000)
                    val blob = Json.parse(raw) as? String ?: continue
                    val list = HistoryPack.unpack(blob)
                    val f = File(agDir(), "$k.blob")
                    val tmp = File(agDir(), "$k.blob.tmp")
                    tmp.writeText(blob)
                    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                    parts[k] = list
                    local[k] = h
                    changed = true
                }
                for (k in parts.keys - remote.keys) {
                    parts.remove(k)
                    local.remove(k)
                    File(agDir(), "$k.blob").delete()
                    changed = true
                }
            }
            ag.edit().putString("h", Json.write(local)).putLong("at", o.long("at")).apply()
            if (changed) agPublish(statusFor(cfg, "")) else _agent.value = _agent.value.copy(status = statusFor(cfg, ""))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _agent.value = _agent.value.copy(status = statusFor(cfg, (e.message ?: e.javaClass.simpleName).take(120)), loaded = true)
        }
    }
}
