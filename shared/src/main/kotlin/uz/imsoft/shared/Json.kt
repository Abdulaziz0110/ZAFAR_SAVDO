package uz.imsoft.shared

/**
 * Kichik, bog'liqliksiz JSON o'quvchi/yozuvchi (ikkala ilova va JVM testlarida bir xil ishlaydi).
 * Qiymatlar: null, Boolean, Double/Long (raqam), String, List<Any?>, Map<String, Any?> (tartib saqlanadi).
 */
object Json {
    class ParseException(msg: String) : Exception(msg)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw ParseException("Ortiqcha belgi: ${p.i}")
        return v
    }

    fun write(v: Any?): String = StringBuilder().also { write(v, it) }.toString()

    fun write(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> num(v, sb)
            is Float -> num(v.toDouble(), sb)
            is Number -> num(v.toDouble(), sb)
            is CharSequence -> str(v, sb)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    str(k.toString(), sb)
                    sb.append(':')
                    write(x, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(x, sb)
                }
                sb.append(']')
            }
            is Array<*> -> write(v.asList(), sb)
            else -> str(v.toString(), sb)
        }
    }

    private fun num(d: Double, sb: StringBuilder) {
        if (d.isNaN() || d.isInfinite()) { sb.append("0"); return }
        if (d == Math.rint(d) && Math.abs(d) < 9.0E15) sb.append(d.toLong()) else sb.append(d.toString())
    }

    private fun str(s: CharSequence, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(): Any? {
            if (i >= s.length) throw ParseException("Kutilmagan oxir")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw ParseException("Noto'g'ri belgi '$c' @$i")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw ParseException("Kutilgan $word @$i")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw ParseException("Kalit kutilgan @$i")
                val k = string()
                ws()
                if (i >= s.length || s[i] != ':') throw ParseException("':' kutilgan @$i")
                i++
                ws()
                m[k] = value()
                ws()
                if (i >= s.length) throw ParseException("Kutilmagan oxir")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw ParseException("',' yoki '}' kutilgan @$i")
                }
            }
        }

        private fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                ws()
                l += value()
                ws()
                if (i >= s.length) throw ParseException("Kutilmagan oxir")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw ParseException("',' yoki ']' kutilgan @$i")
                }
            }
        }

        private fun string(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw ParseException("Satr yopilmagan")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw ParseException("Satr yopilmagan")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseException("\\u noto'g'ri")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw ParseException("Noto'g'ri escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            val t = s.substring(start, i)
            if (t.none { it == '.' || it == 'e' || it == 'E' }) t.toLongOrNull()?.let { return it }
            return t.toDoubleOrNull() ?: throw ParseException("Noto'g'ri raqam '$t'")
        }
    }
}

/** JSON obyektidan qulay o'qish. */
@Suppress("UNCHECKED_CAST")
class JObj(val map: Map<String, Any?>) {
    fun str(k: String, def: String = ""): String = when (val v = map[k]) {
        null -> def
        is String -> v
        is Double -> if (v == Math.rint(v)) v.toLong().toString() else v.toString()
        else -> v.toString()
    }
    fun num(k: String, def: Double = 0.0): Double = when (val v = map[k]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: def
        else -> def
    }
    fun long(k: String, def: Long = 0L): Long = when (val v = map[k]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: def
        else -> def
    }
    fun bool(k: String, def: Boolean = false): Boolean = (map[k] as? Boolean) ?: def
    fun obj(k: String): JObj? = (map[k] as? Map<String, Any?>)?.let { JObj(it) }
    fun arr(k: String): List<Any?> = (map[k] as? List<Any?>).orEmpty()
    fun objs(k: String): List<JObj> = arr(k).mapNotNull { (it as? Map<String, Any?>)?.let(::JObj) }
    fun has(k: String) = map.containsKey(k)

    companion object {
        fun parse(text: String): JObj = JObj(Json.parse(text) as? Map<String, Any?> ?: throw Json.ParseException("Obyekt kutilgan"))
    }
}
