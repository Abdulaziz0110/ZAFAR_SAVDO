package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v4.0: kalitsiz ishlash — do'kon kodi, Mijoz kodi (agentlar kalitisiz), biriktirish. */
class V40DeviceTest {
    private val url = "https://shop-1-default-rtdb.europe-west1.firebasedatabase.app"
    private val ak = "AbcdEfghJkmnPqrsTuvwXyz234567892"
    private val dk = "ZyxwVutsRqpnMkjhGfedCba987654322"

    @Test fun shopCodeRoundTrip() {
        val s = DeviceCodes.Shop(url, ak, dk)
        val code = DeviceCodes.encodeShop(s)
        assertTrue(code.startsWith("IMSHOP1:"))
        assertEquals(s, DeviceCodes.decodeShop("Kod: $code (nusxa)"))
        assertEquals(s.dev, DeviceCodes.decodeDev(code))
        assertNull(DeviceCodes.decodeShop("IMSHOP1:buzuq"))
        assertNull(DeviceCodes.decodeShop(""))
    }

    @Test fun devCodeHasNoAgentKey() {
        val dev = DeviceCodes.encodeDev(DeviceCodes.Dev(url, dk))
        val raw = String(B64.decode(dev.removePrefix(DeviceCodes.DEV_PREFIX)), Charsets.UTF_8)
        assertTrue(!raw.contains(ak))
        assertEquals(DeviceCodes.Dev(url, dk), DeviceCodes.decodeDev(dev))
        // Gradle (mijoz/build.gradle.kts devCode) bilan bir xil format: java.util.Base64 url, paddingsiz
        val gradle = "IMSDEV1:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("$url\n$dk".toByteArray())
        assertEquals(DeviceCodes.Dev(url, dk), DeviceCodes.decodeDev(gradle))
    }

    @Test fun paths() {
        val d = DeviceCodes.Dev(url, dk)
        assertEquals("d/$dk/abc/i", d.path("abc", "i"))
        // build skripti (python) bilan bir xil: sha256(devKey + "|upd") hex[:24]
        val h = java.security.MessageDigest.getInstance("SHA-256").digest("$dk|upd".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(h.take(24), DeviceCodes.updateDir(dk))
    }

    @Test fun assignment() {
        val a = DeviceAssignment.parse(mapOf("s" to "blocked", "a" to listOf("7", "Ali", "", "998"), "n" to "Ali", "at" to 5L))
        assertTrue(a?.blocked == true)
        assertEquals("7", a?.agentId)
        val i = DeviceInfo.parse("id1", mapOf("i" to mapOf("m" to "Xiaomi", "r" to "agent", "v" to "4.0", "vc" to 25L, "at" to 9L), "c" to mapOf("at" to 3L, "t" to "boom")))
        assertEquals("Xiaomi", i?.model)
        assertNull(i?.assignment)
        assertEquals("boom", i?.crash)
        // Qoidalar JSON'i buzilmagan
        assertTrue(Json.parse(CloudDb.RULES) is Map<*, *>)
    }
}
