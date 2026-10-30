package uz.imsoft.shared

import java.net.URI
import java.net.URLDecoder

/**
 * Testlar uchun Firebase Realtime Database REST'ning soddalashtirilgan nusxasi (haqiqiy Firebase EMAS):
 * GET (shallow), PUT, PATCH, DELETE, {".sv":"timestamp"}, print=silent, va CloudDb.RULES dagi qoidalar
 * qo'lda takrorlangan (secret bilan — hammasi ruxsat).
 */
class FakeRtdb(private val secret: String) : AutoCloseable {
    val root = LinkedHashMap<String, Any?>()
    var now = 1_700_000_000_000L
    var rulesSet: String? = null
    val requests = ArrayList<String>()

    /**
     * CloudDb uchun HTTP: so'rov tarmoqqa chiqmaydi, shu obyekt ichida bajariladi (Android unit testlarida
     * com.sun.net.httpserver yo'q). URL, metod, so'rov parametrlari va tana haqiqiy REST'dagidek tahlil qilinadi.
     */
    fun http(): CloudDb.Http = object : CloudDb.Http {
        override fun request(method: String, url: String, body: String?, timeoutMs: Int): CloudDb.Response {
            val uri = URI(url)
            val rawPath = (uri.rawPath ?: "").removeSuffix(".json").trim('/')
            val path = rawPath.split('/').filter { it.isNotEmpty() }.map { URLDecoder.decode(it, "UTF-8") }
            val q = (uri.rawQuery ?: "").split('&').filter { it.contains('=') }
                .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            val m = if (method == "PATCH" || q["x-http-method-override"] == "PATCH") "PATCH" else method
            synchronized(this@FakeRtdb) {
                requests += "$m /${path.joinToString("/")}"
                val (code, resp) = handle(m, path, q, body.orEmpty(), q["auth"] == secret)
                return CloudDb.Response(code, resp)
            }
        }
    }

    private fun canRead(p: List<String>): Boolean = when {
        p.isEmpty() -> false
        p[0] in setOf("s", "q", "p") && p.size >= 2 -> p[1].length >= 24
        p[0] == "a" && p.size >= 2 -> p[1].length >= 40
        else -> false
    }

    private fun canWrite(p: List<String>, newValue: Any?): Boolean = when {
        p.size == 3 && p[0] == "s" && p[2] == "req" -> p[1].length >= 24 && newValue is Number
        // CloudDb.RULES "ord": faqat yangi yozuv, belgilangan maydonlar, cheklangan uzunlik
        p.size == 4 && p[0] == "s" && p[2] == "ord" -> p[1].length >= 24 && get(p) == null && orderOk(p[3], newValue)
        p.size == 3 && p[0] == "q" -> p[1].length >= 24 && newValue == null
        p.size >= 2 && p[0] == "p" -> p[1].length >= 24
        else -> false
    }

    private fun orderOk(id: String, v: Any?): Boolean {
        val m = v as? Map<*, *> ?: return false
        if (id.length !in 10..40 || !m.containsKey("at") || !m.containsKey("l")) return false
        if (m.keys.any { it !in setOf("at", "v", "src", "n", "l", "by") }) return false
        if ((m["at"] as? Number)?.toLong() != now - 1) return false // {".sv":"timestamp"} == now
        val l = m["l"] as? String ?: return false
        if (l.length > 30000) return false
        (m["n"])?.let { if (it !is String || it.length > 300) return false }
        (m["src"])?.let { if (it !is String || it.length > 200) return false }
        (m["by"])?.let { if (it !is String || it.length > 80) return false }
        return true
    }

    private fun resolve(v: Any?): Any? = when (v) {
        is Map<*, *> -> if (v.size == 1 && v[".sv"] == "timestamp") now++ else v.entries.associate { (k, x) -> k.toString() to resolve(x) }
        is List<*> -> v.map { resolve(it) }
        else -> v
    }

    @Suppress("UNCHECKED_CAST")
    private fun node(p: List<String>, create: Boolean): MutableMap<String, Any?>? {
        var cur: MutableMap<String, Any?> = root
        for (seg in p) {
            val next = cur[seg]
            cur = when {
                next is MutableMap<*, *> -> next as MutableMap<String, Any?>
                create -> LinkedHashMap<String, Any?>().also { cur[seg] = it }
                else -> return null
            }
        }
        return cur
    }

    private fun get(p: List<String>): Any? {
        if (p.isEmpty()) return root.ifEmpty { null }
        val parent = node(p.dropLast(1), false) ?: return null
        return parent[p.last()]
    }

    private fun set(p: List<String>, v: Any?) {
        if (p.isEmpty()) { root.clear(); (v as? Map<*, *>)?.forEach { (k, x) -> root[k.toString()] = x }; return }
        if (v == null) { node(p.dropLast(1), false)?.remove(p.last()); prune(); return }
        node(p.dropLast(1), true)!![p.last()] = v
    }

    private fun prune() {
        fun clean(m: MutableMap<String, Any?>) {
            val it = m.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                @Suppress("UNCHECKED_CAST")
                (e.value as? MutableMap<String, Any?>)?.let { c -> clean(c); if (c.isEmpty()) it.remove() }
            }
        }
        clean(root)
    }

    private fun handle(method: String, p: List<String>, q: Map<String, String>, body: String, admin: Boolean): Pair<Int, String> {
        val denied = 401 to """{"error":"Permission denied"}"""
        if (p.firstOrNull() == ".settings") {
            if (!admin) return denied
            rulesSet = body
            return 200 to "{}"
        }
        val silent = q["print"] == "silent"
        return when (method) {
            "GET" -> {
                if (!admin && !canRead(p)) return denied
                val v = get(p)
                if (q["shallow"] == "true" && v is Map<*, *>) 200 to Json.write(v.keys.associate { it.toString() to true })
                else 200 to Json.write(v)
            }
            "PUT" -> {
                val v = resolve(Json.parse(body))
                if (!admin && !canWrite(p, v)) return denied
                set(p, v)
                if (silent) 204 to "" else 200 to Json.write(v)
            }
            "PATCH" -> {
                val v = resolve(Json.parse(body)) as? Map<*, *> ?: return 400 to """{"error":"bad"}"""
                if (!admin && v.any { (k, x) -> !canWrite(p + k.toString(), x) }) return denied
                for ((k, x) in v) set(p + k.toString().split('/'), x)
                if (silent) 204 to "" else 200 to Json.write(v)
            }
            "DELETE" -> {
                if (!admin && !canWrite(p, null)) return denied
                set(p, null)
                200 to "null"
            }
            else -> 405 to """{"error":"method"}"""
        }
    }

    override fun close() = Unit
}
