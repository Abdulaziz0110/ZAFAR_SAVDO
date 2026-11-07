package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.imsoft.shared.onec.OneCReportsCodec
import uz.imsoft.shared.onec.PartyField
import uz.imsoft.shared.onec.PriceRule
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteLine
import uz.imsoft.shared.onec.WriteRequest

/** v4.1: kontragent yozuvi, hisobdan chiqarish, hisobotlar, ommaviy narx. */
class V41Test {
    @Test fun partyValidationAndCodec() {
        fun p(kind: String, party: String, f: Map<String, String>) = WriteRequest("w", kind, 1, party, fields = f).problem()
        assertEquals("name", p(WriteKind.PARTY_NEW, "", mapOf(PartyField.PHONE to "998901234567")))
        assertEquals("phone", p(WriteKind.PARTY_NEW, "", mapOf(PartyField.NAME to "Vali", PartyField.PHONE to "abc")))
        assertNull(p(WriteKind.PARTY_NEW, "", mapOf(PartyField.NAME to "Vali", PartyField.PHONE to "+998 90 123-45-67", PartyField.GROUP to PartyField.SUPPLIERS)))
        assertEquals("party", p(WriteKind.PARTY_EDIT, "", mapOf(PartyField.PHONE to "998901234567")))
        assertEquals("empty", p(WriteKind.PARTY_EDIT, "Ali", mapOf(PartyField.GROUP to "c")))
        val r = WriteRequest("w1", WriteKind.PARTY_NEW, 5, fields = mapOf(PartyField.NAME to "Vali", PartyField.REGION to "Toshkent", "hack" to "x"))
        val back = WriteCodec.fromCloud("w1", mapOf("k" to r.kind, "c" to 5L, "p" to WriteCodec.payload(r)))!!
        assertEquals(mapOf(PartyField.NAME to "Vali", PartyField.REGION to "Toshkent"), back.fields)
    }

    @Test fun writeOffValidation() {
        assertEquals("lines", WriteRequest("w", WriteKind.WRITE_OFF, 1).problem())
        assertEquals("line", WriteRequest("w", WriteKind.WRITE_OFF, 1, lines = listOf(WriteLine("Pista", "1", 0.0, 0.0, 0.0))).problem())
        assertNull(WriteRequest("w", WriteKind.WRITE_OFF, 1, lines = listOf(WriteLine("Pista", "1", 2.0, 0.0, 0.0))).problem())
    }

    @Test fun reportsParse() {
        val json = """{"v":1,"f":"20261101","t":"20261102","at":5,
            "profit":{"tot":[5000,8000,3000,6,10,4],"rows":[["Mayiz","77",2,0,0,0,6,10,4],["Pista","1001",10,5000,8000,3000,0,0,0]],"n":2,"disc":[500,1]},
            "exp":[["Ijara",2000,0]],
            "cash":{"bal":[100,20,5,3,0],"turn":[["Savdo",50,0,10,0,0,0,1,0]]},
            "stock":{"rows":[["Pista","1001",3,0,500,1500],["Mayiz","77",-1,1,3,-3]],"n":2,"vs":1500,"vu":-3,"neg":1},
            "debt":{"pos":[700,0],"neg":[900,2],"top":[["Ali",700,0,0]],"owe":[["Zavod",900,2,1]]},"err":["x: y"]}"""
        val r = OneCReportsCodec.parse(json.toByteArray())
        assertEquals(20261101000000L, r.from)
        assertEquals(3000.0 + 4 * 12000, r.profit!!.profit.total(12000.0), 0.01)
        assertEquals(3000.0 + 48000 - 2000, r.netProfit(12000.0)!!, 0.01)
        assertEquals(125.0, r.cash!!.totalSom, 0.01)
        assertEquals(60.0, r.cash!!.turn[0].inSom, 0.01)
        assertTrue(r.stock!!.rows[1].usd)
        assertEquals(1500.0 - 36000, r.stock!!.total(12000.0), 0.01)
        assertTrue(r.debts!!.owe[0].supplier)
        assertEquals(listOf("x: y"), r.errors)
    }

    @Test fun priceRule() {
        assertEquals(11000.0, PriceRule.apply(10000.0, 0.0, PriceRule.Mode.PERCENT, 10.0, 100.0)!!, 0.0)
        assertEquals(12000.0, PriceRule.apply(0.0, 9800.0, PriceRule.Mode.COST_PLUS, 2000.0, 500.0)!!, 0.0)
        assertNull(PriceRule.apply(5000.0, 0.0, PriceRule.Mode.COST_PLUS, 2000.0, 100.0))
        assertEquals(10780.0, PriceRule.apply(0.0, 9800.0, PriceRule.Mode.COST_PERCENT, 10.0, 10.0)!!, 0.0)
        assertEquals(3.35, PriceRule.apply(3.0, 0.0, PriceRule.Mode.PERCENT, 11.5, 0.01)!!, 1e-9)
        assertNull(PriceRule.apply(1000.0, 0.0, PriceRule.Mode.PERCENT, -100.0, 100.0))
    }
}
