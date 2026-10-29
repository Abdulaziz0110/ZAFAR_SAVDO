package uz.imsoft.shared.report

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogMatchTest {
    data class P(val id: String, val name: String)
    private val cat = listOf(P("1", "Pista Dolana (D)"), P("2", "Pista Dolana (DA)"), P("3", "Orik Subxon 120"), P("4", "Keshu 210"), P("5", "Bodom Yongoq M"))
    private val m = CatalogMatch(cat, { it.name }, { it.id })

    @Test fun exactByNameOrId() {
        assertEquals("1", m.match("pista dolana (d)").item!!.id)
        assertEquals(CatalogMatch.Level.EXACT, m.match("ПИСТА ДОЛАНА (D)").level)
        assertEquals("4", m.match("whatever", "4").item!!.id)
    }

    @Test fun approxNeedsConfirmation() {
        val r = m.match("Orik Subxon 116")
        assertEquals(CatalogMatch.Level.APPROX, r.level)
        assertEquals("3", r.item!!.id)
        assertEquals(CatalogMatch.Level.APPROX, m.match("Bodom Yongok M").level)
    }

    @Test fun noneForUnrelated() {
        assertEquals(CatalogMatch.Level.NONE, m.match("Kompot Smes 1 kg").level)
        assertEquals(CatalogMatch.Level.NONE, m.match("").level)
    }
}
