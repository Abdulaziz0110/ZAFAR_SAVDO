package uz.imsoft.shared

/**
 * 2-ilovani ulash kodi. 1-ilova yaratadi, egasi uni Telegram/SMS orqali 2-ilova telefoniga yuboradi va
 * 2-ilovada "Kodni joylash" bosiladi. Ichida SECRET YO'Q — faqat baza manzili va o'qish kalitlari.
 *
 * Ko'rinishi: "IMSOFT-1:" + base64url(JSON)
 */
data class LinkCode(
    val dbUrl: String,
    val readKey: String,
    /** Chek navbati kaliti (bo'sh — chek printeri ishlatilmaydi). */
    val queueKey: String,
    /** Mijoz nomi (faqat ko'rsatish uchun). */
    val clientName: String,
) {
    fun encode(): String {
        val json = Json.write(linkedMapOf("u" to dbUrl, "r" to readKey, "q" to queueKey, "n" to clientName))
        return PREFIX + B64.encode(json.toByteArray(Charsets.UTF_8), urlSafe = true, pad = false)
    }

    companion object {
        const val PREFIX = "IMSOFT-1:"

        /** Matn ichidan kodni topadi (xabar bilan birga nusxalangan bo'lsa ham). Noto'g'ri bo'lsa null. */
        fun decode(text: String): LinkCode? {
            val start = text.indexOf(PREFIX)
            if (start < 0) return null
            val body = text.substring(start + PREFIX.length).takeWhile { (it.code < 128 && it.isLetterOrDigit()) || it == '-' || it == '_' }
            if (body.isEmpty()) return null
            return runCatching {
                val o = JObj.parse(String(B64.decode(body), Charsets.UTF_8))
                val code = LinkCode(CloudDb.normalizeUrl(o.str("u")), o.str("r"), o.str("q"), o.str("n"))
                if (!CloudDb.looksLikeFirebase(code.dbUrl) || !CloudDb.isKey(code.readKey)) return null
                if (code.queueKey.isNotEmpty() && !CloudDb.isKey(code.queueKey)) return null
                code
            }.getOrNull()
        }
    }
}
