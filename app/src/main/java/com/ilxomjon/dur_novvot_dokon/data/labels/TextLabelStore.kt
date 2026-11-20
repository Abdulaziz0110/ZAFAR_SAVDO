package com.ilxomjon.dur_novvot_dokon.data.labels

import android.content.ContentValues
import com.ilxomjon.dur_novvot_dokon.data.local.LocalDb
import uz.imsoft.shared.label.TextLabels

/** Matnli etiketka tarixi: matn, nusxa soni, yaratilgan va oxirgi marta chiqarilgan vaqt, necha marta chiqarilgani. */
data class TextLabel(val id: Long, val text: String, val copies: Int, val created: Long, val printedAt: Long, val prints: Int)

/** v5.3.3: Sozlamalar -> Yorliqlar -> Matnli etiketka. Hamma amal fon oqimida chaqirilishi kerak. */
class TextLabelStore(private val db: LocalDb) {
    fun all(): List<TextLabel> =
        db.readableDatabase.rawQuery(
            "SELECT id, text, copies, created, printed_at, prints FROM text_label " +
                "ORDER BY CASE WHEN printed_at > 0 THEN printed_at ELSE created END DESC, id DESC LIMIT 200",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(TextLabel(c.getLong(0), c.getString(1).orEmpty(), c.getInt(2), c.getLong(3), c.getLong(4), c.getInt(5)))
            }
        }

    /** id > 0 va shunday yozuv bor bo'lsa — yangilaydi; aks holda yangisini qo'shadi. Yozuv id'sini qaytaradi. */
    fun save(id: Long, text: String, copies: Int): Long {
        val t = TextLabels.clean(text)
        val n = TextLabels.copies(copies)
        val w = db.writableDatabase
        if (id > 0) {
            val v = ContentValues().apply { put("text", t); put("copies", n) }
            if (w.update("text_label", v, "id = ?", arrayOf(id.toString())) > 0) return id
        }
        val v = ContentValues().apply {
            put("text", t); put("copies", n); put("created", System.currentTimeMillis()); put("printed_at", 0L); put("prints", 0)
        }
        val rid = w.insert("text_label", null, v)
        check(rid > 0) { "text_label: yozib bo'lmadi" }
        return rid
    }

    fun markPrinted(id: Long) {
        db.writableDatabase.execSQL(
            "UPDATE text_label SET printed_at = ?, prints = prints + 1 WHERE id = ?",
            arrayOf<Any>(System.currentTimeMillis(), id),
        )
    }

    fun delete(id: Long) {
        db.writableDatabase.delete("text_label", "id = ?", arrayOf(id.toString()))
    }
}
