package uz.imsoft.shared

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Bepul bulut: Google Firebase Realtime Database'ning REST API'si (kutubxonasiz, oddiy HTTPS).
 *
 * Tuzilma (ikki ilova va kompyuter o'rtasida):
 *   /s/{readKey}/meta  — 2-ilova uchun ma'lumot sarlavhasi (vaqt, hajm)          [1-ilova yozadi, 2-ilova o'qiydi]
 *   /s/{readKey}/blob  — siqilgan ma'lumot (narxlar, xaridlar, to'lovlar, qarz)     [1-ilova yozadi, 2-ilova o'qiydi]
 *   /s/{readKey}/req   — 2-ilova "yangilab ber" so'rovi vaqti                        [2-ilova yozadi, 1-ilova o'qiydi]
 *   /q/{queueKey}/{id} — chek navbati (xom ESC/POS baytlar, base64)                  [kompyuter yozadi, 2-ilova o'qib o'chiradi]
 *   /p/{queueKey}/...  — printer holati va "yangi chek bor" signali                  [ikkalasi]
 *
 * Kalitlar (readKey/queueKey) 32 belgili tasodifiy satr: ularni bilmagan odam hech narsani o'qiy olmaydi
 * (qoidalar ildizni ro'yxatlashni taqiqlaydi). Yozish huquqi faqat "secret" bilan (1-ilova va kompyuter).
 */
class CloudDb(
    baseUrl: String,
    /** Firebase "Database secret". 2-ilovada bo'sh (faqat qoidalar ruxsat bergan joylar). */
    private val secret: String = "",
    private val http: Http = UrlHttp(),
) {
    val baseUrl: String = normalizeUrl(baseUrl)

    class Response(val code: Int, val body: String)

    /** HTTP qatlami (testlarda soxtasi qo'yiladi). */
    interface Http {
        fun request(method: String, url: String, body: String?, timeoutMs: Int): Response
    }

    class CloudException(val code: Int, message: String) : IOException(message) {
        /** Ruxsat yo'q (kalit yoki secret noto'g'ri, qoidalar o'rnatilmagan). */
        val denied: Boolean get() = code == 401 || code == 403
    }

    fun url(path: String, query: Map<String, String> = emptyMap()): String {
        val clean = path.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { seg ->
            require(seg == ".settings" || seg.none { it in ".#$[]" }) { "Noto'g'ri yo'l: $seg" }
            URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
        val q = LinkedHashMap(query)
        if (secret.isNotEmpty()) q["auth"] = secret
        val qs = if (q.isEmpty()) "" else "?" + q.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        return "$baseUrl/$clean.json$qs"
    }

    private fun call(method: String, path: String, body: String? = null, query: Map<String, String> = emptyMap(), timeoutMs: Int = 20_000): String {
        val r = http.request(method, url(path, query), body, timeoutMs)
        if (r.code !in 200..299) throw CloudException(r.code, errorText(r))
        return r.body
    }

    private fun errorText(r: Response): String {
        val msg = runCatching { JObj.parse(r.body).str("error") }.getOrNull().orEmpty()
        return "HTTP ${r.code}" + if (msg.isNotEmpty()) ": $msg" else ""
    }

    /** O'qish. Yo'q bo'lsa null. */
    fun get(path: String, query: Map<String, String> = emptyMap(), timeoutMs: Int = 20_000): Any? =
        Json.parse(call("GET", path, null, query, timeoutMs).ifBlank { "null" })

    fun getRaw(path: String, query: Map<String, String> = emptyMap(), timeoutMs: Int = 30_000): String =
        call("GET", path, null, query, timeoutMs)

    /** Faqat kalitlar (bolalarning qiymatisiz) — arzon. */
    @Suppress("UNCHECKED_CAST")
    fun keys(path: String): List<String> =
        ((get(path, mapOf("shallow" to "true")) as? Map<String, Any?>)?.keys?.toList() ?: emptyList()).sorted()

    /** Idempotent yozish (qayta yuborilsa ham bitta yozuv). */
    fun put(path: String, value: Any?, timeoutMs: Int = 30_000) {
        call("PUT", path, Json.write(value), mapOf("print" to "silent"), timeoutMs)
    }

    fun putRaw(path: String, json: String, timeoutMs: Int = 60_000) {
        call("PUT", path, json, mapOf("print" to "silent"), timeoutMs)
    }

    /** Bir nechta joyni bitta atomik so'rovda yozish (masalan meta + blob birga). */
    fun update(path: String, values: Map<String, Any?>, timeoutMs: Int = 60_000) {
        // HttpURLConnection PATCH'ni bilmaydi: POST + x-http-method-override (sarlavha va parametr — Firebase ikkalasini ham qabul qiladi)
        call("PATCH", path, Json.write(values), mapOf("print" to "silent", "x-http-method-override" to "PATCH"), timeoutMs)
    }

    fun delete(path: String) {
        call("DELETE", path, null, emptyMap(), 20_000)
    }

    /** Qoidalarni o'rnatish (faqat secret bilan). */
    fun setRules(rulesJson: String) {
        call("PUT", ".settings/rules", rulesJson, emptyMap(), 30_000)
    }

    companion object {
        /** Server vaqti belgisi (Firebase o'zi qo'yadi — telefon soati noto'g'ri bo'lsa ham). */
        val SERVER_TIME: Map<String, Any?> = mapOf(".sv" to "timestamp")

        /**
         * Qoidalar versiyasi: oshsa 1-ilova ularni o'zi qayta o'rnatadi (v2: 2-ilovadan buyurtma, v3: telefon+parol bilan kirish,
         * v4: agentlar "Admin orqali" — g/{kalit}/..., v5: 2-ilovadan to'lov va chek tuzatish so'rovlari — s/{kalit}/pay, ed,
         * v6: agentlar Admin'ning "1C tarixi"ni o'qiydi — g/{kalit}/hist).
         */
        const val RULES_VERSION = 6

        /** Ikki ilova uchun xavfsizlik qoidalari: ildizni hech kim o'qiy/yozolmaydi, faqat kalitni bilgan o'z joyini. */
        const val RULES = """{
  "rules": {
    ".read": false,
    ".write": false,
    "s": {
      "${'$'}rk": {
        ".read": "${'$'}rk.length >= 24",
        "req": { ".write": "${'$'}rk.length >= 24", ".validate": "newData.isNumber()" },
        "ord": {
          "${'$'}oid": {
            ".write": "${'$'}rk.length >= 24 && !data.exists() && newData.exists()",
            ".validate": "${'$'}oid.length >= 10 && ${'$'}oid.length <= 40 && newData.hasChildren(['at', 'l'])",
            "at": { ".validate": "newData.val() == now" },
            "v": { ".validate": "newData.isNumber()" },
            "src": { ".validate": "newData.isString() && newData.val().length <= 200" },
            "n": { ".validate": "newData.isString() && newData.val().length <= 300" },
            "by": { ".validate": "newData.isString() && newData.val().length <= 80" },
            "l": { ".validate": "newData.isString() && newData.val().length <= 30000" },
            "${'$'}other": { ".validate": false }
          }
        },
        "pay": {
          "${'$'}pid": {
            ".write": "${'$'}rk.length >= 24 && !data.exists() && newData.exists()",
            ".validate": "${'$'}pid.length >= 10 && ${'$'}pid.length <= 40 && newData.hasChildren(['at', 's', 'u'])",
            "at": { ".validate": "newData.val() == now" },
            "by": { ".validate": "newData.isString() && newData.val().length <= 80" },
            "s": { ".validate": "newData.isNumber() && newData.val() >= 0" },
            "u": { ".validate": "newData.isNumber() && newData.val() >= 0" },
            "m": { ".validate": "newData.isString() && newData.val().length <= 2" },
            "n": { ".validate": "newData.isString() && newData.val().length <= 300" },
            "${'$'}other": { ".validate": false }
          }
        },
        "ed": {
          "${'$'}eid": {
            ".write": "${'$'}rk.length >= 24 && !data.exists() && newData.exists()",
            ".validate": "${'$'}eid.length >= 10 && ${'$'}eid.length <= 40 && newData.hasChildren(['at', 'd', 'l'])",
            "at": { ".validate": "newData.val() == now" },
            "by": { ".validate": "newData.isString() && newData.val().length <= 80" },
            "d": { ".validate": "newData.isString() && newData.val().length <= 200" },
            "n": { ".validate": "newData.isString() && newData.val().length <= 300" },
            "l": { ".validate": "newData.isString() && newData.val().length <= 30000" },
            "${'$'}other": { ".validate": false }
          }
        }
      }
    },
    "a": {
      "${'$'}h": { ".read": "${'$'}h.length >= 40" }
    },
    "q": {
      "${'$'}qk": {
        ".read": "${'$'}qk.length >= 24",
        "${'$'}id": { ".write": "${'$'}qk.length >= 24 && !newData.exists()" }
      }
    },
    "p": {
      "${'$'}qk": { ".read": "${'$'}qk.length >= 24", ".write": "${'$'}qk.length >= 24" }
    },
    "g": {
      "${'$'}gk": {
        "cat": { ".read": "${'$'}gk.length >= 24" },
        "codes": { ".read": "${'$'}gk.length >= 24" },
        "hist": { ".read": "${'$'}gk.length >= 24" },
        "hb": { ".read": "${'$'}gk.length >= 24" },
        "sig": { ".write": "${'$'}gk.length >= 24", ".validate": "newData.isNumber()" },
        "creq": { ".write": "${'$'}gk.length >= 24", ".validate": "newData.isNumber()" },
        "rq": {
          "${'$'}id": {
            ".write": "${'$'}gk.length >= 24 && !data.exists() && newData.exists()",
            ".validate": "${'$'}id.length >= 24 && ${'$'}id.length <= 64 && newData.hasChildren(['at', 'op', 'p'])",
            "at": { ".validate": "newData.val() == now" },
            "op": { ".validate": "newData.isString() && newData.val().length <= 20" },
            "p": { ".validate": "newData.isString() && newData.val().length <= 4000" },
            "${'$'}other": { ".validate": false }
          }
        },
        "rs": {
          "${'$'}id": { ".read": "${'$'}gk.length >= 24 && ${'$'}id.length >= 24", ".write": "${'$'}gk.length >= 24 && !newData.exists()" }
        },
        "ob": {
          "${'$'}id": {
            ".write": "${'$'}gk.length >= 24 && !data.exists() && newData.exists()",
            ".validate": "${'$'}id.length == 40 && newData.hasChildren(['at', 'u', 't', 'p'])",
            "at": { ".validate": "newData.val() == now" },
            "u": { ".validate": "newData.isString() && newData.val().length <= 40" },
            "t": { ".validate": "newData.val() == '1' || newData.val() == '4'" },
            "p": { ".validate": "newData.isString() && newData.val().length <= 60000" },
            "m": { ".validate": "newData.isString() && newData.val().length <= 60000" },
            "${'$'}other": { ".validate": false }
          }
        },
        "obr": {
          "${'$'}id": { ".read": "${'$'}gk.length >= 24 && ${'$'}id.length == 40" }
        },
        "lb": {
          "${'$'}id": {
            ".write": "${'$'}gk.length >= 24 && !data.exists() && newData.exists()",
            ".validate": "${'$'}id.length >= 24 && newData.hasChildren(['at', 'n', 'b'])",
            "at": { ".validate": "newData.val() == now" },
            "n": { ".validate": "newData.isNumber()" },
            "b": { ".validate": "newData.isString() && newData.val().length <= 6000000" },
            "${'$'}other": { ".validate": false }
          }
        },
        "lbr": {
          "${'$'}id": { ".read": "${'$'}gk.length >= 24 && ${'$'}id.length >= 24" }
        }
      }
    }
  }
}"""

        private val ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()

        /** 32 belgili tasodifiy kalit (taxminan 186 bit). */
        fun newKey(random: SecureRandom = SecureRandom()): String =
            String(CharArray(32) { ALPHABET[random.nextInt(ALPHABET.size)] })

        fun isKey(s: String): Boolean = s.length in 24..64 && s.all { it.isLetterOrDigit() && it.code < 128 }

        /** "my-db.firebaseio.com", "https://x-default-rtdb.europe-west1.firebasedatabase.app/" -> toza manzil. */
        fun normalizeUrl(raw: String): String {
            var u = raw.trim().trimEnd('/')
            if (u.isEmpty()) return ""
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
            // Ba'zan konsoldan "/.json" yoki yo'l bilan nusxalanadi.
            val afterScheme = u.removePrefix("https://")
            val host = afterScheme.substringBefore('/')
            return "https://$host"
        }

        fun looksLikeFirebase(url: String): Boolean {
            val host = normalizeUrl(url).removePrefix("https://")
            return host.endsWith(".firebaseio.com") || host.endsWith(".firebasedatabase.app")
        }
    }
}

/** HttpURLConnection asosidagi haqiqiy HTTP. */
class UrlHttp : CloudDb.Http {
    override fun request(method: String, url: String, body: String?, timeoutMs: Int): CloudDb.Response {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = minOf(timeoutMs, 15_000)
            c.readTimeout = timeoutMs
            c.useCaches = false
            // HttpURLConnection PATCH'ni bilmaydi — Firebase X-HTTP-Method-Override'ni qabul qiladi.
            if (method == "PATCH") {
                c.requestMethod = "POST"
                c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                c.requestMethod = method
            }
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { s -> BufferedReader(InputStreamReader(s, Charsets.UTF_8)).readText() }.orEmpty()
            return CloudDb.Response(code, text)
        } finally {
            c.disconnect()
        }
    }
}
