package com.ilxomjon.dur_novvot_dokon.data.local

import android.content.ContentValues
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.domain.Agent
import com.ilxomjon.dur_novvot_dokon.domain.Group
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.domain.Store

/** Serverdan kelgan to'liq ma'lumot nusxasi (bitta tranzaksiyada almashtiriladi). */
class CatalogSnapshot(
    val groups: List<Group>,
    val regions: List<Pair<String, String>>,
    val stores: List<RawStore>,
    val rates: List<String>,
    val products: List<RawProduct>,
    val agent: RawAgent?,
)

class RawStore(val regionId: String, val id: String, val name: String, val debtSom: String, val debtVal: String, val time: String)

class RawProduct(
    val groupId: String, val id: String, val name: String, val currencyFlag: String,
    val wholesaleSom: String, val wholesaleVal: String, val blockSize: String, val stock: String, val time: String,
    val cashSom: String, val cashVal: String, val costSom: String, val costVal: String,
)

class RawAgent(val id: String, val fullName: String, val boss: String, val phone: String)

class CatalogDao(private val db: LocalDb) {

    fun products(): List<Product> {
        val sql = "SELECT gruppa_id, tovar_id, nomi, valyuta_turi, sotish_narxi_chakana_som, sotish_narxi_chakana_val, " +
            "sotish_narxi_optom_som, sotish_narxi_optom_val, kirim_narxi_som, kirim_narxi_val, blokdagi_soni FROM tovar"
        val out = ArrayList<Product>()
        db.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out += Product(
                    groupId = c.getString(0).orEmpty(),
                    id = c.getString(1).orEmpty(),
                    name = c.getString(2).orEmpty(),
                    baseIsSom = c.getString(3).orEmpty().trim() == "1",
                    cashSom = Money.parse(c.getString(4)),
                    cashUsd = Money.parse(c.getString(5)),
                    wholesaleSom = Money.parse(c.getString(6)),
                    wholesaleUsd = Money.parse(c.getString(7)),
                    costSom = Money.parse(c.getString(8)),
                    costUsd = Money.parse(c.getString(9)),
                    blockSize = Money.parse(c.getString(10)).let { if (it <= 0.0) 1.0 else it },
                )
            }
        }
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return out
    }

    fun groups(): List<Group> {
        val out = ArrayList<Group>()
        db.readableDatabase.rawQuery("SELECT gruppa_id, gruppa_nomi FROM gruppa", null).use { c ->
            while (c.moveToNext()) out += Group(c.getString(0).orEmpty(), c.getString(1).orEmpty())
        }
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return out
    }

    fun stores(): List<Store> {
        val sql = "SELECT kon.region_id, kon.dokon_id, kon.nomi, reg.nomi, kon.qarzi_som, kon.qarzi_val " +
            "FROM dokon AS kon LEFT JOIN region AS reg ON kon.region_id = reg.region_id"
        val out = ArrayList<Store>()
        db.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out += Store(
                    id = c.getString(1).orEmpty(),
                    regionId = c.getString(0).orEmpty(),
                    name = c.getString(2).orEmpty(),
                    regionName = c.getString(3).orEmpty(),
                    debtSom = Money.parse(c.getString(4)),
                    debtUsd = Money.parse(c.getString(5)),
                )
            }
        }
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return out
    }

    fun storeById(id: String): Store? = stores().firstOrNull { it.id == id }

    /** Oxirgi yozuv joriy kurs hisoblanadi (avvalgi ilova bilan bir xil). */
    fun rate(): Double {
        var last = ""
        db.readableDatabase.rawQuery("SELECT kurs FROM kurs", null).use { c ->
            while (c.moveToNext()) last = c.getString(0).orEmpty()
        }
        return Money.parse(last)
    }

    fun rateText(): String {
        var last = ""
        db.readableDatabase.rawQuery("SELECT kurs FROM kurs", null).use { c ->
            while (c.moveToNext()) last = c.getString(0).orEmpty()
        }
        return last.trim()
    }

    fun agent(): Agent? {
        db.readableDatabase.rawQuery("SELECT agent_id, fio, telefon FROM agent", null).use { c ->
            var a: Agent? = null
            while (c.moveToNext()) a = Agent(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty())
            return a?.takeIf { it.id.isNotBlank() }
        }
    }

    fun hasProducts(): Boolean =
        db.readableDatabase.rawQuery("SELECT 1 FROM tovar LIMIT 1", null).use { it.moveToFirst() }

    fun updateDebt(storeId: String, som: String, usd: String) {
        val cv = ContentValues().apply { put("qarzi_som", som.trim()); put("qarzi_val", usd.trim()) }
        db.writableDatabase.update("dokon", cv, "dokon_id = ?", arrayOf(storeId))
    }

    fun clearAgent() {
        db.transaction { d -> d.execSQL("DELETE FROM agent"); d.execSQL("DELETE FROM sqlite_sequence WHERE name='agent'") }
    }

    /** Agent qatorini yozadi. Parol saqlanmaydi (avvalgi ilova uni ochiq matnda saqlagan edi). */
    fun saveAgent(a: RawAgent) {
        db.transaction { d -> writeAgent(d, a) }
    }

    private fun writeAgent(d: android.database.sqlite.SQLiteDatabase, a: RawAgent) {
        d.execSQL("DELETE FROM agent")
        d.execSQL("DELETE FROM sqlite_sequence WHERE name='agent'")
        val cv = ContentValues().apply {
            put("agent_id", a.id.trim()); put("fio", a.fullName.trim()); put("boshagan", a.boss.trim())
            put("telefon", a.phone.trim()); put("parol", ""); put("zakaz_id", "1"); put("zakaz_letter", "A")
        }
        d.insert("agent", null, cv)
    }

    /** Barcha jadvallarni bitta tranzaksiyada almashtiradi: xato bo'lsa eski ma'lumot saqlanib qoladi. */
    fun replaceAll(s: CatalogSnapshot) {
        db.transaction { d ->
            for (t in listOf("gruppa", "region", "dokon", "kurs", "tovar")) {
                d.execSQL("DELETE FROM $t")
                d.execSQL("DELETE FROM sqlite_sequence WHERE name='$t'")
            }
            for (g in s.groups) d.insert("gruppa", null, ContentValues().apply {
                put("gruppa_id", g.id.trim()); put("gruppa_nomi", g.name.trim())
            })
            for ((id, name) in s.regions) d.insert("region", null, ContentValues().apply {
                put("region_id", id.trim()); put("nomi", name.trim())
            })
            for (st in s.stores) d.insert("dokon", null, ContentValues().apply {
                put("region_id", st.regionId.trim()); put("dokon_id", st.id.trim()); put("nomi", st.name.trim())
                put("qarzi_som", st.debtSom.trim()); put("qarzi_val", st.debtVal.trim()); put("vaqti", st.time.trim())
            })
            for (r in s.rates) d.insert("kurs", null, ContentValues().apply { put("kurs", r.trim()) })
            for (p in s.products) d.insert("tovar", null, ContentValues().apply {
                put("gruppa_id", p.groupId.trim()); put("tovar_id", p.id.trim()); put("nomi", p.name.trim())
                put("valyuta_turi", compact(p.currencyFlag))
                put("sotish_narxi_optom_som", number(p.wholesaleSom)); put("sotish_narxi_optom_val", number(p.wholesaleVal))
                put("blokdagi_soni", compact(p.blockSize.replace(',', '.')))
                put("qoldiq", compact(p.stock).ifEmpty { "0" }); put("time", compact(p.time))
                put("sotish_narxi_chakana_som", number(p.cashSom)); put("sotish_narxi_chakana_val", number(p.cashVal))
                put("kirim_narxi_som", number(p.costSom)); put("kirim_narxi_val", number(p.costVal))
            })
            s.agent?.let { writeAgent(d, it) }
        }
    }

    private fun compact(v: String) = v.filter { !it.isWhitespace() }
    private fun number(v: String) = if (v.isBlank()) "0" else compact(v).replace(',', '.')
}
