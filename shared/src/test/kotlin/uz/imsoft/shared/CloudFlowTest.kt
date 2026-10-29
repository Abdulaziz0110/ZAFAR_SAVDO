package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.report.CardParser
import uz.imsoft.shared.report.ClientSnapshot
import uz.imsoft.shared.report.DebtInfo
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.PriceItem
import uz.imsoft.shared.report.SnapshotCodec
import uz.imsoft.shared.report.TableReader

/**
 * 1-ilova (secret bilan) -> bulut -> 2-ilova (secret'siz, faqat ulanish kodi) oqimi, soxta Firebase bilan.
 * Ilovalardagi ShareRepository/SnapshotRepository/PrintService aynan shu chaqiriqlarni ishlatadi.
 */
class CloudFlowTest {
    private val url = "https://imsoft-test-default-rtdb.firebaseio.com"

    @Test fun publishThenReadAsClient() {
        FakeRtdb("SECRET").use { fake ->
            val owner = CloudDb(url, "SECRET", fake.http())
            owner.setRules(CloudDb.RULES)
            assertNotNull(fake.rulesSet)
            val rk = CloudDb.newKey()
            val qk = CloudDb.newKey()

            val entries = CardParser.parse(TableReader.read(javaClass.classLoader!!.getResourceAsStream("cards/card.htm")!!.readBytes(), "card.htm"))
            val merged = LedgerMerge.merge(emptyList(), emptyList(), entries, 1L, "file")
            val snap = ClientSnapshot(
                5L, "S9", "Dadaxon Aka (TRZ)", 12650.0, DebtInfo(11_130_000.0, 0.0, 4L, "server"),
                listOf(PriceItem("T1", "B Mayiz 134", "Mayiz", 71000.0, 5.61, 70000.0, 5.53)), merged.entries, merged.coverage, 3L,
            )
            val blob = SnapshotCodec.pack(snap)
            owner.update("s/$rk", linkedMapOf("blob" to blob, "meta" to linkedMapOf("v" to 1, "at" to CloudDb.SERVER_TIME, "n" to snap.ledger.size)))

            // 2-ilova: kod orqali
            val code = LinkCode.decode(LinkCode(url, rk, qk, snap.clientName).encode())!!
            val client = CloudDb(code.dbUrl, "", fake.http())
            val meta = JObj(client.get("s/$rk/meta") as Map<String, Any?>)
            assertTrue(meta.long("at") >= fake.now - 10)
            val back = SnapshotCodec.unpack(Json.parse(client.getRaw("s/$rk/blob")) as String)
            assertEquals(snap, back)

            // So'rov yozish mumkin, boshqa narsa — yo'q
            client.put("s/$rk/req", CloudDb.SERVER_TIME)
            assertTrue((owner.get("s/$rk/req") as Number).toLong() > 0)
            expectDenied { client.put("s/$rk/blob", "x") }
            expectDenied { client.get("") }
            expectDenied { client.get("s") }
            expectDenied { CloudDb(url, "", fake.http()).get("s/short") }
            expectDenied { CloudDb(url, "WRONG", fake.http()).setRules("{}") }
        }
    }

    @Test fun printQueue_ownerWritesClientConsumes() {
        FakeRtdb("SECRET").use { fake ->
            val owner = CloudDb(url, "SECRET", fake.http())
            val qk = CloudDb.newKey()
            for (i in 3 downTo 1) owner.put("q/$qk/c0000000000000000000$i", linkedMapOf("t" to CloudDb.SERVER_TIME, "b" to B64.encode(byteArrayOf(0x1B, 0x40, i.toByte())), "n" to 3))
            // Idempotent: xuddi shu kalit qayta yuborilsa — bitta yozuv
            owner.put("q/$qk/c00000000000000000001", linkedMapOf("t" to CloudDb.SERVER_TIME, "b" to B64.encode(byteArrayOf(0x1B, 0x40, 1)), "n" to 3))
            val client = CloudDb(url, "", fake.http())
            val keys = client.keys("q/$qk")
            assertEquals(listOf("c00000000000000000001", "c00000000000000000002", "c00000000000000000003"), keys)
            val item = JObj(client.get("q/$qk/${keys[0]}") as Map<String, Any?>)
            assertEquals(1, B64.decode(item.str("b"))[2].toInt())
            client.delete("q/$qk/${keys[0]}")
            assertEquals(2, client.keys("q/$qk").size)
            // Mijoz chek QO'SHA olmaydi
            expectDenied { client.put("q/$qk/cX", mapOf("b" to "AA==")) }
            // Holat va signal
            client.put("p/$qk/status", linkedMapOf("at" to CloudDb.SERVER_TIME, "ready" to true, "text" to "ok", "queue" to 2))
            assertEquals(true, (owner.get("p/$qk/status") as Map<*, *>)["ready"])
            client.delete("q/$qk/${keys[1]}"); client.delete("q/$qk/${keys[2]}")
            assertNull(client.get("q/$qk"))
            assertEquals(emptyList<String>(), client.keys("q/$qk"))
        }
    }

    private fun expectDenied(block: () -> Unit) {
        try { block(); throw AssertionError("ruxsat berilmasligi kerak edi") } catch (e: CloudDb.CloudException) { assertTrue(e.denied) }
    }
}
