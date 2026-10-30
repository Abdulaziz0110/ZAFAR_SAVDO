package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class LoginTest {
    @Test fun pbkdf2_matchesJdk() {
        val salt = "tuz123".toByteArray()
        val mine = LoginRecord.pbkdf2("parol-01".toCharArray(), salt, 1000)
        val jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec("parol-01".toCharArray(), salt, 1000, 256)).encoded
        assertEquals(jdk.toList(), mine.toList())
    }

    @Test fun encryptDecrypt_andWrongPassword() {
        val g = LoginRecord.Grant(CloudDb.newKey(), CloudDb.newKey(), "Dadaxon Aka (TRZ)", "Sotuvchi Ali", "S9")
        val rec = LoginRecord.encrypt(g, "pista2026", iterations = 2000)
        assertEquals(g, LoginRecord.decrypt(rec, "pista2026"))
        try { LoginRecord.decrypt(rec, "pista2027"); fail() } catch (e: LoginRecord.WrongPassword) { }
        // Shifrlangan matnda kalit ko'rinmaydi
        assertTrue(!Json.write(rec).contains(g.readKey))
    }

    @Test fun keyDependsOnShopAndPhone() {
        val a = LoginRecord.key("https://a-default-rtdb.firebaseio.com", "+998 90 123 45 67")
        assertEquals(a, LoginRecord.key("a-default-rtdb.firebaseio.com/", "901234567"))
        assertNotEquals(a, LoginRecord.key("https://b-default-rtdb.firebaseio.com", "901234567"))
        assertEquals(40, a.length)
    }

    @Test fun shopCode() {
        assertEquals("dokon-1a2b3", ShopCode.fromUrl("https://dokon-1a2b3-default-rtdb.firebaseio.com/"))
        assertEquals("dokon-1a2b3@europe-west1", ShopCode.fromUrl("dokon-1a2b3-default-rtdb.europe-west1.firebasedatabase.app"))
        assertEquals("https://dokon-1a2b3-default-rtdb.firebaseio.com", ShopCode.toUrl(" Dokon-1a2b3 "))
        assertEquals("https://dokon-1a2b3-default-rtdb.europe-west1.firebasedatabase.app", ShopCode.toUrl("dokon-1a2b3@europe-west1"))
        assertEquals("https://x-default-rtdb.firebaseio.com", ShopCode.toUrl("https://x-default-rtdb.firebaseio.com"))
        assertNull(ShopCode.toUrl("a b"))
        assertNull(ShopCode.toUrl(""))
    }

    @Test fun phone() {
        assertEquals("998901234567", Phone.normalize("+998 (90) 123-45-67"))
        assertEquals("998901234567", Phone.normalize("90 123 45 67"))
        assertEquals("+998 90 123 45 67", Phone.pretty("901234567"))
    }

    @Test fun rules_loginRecordReadableOnlyByKey() {
        val fake = FakeRtdb("S")
        val admin = CloudDb("https://x.firebaseio.com", "S", fake.http())
        val client = CloudDb("https://x.firebaseio.com", "", fake.http())
        val h = LoginRecord.key("https://x.firebaseio.com", "901234567")
        admin.put("a/$h", LoginRecord.encrypt(LoginRecord.Grant("r".repeat(32), "", "N", "M", "1"), "parol1", iterations = 1000))
        val g = LoginRecord.decrypt(client.get("a/$h"), "parol1")
        assertEquals("r".repeat(32), g.readKey)
        try { client.get("a"); fail() } catch (e: CloudDb.CloudException) { assertTrue(e.denied) }
        try { client.put("a/$h", mapOf("x" to 1)); fail() } catch (e: CloudDb.CloudException) { assertTrue(e.denied) }
        assertTrue(CloudDb.RULES.contains("\"a\""))
    }
}
