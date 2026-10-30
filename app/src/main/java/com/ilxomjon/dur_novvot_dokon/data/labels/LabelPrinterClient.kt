package com.ilxomjon.dur_novvot_dokon.data.labels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.B64
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

/** Kompyuterdagi label_printer.py haqida (printer nomi, qog'oz o'lchami). */
data class PrinterInfo(
    val host: String,
    val printer: String,
    val mode: String,
    val wPx: Int,
    val hPx: Int,
    val dpi: Int,
    val wMm: Double,
    val hMm: Double,
    val at: Long = 0L,
    val ok: Boolean = true,
    val text: String = "",
)

/** UDP qidiruvda topilgan kompyuter. */
data class FoundPc(val ip: String, val port: Int, val host: String, val printer: String)

/**
 * Narx yorlig'i printeri (USB bilan kompyuterga ulangan Xprinter) ga yuborish:
 *  1) Wi-Fi/LAN: kompyuterdagi label_printer.py ga to'g'ridan-to'g'ri TCP (tez, internetsiz);
 *  2) bo'lmasa — bulut navbati `L/{kalit}` (kompyuter internetda bo'lsa chiqaradi).
 * Bitta ish ikkala yo'ldan borsa ham kompyuter ish raqami bo'yicha bir marta chop etadi.
 * HTTP emas, oddiy TCP — Android'ning "cleartext" cheklovi tegmaydi.
 */
class LabelPrinterClient {
    class Job(val id: String, val labels: List<Pair<ByteArray, Int>>, val names: List<String>)

    sealed class Sent {
        /** Kompyuter chop etdi (LAN). */
        data class Printed(val count: Int, val printer: String) : Sent()
        /** Bulut navbatiga qo'yildi — kompyuter internetda bo'lsa chiqadi. */
        data class Queued(val count: Int) : Sent()
    }

    class PrintError(val reason: String) : Exception(reason)

    private fun frame(o: Map<String, Any?>): ByteArray = Json.write(o).toByteArray(Charsets.UTF_8)

    /** TCP so'rov: "SLBL" + uzunlik (4 bayt) + JSON; javob ham shunday. */
    private fun call(host: String, port: Int, body: Map<String, Any?>, connectMs: Int = 2500, readMs: Int = 90_000): JObj {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), connectMs)
            s.soTimeout = readMs
            val out = DataOutputStream(s.getOutputStream().buffered())
            val data = frame(body)
            out.write("SLBL".toByteArray(Charsets.US_ASCII))
            out.writeInt(data.size)
            out.write(data)
            out.flush()
            val inp = DataInputStream(s.getInputStream().buffered())
            val magic = ByteArray(4); inp.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != "SLBL") throw PrintError("bad_reply")
            val n = inp.readInt()
            if (n !in 0..1_000_000) throw PrintError("bad_reply")
            val r = ByteArray(n); inp.readFully(r)
            return JObj.parse(String(r, Charsets.UTF_8))
        }
    }

    private fun infoOf(o: JObj, host: String) = PrinterInfo(
        host = o.str("host").ifEmpty { host }, printer = o.str("printer"), mode = o.str("mode"),
        wPx = o.long("wpx").toInt(), hPx = o.long("hpx").toInt(), dpi = o.long("dpi").toInt(),
        wMm = o.num("wmm"), hMm = o.num("hmm"), at = o.long("at"), ok = o.bool("ok", true), text = o.str("text"),
    )

    suspend fun infoLan(host: String, port: Int, key: String): PrinterInfo = withContext(Dispatchers.IO) {
        val r = call(host, port, mapOf("v" to 1, "op" to "info", "key" to key), readMs = 8000)
        if (!r.bool("ok")) throw PrintError(r.str("error").ifEmpty { "error" })
        infoOf(r, host)
    }

    /** Bulutdagi holat (kompyuter har daqiqada yozadi). */
    suspend fun infoCloud(db: CloudDb, key: String): PrinterInfo? = withContext(Dispatchers.IO) {
        @Suppress("UNCHECKED_CAST")
        val m = db.get("P/$key") as? Map<String, Any?> ?: return@withContext null
        infoOf(JObj(m), "")
    }

    /**
     * Wi-Fi tarmog'ida [port] ochiq kompyuterlarni qidirish (telefon tarmog'idagi x.x.x.1..254, parallel).
     * Ulashilgan printer (Windows LPD, 515) uchun — kompyuterda dastur shart emas.
     */
    suspend fun scanPort(port: Int, timeoutMs: Int = 400): List<FoundPc> = withContext(Dispatchers.IO) {
        val prefixes = LinkedHashSet<String>()
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address as? java.net.Inet4Address ?: continue
                    if (!a.isSiteLocalAddress) continue
                    prefixes += a.hostAddress.orEmpty().substringBeforeLast('.') + "."
                }
            }
        }
        val found = java.util.Collections.synchronizedList(ArrayList<FoundPc>())
        val gate = kotlinx.coroutines.sync.Semaphore(48)
        kotlinx.coroutines.coroutineScope {
            for (pre in prefixes) for (i in 1..254) {
                launch {
                    gate.acquire()
                    try {
                        val ip = pre + i
                        val ok = runCatching { Socket().use { s -> s.connect(InetSocketAddress(ip, port), timeoutMs); true } }.getOrDefault(false)
                        if (ok) found += FoundPc(ip, port, "", "")
                    } finally {
                        gate.release()
                    }
                }
            }
        }
        found.sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    /** Wi-Fi tarmog'ida label_printer.py ni qidirish (UDP broadcast, port+1). */
    suspend fun discover(port: Int, timeoutMs: Int = 1500): List<FoundPc> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, FoundPc>()
        DatagramSocket().use { sock ->
            sock.broadcast = true
            sock.soTimeout = 250
            val msg = "SLBL?".toByteArray(Charsets.US_ASCII)
            val targets = LinkedHashSet<InetAddress>()
            runCatching { targets += InetAddress.getByName("255.255.255.255") }
            runCatching {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (ia in ni.interfaceAddresses) ia.broadcast?.let { targets += it }
                }
            }
            for (t in targets) runCatching { sock.send(DatagramPacket(msg, msg.size, t, port + 1)) }
            val end = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < end) {
                val p = DatagramPacket(buf, buf.size)
                try { sock.receive(p) } catch (e: SocketTimeoutException) { continue }
                val o = runCatching { JObj.parse(String(p.data, 0, p.length, Charsets.UTF_8)) }.getOrNull() ?: continue
                if (o.long("slbl") < 1) continue
                val ip = p.address.hostAddress ?: continue
                found[ip] = FoundPc(ip, o.long("port", port.toLong()).toInt(), o.str("host"), o.str("printer"))
            }
        }
        found.values.toList()
    }

    private fun jobBody(job: Job, key: String): Map<String, Any?> = linkedMapOf(
        "v" to 1, "op" to "print", "key" to key, "id" to job.id, "src" to "1-ilova",
        "labels" to job.labels.mapIndexed { i, (png, n) -> linkedMapOf("png" to B64.encode(png), "copies" to n, "name" to job.names.getOrNull(i).orEmpty()) },
    )

    /**
     * Yuborish: avval LAN ([host] bo'lsa), bo'lmasa bulut ([db] bo'lsa). Ikkalasi ham bo'lmasa — xato.
     * LAN javobi yo'qolsa ham bulutga qo'yilgan nusxa takror chop etilmaydi (kompyuter ish raqamini eslaydi).
     */
    suspend fun send(job: Job, host: String, port: Int, key: String, db: CloudDb?): Sent = withContext(Dispatchers.IO) {
        var lanError: Throwable? = null
        if (host.isNotBlank()) {
            try {
                val r = call(host, port, jobBody(job, key))
                if (r.bool("ok")) return@withContext Sent.Printed(r.long("printed").toInt(), r.str("printer"))
                lanError = PrintError(r.str("error").ifEmpty { "error" })
                // Kalit noto'g'ri yoki printer xatosi — bulutga ham yubormaymiz (u ham xuddi shu printer)
                if (r.str("error") == "bad_key" || r.str("error").startsWith("printer")) throw lanError
            } catch (e: PrintError) {
                throw e
            } catch (e: Exception) {
                lanError = e
            }
        }
        if (db != null) {
            // Bulut navbati: katta ishlar 40 tadan bo'linadi (bitta yozuv hajmi kichik bo'lsin)
            val parts = job.labels.indices.chunked(40)
            for ((pi, idx) in parts.withIndex()) {
                val part = Job(if (parts.size == 1) job.id else job.id + "_" + pi, idx.map { job.labels[it] }, idx.map { job.names.getOrElse(it) { "" } })
                db.put("L/$key/${part.id}", jobBody(part, key) + ("at" to CloudDb.SERVER_TIME), timeoutMs = 60_000)
            }
            return@withContext Sent.Queued(job.labels.sumOf { it.second })
        }
        throw lanError ?: PrintError("no_printer")
    }
}
