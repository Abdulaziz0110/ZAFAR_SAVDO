package uz.imsoft.shared

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
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
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val port: Int get() = server.address.port
    val requests = ArrayList<String>()

    init {
        server.createContext("/") { ex ->
            val method = ex.requestHeaders.getFirst("X-HTTP-Method-Override") ?: ex.requestMethod
            val rawPath = ex.requestURI.rawPath.removeSuffix(".json").trim('/')
            val path = rawPath.split('/').filter { it.isNotEmpty() }.map { URLDecoder.decode(it, "UTF-8") }
            val q = (ex.requestURI.rawQuery ?: "").split('&').filter { it.contains('=') }
                .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            synchronized(this) { requests += "$method /${path.joinToString("/")}" }
            val admin = q["auth"] == secret
            val (code, resp) = synchronized(this) { handle(method, path, q, body, admin) }
            val bytes = resp.toByteArray(Charsets.UTF_8)
            if (code == 204) { ex.sendResponseHeaders(204, -1) } else { ex.sendResponseHeaders(code, bytes.size.toLong()); ex.responseBody.use { it.write(bytes) } }
            ex.close()
        }
        server.start()
    }

    /** CloudDb uchun HTTP: https://fake... manzilini shu serverga yo'naltiradi. */
    fun http(): CloudDb.Http = object : CloudDb.Http {
        private val real = UrlHttp()
        override fun request(method: String, url: String, body: String?, timeoutMs: Int): CloudDb.Response =
            real.request(method, url.replace(Regex("^https://[^/]+"), "http://127.0.0.1:$port"), body, timeoutMs)
    }

    private fun canRead(p: List<String>): Boolean = when {
        p.isEmpty() -> false
        p[0] in setOf("s", "q", "p") && p.size >= 2 -> p[1].length >= 24
        else -> false
    }

    private fun canWrite(p: List<String>, newValue: Any?): Boolean = when {
        p.size == 3 && p[0] == "s" && p[2] == "req" -> p[1].length >= 24 && newValue is Number
        p.size == 3 && p[0] == "q" -> p[1].length >= 24 && newValue == null
        p.size >= 2 && p[0] == "p" -> p[1].length >= 24
        else -> false
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

    override fun close() = server.stop(0)
}
