package uz.imsoft.shared.report

import uz.imsoft.shared.B64
import uz.imsoft.shared.Json
import java.security.MessageDigest

/**
 * v3.7: Admin "1C tarixi" (barcha savdolar, 1 yil) agentlarga bulut orqali — 10 kunlik bo'laklar (b2026110, b2026111,
 * b2026112: oyning 1-10, 11-20, 21-31 kunlari). Faqat o'zgargan bo'lak qayta yuklanadi (xesh bo'yicha) — trafik kam.
 */
object HistoryPack {
    fun bucket(stamp: Long): String {
        val ym = stamp / 100_000_000
        val d = ((stamp / 1_000_000) % 100).toInt()
        return "b" + ym + minOf(maxOf(d - 1, 0) / 10, 2)
    }

    fun split(entries: List<LedgerEntry>): Map<String, List<LedgerEntry>> = entries.groupBy { bucket(it.stamp) }

    fun json(list: List<LedgerEntry>): String = Json.write(list.map { SnapshotCodec.entryToList(it) })

    fun hash(json: String): String =
        MessageDigest.getInstance("SHA-1").digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)

    fun pack(json: String): String = B64.encode(B64.gzip(json.toByteArray(Charsets.UTF_8)))

    fun unpack(blob: String): List<LedgerEntry> {
        val raw = String(B64.gunzip(B64.decode(blob)), Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        return (Json.parse(raw) as? List<*>).orEmpty().mapNotNull { (it as? List<Any?>)?.let { a -> runCatching { SnapshotCodec.entryFromList(a) }.getOrNull() } }
    }
}
