package uz.imsoft.shared.onec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v5.3: kassadagi "Paneli" — telefondan tahrirlash. */
class PanelTest {
    private fun panel(vararg filled: Pair<Int, String>): Panel {
        val m = filled.toMap()
        return Panel((1..Panel.SLOTS).map { i -> m[i]?.let { Panel.Slot(it, "Tovar $it") } ?: Panel.EMPTY })
    }

    @Test fun parseNeedsExactly336() {
        assertNull(Panel.parse(emptyList()))
        assertNull(Panel.parse(List(10) { listOf("", "") }))
        val p = Panel.parse(List(Panel.SLOTS) { i -> if (i == 0) listOf("1042", "Fuse Tea") else listOf("", "") })
        assertNotNull(p)
        assertEquals("1042", p?.slots?.get(0)?.code)
        assertTrue(p?.slots?.get(1)?.isEmpty == true)
        assertEquals(8, Panel.PAGES)
        assertEquals(42, p?.page(7)?.size)
    }

    @Test fun changesBecomeLinesWithSlotAsQty() {
        val l = Panel.lines(mapOf(44 to Panel.Slot("7", "Non"), 2 to Panel.EMPTY))
        assertEquals(listOf(2.0, 44.0), l.map { it.qty })
        assertEquals("", l[0].code)
        assertEquals("7", l[1].code)
        val r = WriteRequest("id", WriteKind.PANEL, 1L, lines = l)
        assertNull(r.problem())
    }

    @Test fun validation() {
        assertEquals("lines", WriteRequest("id", WriteKind.PANEL, 1L).problem())
        assertEquals("line", WriteRequest("id", WriteKind.PANEL, 1L, lines = listOf(WriteLine("a", "1", 337.0, 0.0, 0.0))).problem())
        assertEquals("line", WriteRequest("id", WriteKind.PANEL, 1L, lines = listOf(WriteLine("a", "1", 1.5, 0.0, 0.0))).problem())
        assertEquals("line", WriteRequest("id", WriteKind.PANEL, 1L, lines = listOf(WriteLine("a", "1", 3.0, 0.0, 0.0), WriteLine("b", "2", 3.0, 0.0, 0.0))).problem())
    }

    @Test fun suggestFillsFreeSlotsSkippingWhatIsThere() {
        val p = panel(1 to "10", 2 to "20")
        val s = Panel.suggest(p, listOf("20", "30", "10", "40"), limit = 5)
        assertEquals(mapOf(3 to "30", 4 to "40"), s)
    }

    @Test fun suggestStartsFromChosenPage() {
        val p = panel(43 to "10")
        val s = Panel.suggest(p, listOf("30"), fromPage = 1)
        assertEquals(mapOf(44 to "30"), s)
    }

    @Test fun freeSlotsWrapAround() {
        val p = panel()
        val f = p.freeSlots(7)
        assertEquals(295, f.first())
        assertEquals(Panel.SLOTS, f.size)
        assertEquals(294, f.last())
    }
}
