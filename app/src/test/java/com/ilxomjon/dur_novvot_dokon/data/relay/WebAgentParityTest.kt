package com.ilxomjon.dur_novvot_dokon.data.relay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v3.10: iPhone (veb) agent bilan bir xil kalit va kirish isboti. Xuddi shu qiymatlar web/agent/test/core.test.js da
 * tekshiriladi — ikkala tomonda o'zgarsa, test yiqiladi.
 */
class WebAgentParityTest {
    @Test fun proofMatchesWebAgent() {
        assertEquals("-xMrPWVmmOdF8kE9Ng3eZ-47qsFGhO2R-FcTzbLGoGg", RelayCodec.proof("secret12", "SaltSaltSaltSaltSaltSaltSalt2345"))
    }

    @Test fun oidIsSha256Prefix() {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest("7\u00011\u0001abc".toByteArray(Charsets.UTF_8))
        assertEquals(d.joinToString("") { "%02x".format(it) }.take(40), RelayCodec.oid("7", "1", "abc"))
    }
}
