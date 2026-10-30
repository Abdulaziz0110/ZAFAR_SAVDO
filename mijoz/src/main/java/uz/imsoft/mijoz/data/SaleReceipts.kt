package uz.imsoft.mijoz.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.imsoft.mijoz.Locator
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj

/**
 * v2.6: 1-ilovadagi har yangi savdo chekini shu telefonning printer navbatiga olish (mijoz o'zi yoqadi).
 * Sozlama bulutda (`p/{navbat}/auto`) — 1-ilova shuni ko'rib chekni yuboradi. Cheklar navbatda turadi va printer
 * ulanishi bilan (Printer bo'limidagi servis yoniq bo'lsa) hammasi ketma-ket chiqadi.
 */
object SaleReceipts {
    class Pending(val key: String, val at: Long, val text: String)

    private fun db(): Pair<CloudDb, String>? {
        val l = Locator.prefs.link ?: return null
        if (l.queueKey.isEmpty()) return null
        return CloudDb(l.dbUrl) to l.queueKey
    }

    val available: Boolean get() = !Locator.prefs.link?.queueKey.isNullOrEmpty()

    suspend fun set(on: Boolean) = withContext(Dispatchers.IO) {
        val (db, qk) = db() ?: throw IllegalStateException("no_queue")
        db.put("p/$qk/auto", on)
        Locator.prefs.saleReceipts = on
    }

    /** Bulutdagi qiymat (boshqa telefondan o'zgargan bo'lishi mumkin). null — o'qib bo'lmadi. */
    suspend fun fetch(): Boolean? = withContext(Dispatchers.IO) {
        val (db, qk) = db() ?: return@withContext null
        runCatching { (db.get("p/$qk/auto") as? Boolean) == true }.getOrNull()?.also { Locator.prefs.saleReceipts = it }
    }

    /** Navbatdagi cheklar (eskisi birinchi). Kompyuterdan kelganlarida izoh bo'lmaydi. */
    suspend fun pending(): List<Pending> = withContext(Dispatchers.IO) {
        val (db, qk) = db() ?: return@withContext emptyList()
        val keys = db.keys("q/$qk").sorted()
        @Suppress("UNCHECKED_CAST")
        val jl = runCatching { db.get("p/$qk/jl") as? Map<String, Any?> }.getOrNull().orEmpty()
        // Chop etilgan/eskirgan izohlar tozalanadi
        for (k in jl.keys) if (k !in keys) runCatching { db.delete("p/$qk/jl/$k") }
        keys.map { k ->
            @Suppress("UNCHECKED_CAST")
            val o = (jl[k] as? Map<String, Any?>)?.let { JObj(it) }
            Pending(k, o?.long("t") ?: 0L, o?.str("s").orEmpty())
        }
    }
}
