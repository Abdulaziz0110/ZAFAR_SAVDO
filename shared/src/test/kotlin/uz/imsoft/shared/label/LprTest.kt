package uz.imsoft.shared.label

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

class LprTest {
    /** pc/label_printer.py dagi tspl_bytes bilan bir xil natija (40×24, chapdagi 8 nuqta qora). */
    @Test
    fun tsplMatchesPythonVersion() {
        val w = 40; val h = 24
        val black = BooleanArray(w * h) { (it % w) < 8 }
        val b = Tspl.build(black, w, h, 3, 5.0, 3.0, 2.0)
        val s = String(b, Charsets.ISO_8859_1)
        assertTrue(s.startsWith("SIZE 5 mm,3 mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\n"))
        val i = s.indexOf("BITMAP 0,0,5,24,0,") + "BITMAP 0,0,5,24,0,".length
        assertEquals(0x00, b[i].toInt() and 0xFF)
        assertEquals(0xFF, b[i + 1].toInt() and 0xFF)
        assertTrue(s.endsWith("\r\nPRINT 1,3\r\n"))
        assertEquals(i + 5 * 24 + "\r\nPRINT 1,3\r\n".length, b.size)
        // to'liq bo'lmagan bayt: 36 nuqta — oxirgi 4 bit oq (1)
        val b2 = Tspl.build(BooleanArray(36 * 2) { true }, 36, 2, 1, 4.5, 1.0, rotate = true)
        val s2 = String(b2, Charsets.ISO_8859_1)
        assertTrue(s2.contains("SIZE 4.5 mm,1 mm") && s2.contains("DIRECTION 0"))
        val j = s2.indexOf("BITMAP 0,0,5,2,0,") + "BITMAP 0,0,5,2,0,".length
        assertEquals(0x0F, b2[j + 4].toInt() and 0xFF)
    }

    /** Soxta Windows LPD: buyruqlar tartibi, nazorat fayli va ma'lumot aynan yetadi. */
    @Test
    fun lprProtocol() {
        val srv = ServerSocket(0)
        val got = ByteArrayOutputStream()
        var control = ""
        var queue = ""
        val t = thread {
            srv.accept().use { c ->
                val inp = DataInputStream(c.getInputStream())
                val out = c.getOutputStream()
                fun line(): String { val sb = StringBuilder(); while (true) { val b = inp.read(); if (b == '\n'.code || b < 0) break; sb.append(b.toChar()) }; return sb.toString() }
                val first = line(); queue = first.substring(1); out.write(0)
                repeat(2) {
                    val sub = line()
                    val len = sub.substring(1).substringBefore(' ').toInt()
                    out.write(0)
                    val body = ByteArray(len); inp.readFully(body); inp.read()
                    if (sub[0] == '\u0002') control = String(body) else got.write(body)
                    out.write(0)
                }
            }
        }
        val data = ByteArray(5000) { (it % 251).toByte() }
        Lpr.print("127.0.0.1", "xprinter", data, 7, port = srv.localPort)
        t.join(5000)
        srv.close()
        assertEquals("xprinter", queue)
        assertArrayEquals(data, got.toByteArray())
        assertTrue(control.contains("ldfA007android\n"))
        assertTrue(control.startsWith("Handroid\n"))
    }

    @Test(expected = Lpr.LprError::class)
    fun lprRefusedQueue() {
        val srv = ServerSocket(0)
        val t = thread { srv.accept().use { c -> c.getInputStream().read(ByteArray(64)); c.getOutputStream().write(1) } }
        try { Lpr.print("127.0.0.1", "yoq", byteArrayOf(1), 1, port = srv.localPort) } finally { t.join(3000); srv.close() }
    }
}
