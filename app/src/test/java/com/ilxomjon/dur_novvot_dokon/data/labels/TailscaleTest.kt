package com.ilxomjon.dur_novvot_dokon.data.labels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** pc 39: Tailscale manzilida avval kompyuter ko'prigi orqali chop etiladi. */
class TailscaleTest {
    @Test
    fun tailscaleRange() {
        assertTrue(isTailscale("100.120.242.27"))
        assertTrue(isTailscale(" 100.64.0.1 "))
        assertTrue(isTailscale("100.127.255.254"))
        assertFalse(isTailscale("100.128.0.1"))
        assertFalse(isTailscale("100.63.0.1"))
        assertFalse(isTailscale("192.168.1.5"))
        assertFalse(isTailscale("kompyuter"))
        assertFalse(isTailscale(""))
    }
}
