package com.ilxomjon.dur_novvot_dokon.data.photo

import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json

/**
 * v5.3.8: yuk xati / chek / qo'lda yozilgan ro'yxat rasmini o'qish — so'rov matni va javobni tahlil qilish (Android'siz,
 * testlanadi). Rasmni sun'iy intellekt (Anthropic Claude, rasm o'qiydi) o'qiydi; tovarni tanlash — [ReceiptMatch] (telefonda).
 */
object ReceiptScan {
    const val DEFAULT_MODEL = "claude-sonnet-5-5"
    const val API_URL = "https://api.anthropic.com/v1/messages"
    const val API_VERSION = "2023-06-01"
    /** Google Gemini — BEPUL kalit (aistudio.google.com), rasm o'qiydi (kirill, qo'lyozma ham). */
    const val GEMINI_MODEL = "gemini-2.5-flash"
    const val GEMINI_FALLBACK = "gemini-3.1-flash-lite"
    const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    /** Kalit turiga qarab: Anthropic — "sk-ant-…"; qolgani Google Gemini ("AQ.…" — 2026-yildan yangi kalitlar, "AIza…" — eskisi). */
    fun isGemini(key: String): Boolean = key.isNotBlank() && !key.trim().startsWith("sk-ant-")

    /** Saqlangan model shu xizmatniki bo'lmasa — standart. */
    fun modelFor(key: String, saved: String): String {
        val g = isGemini(key)
        val m = saved.trim()
        return when {
            m.isEmpty() -> if (g) GEMINI_MODEL else DEFAULT_MODEL
            g && !m.startsWith("gemini") -> GEMINI_MODEL
            !g && !m.startsWith("claude") -> DEFAULT_MODEL
            else -> m
        }
    }

    /** Gemini generateContent so'rovi (javob — faqat JSON). */
    fun geminiRequest(imageB64: String): String = Json.write(
        mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("inline_data" to mapOf("mime_type" to "image/jpeg", "data" to imageB64)),
                        mapOf("text" to PROMPT),
                    ),
                ),
            ),
            "generationConfig" to mapOf("responseMimeType" to "application/json", "temperature" to 0),
        ),
    )

    /** Gemini javobi -> matn. Xato bo'lsa — IllegalStateException(xabar). */
    fun geminiReplyText(body: String): String {
        val o = JObj.parse(body)
        o.obj("error")?.let { throw IllegalStateException(it.str("message").ifEmpty { it.str("status") }) }
        val c = o.objs("candidates").firstOrNull() ?: throw IllegalStateException(o.obj("promptFeedback")?.str("blockReason")?.ifEmpty { null } ?: "javob bo'sh")
        return c.obj("content")?.objs("parts").orEmpty().joinToString("") { it.str("text") }
    }

    /** Rasmdagi bitta tovar qatori. [y0]/[y1] — qator tepasi/pasti, to'g'rilangan rasm bo'yining ulushi (0..1). */
    data class Row(
        val text: String,
        val qty: Double,
        val unit: String,
        val price: Double,
        val sum: Double,
        val y0: Double,
        val y1: Double,
        val crossed: Boolean = false,
    )

    data class Result(
        /** Rasmni tik qilish uchun soat yo'nalishida burish (0/90/180/270). */
        val rotate: Int,
        val supplier: String,
        val date: String,
        val usd: Boolean,
        val total: Double,
        val rows: List<Row>,
        val note: String = "",
    )

    val PROMPT: String = """
Siz O'zbekistondagi quruq meva / oziq-ovqat do'koni uchun YUK XATI, CHEK, NAKLADNAYA yoki QO'LDA YOZILGAN ro'yxat rasmini o'qiysiz.
Matn o'zbek yoki rus tilida, kirill yoki lotin, bosma yoki qo'lyozma, rasm qiyshiq/teskari/xira bo'lishi mumkin.
FAQAT bitta JSON obyekt qaytaring (izohsiz, ``` siz):
{"rotate":0,"supplier":"","date":"","currency":"som","total":0,"rows":[{"text":"","qty":0,"unit":"","price":0,"sum":0,"y0":0,"y1":0,"crossed":false}],"note":""}
Qoidalar:
- rotate: matn tik o'qilishi uchun rasmni soat yo'nalishida necha gradus burish kerak (0, 90, 180, 270).
- supplier: kim yuk berdi (firma, agent yoki yuk beruvchi nomi, sarlavhadan); bilinmasa "". Mijoz (do'kon) nomini emas!
- date: hujjat sanasi dd.mm.yyyy; bo'lmasa "".
- currency: "usd" agar narxlar dollarda ($), aks holda "som".
- total: hujjatdagi umumiy summa (jami / итог / к оплате); bo'lmasa 0.
- rows: HAR BIR tovar qatori, yuqoridan pastga; sarlavha, jami, qarz qatorlarini kiritmang.
  text — tovar nomi rasmdagidek (o'zgartirmasdan, imlo xatosi bilan bo'lsa ham);
  qty — narx qo'llanadigan dona/kg soni: "48 * 45 000" -> qty 48, price 45000; "1бл 12 x 38 200 = 458 400" -> qty 12;
  qty*price summaga teng bo'lmasa va sum/price butun son bo'lsa -> qty = sum/price (blok -> dona);
  unit — "dona", "kg", "blok" yoki rasmdagi birlik; price — 1 birlik narxi; sum — qator summasi (yo'q bo'lsa qty*price);
  "12867к", "12 867", "12.867" -> 12867; "1 456 530" -> 1456530; "61 150,74" -> 61150.74;
  y0, y1 — shu qatorning rasm (tik holatda) bo'yidagi tepasi va pasti, 0..1 ulushda;
  crossed — qator ustidan chizilgan (o'chirilgan) bo'lsa true.
- note: o'qishda shubhali joylar (qisqa), bo'lmasa "".
""".trimIndent()

    /** Messages API so'rovi (rasm base64 JPEG). */
    fun request(imageB64: String, model: String = DEFAULT_MODEL): String = Json.write(
        mapOf(
            "model" to model.ifBlank { DEFAULT_MODEL },
            "max_tokens" to 8000,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "image", "source" to mapOf("type" to "base64", "media_type" to "image/jpeg", "data" to imageB64)),
                        mapOf("type" to "text", "text" to PROMPT),
                    ),
                ),
            ),
        ),
    )

    /** API javobi (to'liq JSON) -> modelning matni. Xato bo'lsa — IllegalStateException(xabar). */
    fun replyText(body: String): String {
        val o = JObj.parse(body)
        o.obj("error")?.let { throw IllegalStateException(it.str("message").ifEmpty { it.str("type") }) }
        return o.objs("content").filter { it.str("type") == "text" }.joinToString("") { it.str("text") }
    }

    /** Model matni -> natija. ``` va oldidagi/keyingi so'zlar e'tiborsiz. */
    fun parse(text: String): Result {
        val a = text.indexOf('{')
        val b = text.lastIndexOf('}')
        if (a < 0 || b <= a) throw IllegalStateException("javobda JSON yo'q")
        val o = JObj.parse(text.substring(a, b + 1))
        val rows = o.objs("rows").mapNotNull { r ->
            val name = r.str("text").trim()
            var qty = r.num("qty")
            val price = r.num("price")
            var sum = r.num("sum")
            if (name.isEmpty() && qty <= 0 && price <= 0) return@mapNotNull null
            if (qty <= 0 && price > 0 && sum > 0) qty = Math.round(sum / price * 1000.0) / 1000.0
            if (sum <= 0 && qty > 0 && price > 0) sum = Math.round(qty * price * 100.0) / 100.0
            var y0 = r.num("y0").coerceIn(0.0, 1.0)
            var y1 = r.num("y1").coerceIn(0.0, 1.0)
            if (y1 < y0) { val t = y0; y0 = y1; y1 = t }
            Row(name, qty, r.str("unit").trim(), price, sum, y0, y1, r.bool("crossed"))
        }
        val rot = ((o.num("rotate").toInt() % 360 + 360) % 360).let { if (it % 90 == 0) it else 0 }
        return Result(rot, o.str("supplier").trim(), o.str("date").trim(), o.str("currency").equals("usd", true), o.num("total"),
            rows, o.str("note").trim())
    }

    /** qty*price summa bilan mos kelmaydimi (1% dan ortiq)? */
    fun sumMismatch(r: Row): Boolean = r.qty > 0 && r.price > 0 && r.sum > 0 && Math.abs(r.qty * r.price - r.sum) > Math.max(1.0, r.sum * 0.01)
}
