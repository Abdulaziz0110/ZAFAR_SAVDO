package com.ilxomjon.dur_novvot_dokon.data.remote

import com.ilxomjon.dur_novvot_dokon.data.local.CatalogSnapshot
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.domain.ServerConfig
import com.ilxomjon.dur_novvot_dokon.domain.outbox.NotSentException

/**
 * v5.3: ulanish yo'lini ILOVA O'ZI tanlaydi — sozlamada "Server / Admin orqali" tanlash yo'q, VPN kerak emas.
 *
 *  1) do'kon Wi-Fi'sida — to'g'ridan-to'g'ri serverga ([direct], eng tez);
 *  2) server ko'rinmasa yoki telefon mobil internetda — bulut orqali ([cloud]): server ishini do'kon kompyuteri bajaradi.
 *
 * Ikkalasi ham bitta "savollar" jadvaliga bir xil matnni yozadi va yozishdan oldin serverdan qidiradi,
 * shuning uchun yo'l almashsa ham dublikat bo'lmaydi.
 */
class AutoBackend(
    private val direct: Backend,
    private val cloud: Backend,
    private val directReady: () -> Boolean,
    private val cloudReady: () -> Boolean,
    /** Telefon lokal tarmoqda bo'lishi mumkinmi (Wi-Fi yoki VPN). Mobil internetda server baribir ko'rinmaydi. */
    private val localNet: () -> Boolean,
    private val now: () -> Long = { System.currentTimeMillis() },
) : Backend {
    enum class Route { DIRECT, CLOUD }

    @Volatile private var directDownUntil = 0L
    @Volatile private var fails = 0

    /** Oxirgi ishlatilgan yo'l (ekranda "Wi-Fi" / "Bulut" deb ko'rsatish uchun). */
    @Volatile var lastRoute: Route = Route.DIRECT
        private set

    /** Hozir qaysi yo'l tanlanadi (so'rov yubormasdan). */
    fun route(): Route = when {
        !cloudReady() -> Route.DIRECT
        !directReady() -> Route.CLOUD
        !localNet() -> Route.CLOUD
        now() < directDownUntil -> Route.CLOUD
        else -> Route.DIRECT
    }

    val usingCloud: Boolean get() = route() == Route.CLOUD

    /** Tarmoq o'zgardi (Wi-Fi ulandi) — serverni darhol qayta sinab ko'rish. */
    fun retryDirect() { directDownUntil = 0L; fails = 0 }

    private fun unreachable(e: Throwable): Boolean = when (e) {
        is NotSentException -> true
        is ServerException -> e.kind == ServerError.CONNECT || e.kind == ServerError.TIMEOUT || e.kind == ServerError.NOT_CONFIGURED
        else -> false
    }

    /** Server ko'rinmadi: 2, 4, 8 … 16 daqiqagacha bulutdan ishlaydi (begona Wi-Fi'da har safar kutib o'tirmaslik uchun). */
    private fun markDown() {
        fails = minOf(fails + 1, 4)
        directDownUntil = now() + DOWN_MS * (1L shl (fails - 1))
    }

    private fun markUp() { fails = 0 }

    /** O'qish so'rovlari: server ko'rinmasa — bulutdan. */
    private suspend fun <T> read(block: suspend (Backend) -> T): T {
        if (route() == Route.CLOUD) { lastRoute = Route.CLOUD; return block(cloud) }
        return try {
            block(direct).also { lastRoute = Route.DIRECT; markUp() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!cloudReady() || !unreachable(e)) throw e
            markDown()
            lastRoute = Route.CLOUD
            block(cloud)
        }
    }

    override suspend fun testConnection(cfg: ServerConfig): String = read { it.testConnection(cfg) }
    override suspend fun login(phone: String, password: String): RawAgent? = read { it.login(phone, password) }
    override suspend fun fetchSnapshot(agentId: String): CatalogSnapshot = read { it.fetchSnapshot(agentId) }
    override suspend fun fetchDebt(agentId: String, storeId: String): Pair<String, String> = read { it.fetchDebt(agentId, storeId) }
    override suspend fun fetchReport(agentId: String, payload: String): String = read { it.fetchReport(agentId, payload) }
    override suspend fun serverTimeMillis(): Long = read { it.serverTimeMillis() }

    // ---------------- yozish (navbat yadrosi) ----------------

    /** Belgi (watermark) faqat to'g'ridan yo'lda bor; bulutda har savdoning o'z kaliti. */
    override suspend fun maxRequestId(userId: String): Long {
        if (route() == Route.CLOUD) return 0L
        return try {
            direct.maxRequestId(userId)
        } catch (e: NotSentException) {
            if (!cloudReady()) throw e
            markDown()
            0L
        }
    }

    /**
     * Faqat "yetib bormagani ANIQ" ([NotSentException]) bo'lsa bulutga o'tadi. Natija noma'lum xato (ulanish o'rtada
     * uzildi) — o'zicha qaytadi: navbat avval serverdan qidiradi.
     */
    override suspend fun insert(userId: String, type: String, payload: String): String {
        if (route() == Route.CLOUD) { lastRoute = Route.CLOUD; return cloud.insert(userId, type, payload) }
        return try {
            direct.insert(userId, type, payload).also { lastRoute = Route.DIRECT; markUp() }
        } catch (e: NotSentException) {
            if (!cloudReady()) throw e
            markDown()
            lastRoute = Route.CLOUD
            cloud.insert(userId, type, payload)
        }
    }

    /** Avval bulut natijasi (arzon), keyin — yo'l ochiq bo'lsa — serverning o'zidan. */
    override suspend fun find(userId: String, type: String, payload: String, afterId: Long?): String? {
        if (cloudReady()) {
            val viaCloud = try { cloud.find(userId, type, payload, null) } catch (e: NotSentException) { if (route() == Route.CLOUD) throw e else null }
            if (viaCloud != null) return viaCloud
        }
        if (route() == Route.CLOUD) return null
        return try {
            // belgi bulutda olingan (0) bo'lsa — oxirgi yozuvlar orasidan qidiriladi
            direct.find(userId, type, payload, afterId?.takeIf { it > 0L })
        } catch (e: NotSentException) {
            if (!cloudReady()) throw e
            markDown()
            null
        }
    }

    override suspend fun exists(requestId: String): Boolean {
        if (route() == Route.CLOUD) return cloud.exists(requestId)
        return try {
            direct.exists(requestId)
        } catch (e: NotSentException) {
            if (!cloudReady()) throw e
            markDown()
            true
        }
    }

    private companion object { const val DOWN_MS = 120_000L }
}
