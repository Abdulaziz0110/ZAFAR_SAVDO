package uz.imsoft.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uz.imsoft.shared.onec.OneCCodec
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.OneCLine
import uz.imsoft.shared.onec.OneCSales
import uz.imsoft.shared.onec.Supplier
import uz.imsoft.shared.onec.Suppliers
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteLine
import uz.imsoft.shared.onec.WriteRequest
import uz.imsoft.shared.sched.WorkHours
import java.util.Calendar
import java.util.TimeZone

class V32Test {
    private val secret = "SECRET-123"

    @Test fun workHours() {
        val w = WorkHours(true, 8 * 60, 20 * 60)
        assertFalse(w.isWorkTime(7 * 60 + 59))
        assertTrue(w.isWorkTime(8 * 60))
        assertTrue(w.isWorkTime(19 * 60 + 59))
        assertFalse(w.isWorkTime(20 * 60))
        assertEquals(19 * 60 + 59, w.finalMinute)
        // Tungi smena
        val n = WorkHours(true, 20 * 60, 4 * 60)
        assertTrue(n.isWorkTime(23 * 60))
        assertTrue(n.isWorkTime(3 * 60))
        assertFalse(n.isWorkTime(12 * 60))
        // O'chiq — doim
        assertTrue(WorkHours(false, 8 * 60, 9 * 60).isWorkTime(23 * 60))
        // Saqlash/o'qish, buzuq qiymat
        assertEquals(n, WorkHours.decode(n.encode()))
        assertEquals(WorkHours.DEFAULT, WorkHours.decode("x"))
        assertEquals(WorkHours.DEFAULT, WorkHours.decode(null))
        // Keyingi oxirgi yuklash: 19:59
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear(); cal.set(2026, 10, 1, 10, 0, 0)
        val now = cal.timeInMillis
        val next = w.nextFinal(now, Calendar.getInstance(TimeZone.getTimeZone("UTC")))
        assertEquals(now + (9 * 60 + 59) * 60_000L, next)
        // Vaqti o'tgan bo'lsa — ertaga
        cal.set(2026, 10, 1, 21, 0, 0)
        val late = cal.timeInMillis
        val next2 = w.nextFinal(late, Calendar.getInstance(TimeZone.getTimeZone("UTC")))
        assertEquals(late + (22 * 60 + 59) * 60_000L, next2)
        assertEquals("08:05", WorkHours.format(485))
    }

    @Test fun salesCodec() {
        val json = """{"v":1,"f":"20261001","t":"20261031","at":5,"docs":[
          {"id":"u1","k":"r","no":"0000000123","d":"20261031143000","c":"Dadaxon Aka (TRZ)","cc":12,"s":1500000,"u":0,"n":"","l":[["Pista","1001",2,750000,1500000,0,0]]},
          {"id":"u2","k":"q","no":7.0,"d":"2026-10-30 09:00:00","c":"Naqd","s":100000,"u":0,"l":[["Bodom","",1,100000,100000]]},
          {"id":"bad","k":"r","no":"1","d":"","c":"x"}],"err":["ПродажаРозница: yo'q"]}"""
        val s = OneCCodec.parseSales(json.toByteArray())
        assertEquals(2, s.docs.size)
        assertEquals("0000000123", s.docs[0].number)
        assertEquals(20261031143000L, s.docs[0].stamp)
        assertEquals("12", s.docs[0].partyCode)
        assertEquals("7", s.docs[1].number)
        assertEquals(20261030090000L, s.docs[1].stamp)
        assertEquals(1, s.docs[0].lines.size)
        assertEquals(1, s.filter("pista dadaxon").size)
        assertEquals(1, s.filter("", setOf(OneCDoc.CLIENT_RETURN)).size)
        assertEquals(1_400_000.0, s.totals(s.docs).first, 0.0)
        assertEquals(listOf("ПродажаРозница: yo'q"), s.errors)
        // Yozish -> o'qish
        val back = OneCCodec.parseSales(OneCCodec.salesJson(s).toByteArray())
        assertEquals(s.docs, back.docs)
    }

    @Test fun suppliersCodec() {
        val doc = OneCDoc("d1", OneCDoc.RECEIPT, "5", 20261020100000L, "Ali", "3", 2_000_000.0, 0.0, "", listOf(OneCLine("Mayiz", "77", 10.0, 200_000.0, 2_000_000.0)))
        val s = Suppliers(20261001000000L, 20261031235959L, 9L, listOf(
            Supplier("a", "3", "Ali", "+998", -500_000.0, 0.0, listOf(doc)),
            Supplier("b", "4", "Vali", "", 0.0, 0.0, emptyList()),
        ))
        val back = OneCCodec.parseSuppliers(OneCCodec.suppliersJson(s).toByteArray())
        assertEquals(2, back.list.size)
        assertEquals("Ali", back.list[0].name)
        assertEquals(500_000.0, back.list[0].oweSom, 0.0)
        assertEquals(doc, back.list[0].docs[0])
    }

    @Test fun writeCodec() {
        val r = WriteRequest(
            WriteCodec.newId(1_700_000_000_000L), WriteKind.RECEIPT, 1_700_000_000_000L, "Ali", "3", "kirim",
            listOf(WriteLine("Mayiz", "77", 10.0, 20_000.0, 0.0), WriteLine("Pista", "", 2.5, 0.0, 9.5)),
        )
        assertNull(r.problem())
        assertEquals(200_000.0, r.totalSom, 0.0)
        assertEquals(23.75, r.totalUsd, 1e-9)
        val cloud = WriteCodec.toCloud(r, "Admin").mapValues { (k, v) -> if (k == "at") 1L else v }
        val back = WriteCodec.fromCloud(r.id, cloud)
        assertEquals(r, back)
        // Tekshiruvlar
        assertEquals("party", r.copy(party = "").problem())
        assertEquals("lines", r.copy(lines = emptyList()).problem())
        assertEquals("line", r.copy(lines = listOf(WriteLine("A", "", 0.0, 1.0, 0.0))).problem())
        val pay = WriteRequest("w1", WriteKind.SUPPLIER_PAY, 1L, "Ali", cashSom = 0.0)
        assertEquals("amount", pay.problem())
        assertNull(pay.copy(cardUsd = 10.0).problem())
        assertEquals(10.0, pay.copy(cardUsd = 10.0).totalUsd, 0.0)
        val bc = WriteRequest("w2", WriteKind.BARCODE, 1L, "Pista", "1001", barcode = "4780000000017")
        assertNull(bc.problem())
        assertEquals("barcode", bc.copy(barcode = "12 3").problem())
        assertEquals("target", WriteRequest("w3", WriteKind.UNDO, 1L).problem())
        assertNull(WriteCodec.fromCloud("x", mapOf("k" to "zz", "p" to "{}")))
        assertNull(WriteCodec.fromCloud("x", mapOf("k" to "in", "p" to "buzuq")))
        val res = WriteCodec.resultFromCloud("w1", mapOf("ok" to true, "no" to "000123", "ref" to "abc", "at" to 5L))
        assertNotNull(res)
        assertEquals("done", res?.state)
        assertEquals("err", WriteCodec.resultFromCloud("w1", mapOf("ok" to false, "e" to "x"))?.state)
    }

    @Test fun clientRequestsAndRules() {
        val fake = FakeRtdb(secret)
        val rk = CloudDb.newKey()
        val client = CloudDb("https://x.firebaseio.com", "", fake.http())
        val admin = CloudDb("https://x.firebaseio.com", secret, fake.http())
        val pid = ClientReqCodec.newId('p')
        client.put("s/$rk/pay/$pid", ClientReqCodec.payToCloud(150_000.0, 0.0, "n", "naqd berdim", "Ali"))
        try { client.put("s/$rk/pay/$pid", ClientReqCodec.payToCloud(1.0, 0.0, "n", "", "Ali")); fail("qayta yozildi") } catch (e: Exception) { assertTrue(e.message.orEmpty().isNotEmpty()) }
        try { client.put("s/$rk/pay/$pid/st", ClientReqCodec.statusToCloud(ReqStatus(ReqStatus.OK))); fail("mijoz holat yozdi") } catch (e: Exception) { assertTrue(e.message.orEmpty().isNotEmpty()) }
        admin.put("s/$rk/pay/$pid/st", ClientReqCodec.statusToCloud(ReqStatus(ReqStatus.OK, serverId = "S-77")))
        val list = ClientReqCodec.all(client.get("s/$rk/pay")) { k, v -> ClientReqCodec.payFromCloud(k, v) }
        assertEquals(1, list.size)
        assertEquals(150_000.0, list[0].som, 0.0)
        assertEquals(ReqStatus.OK, list[0].status.state)
        assertEquals("S-77", list[0].status.serverId)
        assertTrue(list[0].status.isFinal)
        // Chek tuzatish
        val eid = ClientReqCodec.newId('e')
        client.put("s/$rk/ed/$eid", ClientReqCodec.editToCloud("20261029161619|S|Savdo", listOf(EditLine("Pista", 39.0, 182_000.0, 0.0)), "1 ta kam keldi", "Ali"))
        val ed = ClientReqCodec.all(client.get("s/$rk/ed")) { k, v -> ClientReqCodec.editFromCloud(k, v) }
        assertEquals(1, ed.size)
        assertEquals(39.0, ed[0].lines[0].qty, 0.0)
        assertEquals("20261029161619|S|Savdo", ed[0].doc)
        // Manfiy summa — rad
        try { client.put("s/$rk/pay/${ClientReqCodec.newId('p')}", ClientReqCodec.payToCloud(-5.0, 0.0, "n", "", "")); fail("manfiy") } catch (e: Exception) { assertTrue(e.message.orEmpty().isNotEmpty()) }
        assertNull(ClientReqCodec.payFromCloud("p", mapOf("s" to 0.0, "u" to 0.0)))
        assertTrue(CloudDb.RULES_VERSION >= 5)
        assertNotNull(Json.parse(CloudDb.RULES))
    }

    @Test fun posLogic() {
        val a = uz.imsoft.shared.pos.PosLine("Pista", "1", 2.0, 150_000.0)
        val b = uz.imsoft.shared.pos.PosLine("Bodom", "", 0.5, 99_999.0)
        val s1 = uz.imsoft.shared.pos.PosSale(1, 20261101100000L, 7, listOf(a, b), discount = 10_000.0, paid = 200_000.0)
        assertEquals(339_999.5, s1.total, 1e-9)
        assertEquals(139_999.5, s1.debt, 1e-9)
        val s2 = uz.imsoft.shared.pos.PosSale(2, 20261101120000L, 0, listOf(a), 0.0, 300_000.0)
        val s3 = uz.imsoft.shared.pos.PosSale(3, 20261020120000L, 7, listOf(a), 0.0, 0.0)
        val p1 = uz.imsoft.shared.pos.PosPayment(1, 20261101130000L, 7, 100_000.0)
        assertEquals(339_999.5, uz.imsoft.shared.pos.Pos.balance(7, listOf(s1, s2, s3), listOf(p1)), 1e-9)
        assertEquals(0.0, uz.imsoft.shared.pos.Pos.balance(0, listOf(s2), emptyList()), 0.0)
        val (from, to) = uz.imsoft.shared.pos.Pos.range(20261101150000L, 1)
        assertEquals(20261101000000L, from)
        assertEquals(20261101235959L, to)
        val r = uz.imsoft.shared.pos.Pos.report(from, to, listOf(s1, s2, s3), listOf(p1))
        assertEquals(2, r.sales)
        assertEquals(639_999.5, r.total, 1e-9)
        assertEquals(500_000.0, r.paidAtSale, 1e-9)
        assertEquals(139_999.5, r.onCredit, 1e-9)
        assertEquals(600_000.0, r.cashIn, 1e-9)
        assertEquals("Pista", r.top[0].first)
        assertEquals(20261020000000L, uz.imsoft.shared.pos.Pos.range(20261101150000L, 13).first)
        val labels = uz.imsoft.shared.pos.Pos.Labels("Savdo", "Mijoz", "Jami", "Chegirma", "To'lanadi", "To'landi", "Qarz", "Chek №", "Vaqt")
        val rc = uz.imsoft.shared.pos.Pos.receipt("ZAFAR", s1, uz.imsoft.shared.pos.PosCustomer(7, "Ali"), 139_999.5, labels)
        assertEquals("ZAFAR", rc.header)
        assertEquals(2, rc.lines.size)
        assertEquals("2 x 150 000", rc.lines[0].detail)
        assertTrue(rc.totals.any { it.bold && it.value == "339 999,5" })
        assertEquals("Ali", rc.clientName)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear(); cal.set(2026, 10, 1, 9, 5, 7)
        assertEquals(20261101090507L, uz.imsoft.shared.pos.Pos.stampOf(cal.timeInMillis, Calendar.getInstance(TimeZone.getTimeZone("UTC"))))
    }
}
