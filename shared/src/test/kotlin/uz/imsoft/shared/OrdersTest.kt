package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OrdersTest {
    private val secret = "SECRET-123"

    @Test fun codecRoundTrip() {
        val lines = listOf(OrderLine("Pista Dolana (D)", 40.0, "T17"), OrderLine("Bodom \"Yongoq\" M", 2.5))
        val cloud = OrderCodec.toCloud("ertaga ertalab", "20261029161619|S|Savdo So'm", lines)
        val resolved = cloud.mapValues { (k, v) -> if (k == "at") 123L else v }
        val o = OrderCodec.fromCloud("o1761728000000abcd", resolved)!!
        assertEquals(lines, o.lines)
        assertEquals("ertaga ertalab", o.note)
        assertEquals(OrderRequest.Status.SENT, o.status)
        val withSt = resolved + ("st" to mapOf("s" to "taken", "at" to 456L))
        val o2 = OrderCodec.fromCloud("o1", withSt)!!
        assertEquals(OrderRequest.Status.TAKEN, o2.status)
        assertEquals(456L, o2.statusAt)
    }

    @Test fun idsSortByTime() {
        val a = OrderCodec.newId(1_000_000_000_000L)
        val b = OrderCodec.newId(1_000_000_000_001L)
        assertTrue(a < b)
        assertTrue(a.length in 10..40)
    }

    @Test fun rules_clientCanOnlyAddNewOrders() {
        val fake = FakeRtdb(secret)
        val rk = CloudDb.newKey()
        val client = CloudDb("https://x.firebaseio.com", "", fake.http())
        val admin = CloudDb("https://x.firebaseio.com", secret, fake.http())
        val id = OrderCodec.newId()
        client.put("s/$rk/ord/$id", OrderCodec.toCloud("", "src", listOf(OrderLine("A", 1.0))))
        // qayta yozish (o'zgartirish) mumkin emas
        try { client.put("s/$rk/ord/$id", OrderCodec.toCloud("x", "src", listOf(OrderLine("B", 9.0)))); fail() } catch (e: CloudDb.CloudException) { assertTrue(e.denied) }
        // o'chirish mumkin emas
        try { client.delete("s/$rk/ord/$id"); fail() } catch (e: CloudDb.CloudException) { assertTrue(e.denied) }
        // begona maydon mumkin emas
        try { client.put("s/$rk/ord/${OrderCodec.newId()}", OrderCodec.toCloud("", "", emptyList()) + ("hack" to 1)); fail() } catch (e: CloudDb.CloudException) { assertTrue(e.denied) }
        // meta/blob ga yozish mumkin emas
        try { client.put("s/$rk/meta", mapOf("at" to 1)); fail() } catch (e: CloudDb.CloudException) { assertTrue(e.denied) }
        // 1-ilova (secret) holat yozadi
        admin.put("s/$rk/ord/$id/st", OrderCodec.statusValue(OrderRequest.Status.SEEN))
        val back = OrderCodec.fromCloud(id, client.get("s/$rk/ord/$id"))
        assertNotNull(back)
        assertEquals(OrderRequest.Status.SEEN, back!!.status)
        assertEquals(listOf(OrderLine("A", 1.0)), back.lines)
    }

    @Test fun orderCarriesSender() {
        val fake = FakeRtdb(secret)
        val rk = CloudDb.newKey()
        val client = CloudDb("https://x.firebaseio.com", "", fake.http())
        val id = OrderCodec.newId()
        client.put("s/$rk/ord/$id", OrderCodec.toCloud("tez", "cart", listOf(OrderLine("Pista", 5.0, "T1")), "Ali (ishchi)"))
        val back = OrderCodec.fromCloud(id, client.get("s/$rk/ord/$id"))!!
        assertEquals("Ali (ishchi)", back.by)
        assertEquals("cart", back.source)
        assertTrue(CloudDb.RULES.contains("\"by\""))
    }

    @Test fun rulesContainOrders() {
        val r = Json.parse(CloudDb.RULES)
        assertNotNull(r)
        assertTrue(CloudDb.RULES.contains("\"ord\""))
        assertTrue(CloudDb.RULES_VERSION >= 2)
    }
}
