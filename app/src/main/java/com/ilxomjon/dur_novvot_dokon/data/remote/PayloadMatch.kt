package com.ilxomjon.dur_novvot_dokon.data.remote

/**
 * Serverdagi matn bizning yuborganimiz bilan bir xilmi. Agar ustun unicode bo'lmasa (varchar), lotin bo'lmagan harflar
 * "?" bo'lib saqlanadi — shuni hisobga oladi. Oxiridagi probellar (char ustun) e'tiborga olinmaydi.
 */
object PayloadMatch {
    fun same(sent: String, stored: String): Boolean {
        val a = sent.trimEnd()
        val b = stored.trimEnd()
        if (a == b) return true
        if (a.length != b.length) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x == y) continue
            if (x.code > 127 && y == '?') continue
            return false
        }
        return true
    }
}
