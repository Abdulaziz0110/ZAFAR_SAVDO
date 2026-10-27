package com.ilxomjon.dur_novvot_dokon.data

import com.ilxomjon.dur_novvot_dokon.data.remote.PayloadMatch
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupException
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class BackupCryptoAndMatchTest {
    private val crypto = BackupRepository.Crypto

    @Test fun encryptDecryptRoundTrip() {
        val data = ByteArray(50_000).also { Random(1).nextBytes(it) }
        val enc = crypto.encrypt(data, "Parol-123".toCharArray())
        assertTrue(crypto.isEncrypted(enc))
        assertFalse(crypto.isEncrypted(data))
        assertArrayEquals(data, crypto.decrypt(enc, "Parol-123".toCharArray()))
    }

    @Test fun wrongPasswordIsDetected() {
        val enc = crypto.encrypt("zip".toByteArray(), "a".toCharArray())
        try {
            crypto.decrypt(enc, "b".toCharArray())
            throw AssertionError("must fail")
        } catch (e: BackupException) {
            assertEquals(BackupException.Kind.WRONG_PASSWORD, e.kind)
        }
    }

    @Test fun sameCiphertextNeverRepeats() {
        val a = crypto.encrypt("x".toByteArray(), "p".toCharArray())
        val b = crypto.encrypt("x".toByteArray(), "p".toCharArray())
        assertFalse(a.contentEquals(b)) // tasodifiy salt/iv
    }

    @Test fun payloadMatch() {
        val p = "7^S9^1000^0^26.10.2026 14:05:09^12650^Қарз тўлови^1000^0^0^1~"
        assertTrue(PayloadMatch.same(p, p))
        assertTrue(PayloadMatch.same(p, p + "   "))                                     // char ustun
        assertTrue(PayloadMatch.same(p, p.map { if (it.code > 127) '?' else it }.joinToString(""))) // varchar
        assertFalse(PayloadMatch.same(p, p.replace("1000", "2000")))
        assertFalse(PayloadMatch.same(p, p.dropLast(1)))
        assertFalse(PayloadMatch.same("abc", "a?c"))                                     // ASCII harf '?' emas
    }
}
