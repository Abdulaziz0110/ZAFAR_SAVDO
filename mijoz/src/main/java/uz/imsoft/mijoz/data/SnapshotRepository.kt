package uz.imsoft.mijoz.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.OrderCodec
import uz.imsoft.shared.OrderLine
import uz.imsoft.shared.OrderRequest
import uz.imsoft.shared.report.ClientSnapshot
import uz.imsoft.shared.report.SnapshotCodec
import java.io.File

data class DataState(
    val snapshot: ClientSnapshot? = null,
    /** Ma'lumot 1-ilovadan qachon yuborilgan (server vaqti). */
    val publishedAt: Long = 0L,
    val fetchedAt: Long = 0L,
    val loading: Boolean = false,
    val error: Throwable? = null,
    /** "Yangilash" so'rovi yuborilgan, 1-ilova hali javob bermagan. */
    val requestPending: Boolean = false,
    val requestAt: Long = 0L,
    val linked: Boolean = false,
    val loaded: Boolean = false,
    /** 1-ilovada bu telefon o'chirilgan yoki paroli almashgan — qayta kirish kerak. */
    val revoked: Boolean = false,
)

/** Kirish bekor qilingan (1-ilova egasi telefonni o'chirgan yoki parolni almashtirgan). */
class AccessRevokedException : Exception("revoked")

enum class RefreshResult { UPDATED, SAME, NO_DATA, NOT_LINKED }

/**
 * 1-ilova yuborgan ma'lumotni Firebase'dan oladi va telefonda saqlaydi (internetsiz ham ko'rinadi).
 * Hech narsa o'zgartirmaydi — faqat "yangilab ber" so'rovini (vaqt belgisi) yozadi.
 */
class SnapshotRepository(
    private val context: Context,
    private val prefs: MijozPrefs,
    /** Telefon/parol bilan jimgina qayta kirish (kalit almashganda). */
    private val relogin: suspend (force: Boolean) -> LoginRepository.Relogin = { LoginRepository.Relogin.NONE },
) {
    private val _state = MutableStateFlow(DataState())
    val state: StateFlow<DataState> = _state.asStateFlow()
    private val mutex = Mutex()

    private fun file() = File(context.filesDir, "snapshot.blob")

    suspend fun loadLocal() = mutex.withLock {
        val snap = withContext(Dispatchers.IO) {
            val f = file()
            if (prefs.link != null && f.exists()) runCatching { SnapshotCodec.unpack(f.readText()) }.getOrNull() else null
        }
        _state.update {
            it.copy(
                snapshot = snap, publishedAt = prefs.metaAt, fetchedAt = prefs.fetchedAt, linked = prefs.link != null,
                requestAt = prefs.requestAt, requestPending = prefs.requestAt > 0 && prefs.requestAt > prefs.metaAt, loaded = true,
            )
        }
    }

    /** Ulanish o'zgarganda eski ma'lumot o'chiriladi. */
    suspend fun unlink() = mutex.withLock {
        withContext(Dispatchers.IO) { file().delete() }
        prefs.setLink(null)
        prefs.requestAt = 0L
        prefs.fetchedAt = 0L
        _state.value = DataState(loaded = true)
    }

    suspend fun onLinked() {
        withContext(Dispatchers.IO) { file().delete() }
        prefs.requestAt = 0L
        loadLocal()
    }

    /**
     * 1-ilova bulutga qo'ygan ENG SO'NGGI ma'lumotni darhol oladi (1-ilovada Excel yuklanganda u darhol yuboriladi).
     * [askFresh] — qo'shimcha ravishda 1-ilovadan narx/qarzni serverdan yangilashni so'raydi (server ko'ringanda bajaradi).
     */
    suspend fun refresh(askFresh: Boolean): RefreshResult {
        val first: RefreshResult
        try {
            first = refreshOnce(askFresh)
        } catch (e: CloudDb.CloudException) {
            if (!e.denied) throw e
            return afterRelogin(askFresh) ?: throw e
        }
        if (first != RefreshResult.NO_DATA) return first
        return afterRelogin(askFresh) ?: first
    }

    /** Ma'lumot topilmadi / ruxsat yo'q: balki 1-ilova kalitni almashtirgan — qayta kirib, bir marta qaytaramiz. */
    private suspend fun afterRelogin(askFresh: Boolean): RefreshResult? = when (relogin(false)) {
        LoginRepository.Relogin.CHANGED -> refreshOnce(askFresh)
        LoginRepository.Relogin.REVOKED -> {
            onRevoked()
            throw AccessRevokedException()
        }
        else -> null
    }

    suspend fun onRevoked() = mutex.withLock {
        withContext(Dispatchers.IO) { file().delete() }
        prefs.requestAt = 0L
        prefs.fetchedAt = 0L
        prefs.metaAt = 0L
        _state.value = DataState(loaded = true, revoked = true)
    }

    private suspend fun refreshOnce(askFresh: Boolean): RefreshResult {
        val link = prefs.link ?: return RefreshResult.NOT_LINKED
        _state.update { it.copy(loading = true, error = null) }
        try {
            val db = CloudDb(link.dbUrl)
            val rk = link.readKey
            return withContext(Dispatchers.IO) {
                if (askFresh) {
                    db.put("s/$rk/req", CloudDb.SERVER_TIME)
                }
                val metaAny = db.get("s/$rk/meta")
                val req = (db.get("s/$rk/req") as? Number)?.toLong() ?: 0L
                if (req > 0) prefs.requestAt = req
                @Suppress("UNCHECKED_CAST")
                val meta = (metaAny as? Map<String, Any?>)?.let { JObj(it) }
                if (meta == null) {
                    mutex.withLock { _state.update { it.copy(loading = false, requestPending = req > 0, requestAt = req) } }
                    return@withContext RefreshResult.NO_DATA
                }
                val at = meta.long("at")
                if (meta.long("v", 1) > ClientSnapshot.VERSION) throw SnapshotCodec.TooNew()
                val changed = at != prefs.metaAt || !file().exists()
                var snap = _state.value.snapshot
                if (changed) {
                    val raw = db.getRaw("s/$rk/blob", timeoutMs = 60_000)
                    val blob = Json.parse(raw) as? String ?: throw IllegalStateException("blob")
                    snap = SnapshotCodec.unpack(blob)
                    val f = file()
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    tmp.writeText(blob)
                    if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                    prefs.metaAt = at
                }
                prefs.fetchedAt = System.currentTimeMillis()
                mutex.withLock {
                    _state.update {
                        it.copy(
                            snapshot = snap, publishedAt = at, fetchedAt = prefs.fetchedAt, loading = false, error = null,
                            requestPending = req > at, requestAt = req, linked = true,
                        )
                    }
                }
                if (changed) RefreshResult.UPDATED else RefreshResult.SAME
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.update { it.copy(loading = false) }
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e) }
            throw e
        }
    }

    // ---------------- buyurtma (v2.5) ----------------

    /** "Qayta buyurtma" ni 1-ilovaga yuboradi. Bu savdo emas — 1-ilova egasi ko'rib, savatchaga oladi. */
    suspend fun sendOrder(note: String, source: String, lines: List<OrderLine>): String = withContext(Dispatchers.IO) {
        val id = OrderCodec.newId()
        val body = OrderCodec.toCloud(note, source, lines, prefs.memberName.take(80))
        val link0 = prefs.link ?: throw IllegalStateException("not_linked")
        val db0 = CloudDb(link0.dbUrl)
        // Qoidalar eski kalit ostiga yozishga ham ruxsat beradi — lekin 1-ilova uni ko'rmaydi. Shuning uchun kalit hali
        // amaldaligini (meta bor-yo'qligini) tekshiramiz; eskirgan bo'lsa qayta kirib, yangi kalit ostiga yozamiz.
        val stale = runCatching { db0.get("s/${link0.readKey}/meta/at") == null }.getOrDefault(false)
        if (stale) {
            when (relogin(true)) {
                LoginRepository.Relogin.REVOKED -> { onRevoked(); throw AccessRevokedException() }
                else -> Unit
            }
        }
        val l = prefs.link ?: throw IllegalStateException("not_linked")
        CloudDb(l.dbUrl).put("s/${l.readKey}/ord/$id", body)
        prefs.myOrders = (prefs.myOrders.split('|').filter { it.isNotEmpty() } + id).takeLast(50).joinToString("|")
        id
    }

    /** Shu telefondan yuborilgan buyurtmalar va holati (yangisi birinchi). */
    suspend fun myOrders(): List<OrderRequest> = withContext(Dispatchers.IO) {
        val link = prefs.link ?: return@withContext emptyList()
        val mine = prefs.myOrders.split('|').filter { it.isNotEmpty() }.toSet()
        if (mine.isEmpty()) return@withContext emptyList()
        @Suppress("UNCHECKED_CAST")
        val raw = CloudDb(link.dbUrl).get("s/${link.readKey}/ord") as? Map<String, Any?> ?: emptyMap()
        raw.filterKeys { it in mine }.mapNotNull { (k, v) -> OrderCodec.fromCloud(k, v) }.sortedByDescending { it.at }
    }
}
