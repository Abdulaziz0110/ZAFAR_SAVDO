package com.ilxomjon.dur_novvot_dokon.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences <-> JSON (turi bilan: s/b/i/l/f/ss) — sozlamalarni boshqa ilovaga (Agent -> Admin) ko'chirish uchun.
 * Faqat parol bilan shifrlangan zaxira ichida ishlatiladi.
 */
object PrefsDump {
    fun toJson(all: Map<String, *>): JSONObject {
        val o = JSONObject()
        for ((k, v) in all) {
            val a = when (v) {
                is String -> JSONArray().put("s").put(v)
                is Boolean -> JSONArray().put("b").put(v)
                is Int -> JSONArray().put("i").put(v)
                is Long -> JSONArray().put("l").put(v)
                is Float -> JSONArray().put("f").put(v.toDouble())
                is Set<*> -> JSONArray().put("ss").put(JSONArray(v.map { it.toString() }))
                else -> null
            } ?: continue
            o.put(k, a)
        }
        return o
    }

    /** [json] dagi qiymatlarni yozadi; [clear] bo'lsa avval hammasi o'chiriladi. Sinxron (commit) — keyin ilova qayta ishga tushadi. */
    fun apply(prefs: SharedPreferences, json: JSONObject, clear: Boolean = true): Boolean {
        val e = prefs.edit()
        if (clear) e.clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val a = json.optJSONArray(k) ?: continue
            when (a.optString(0)) {
                "s" -> e.putString(k, a.optString(1))
                "b" -> e.putBoolean(k, a.optBoolean(1))
                "i" -> e.putInt(k, a.optInt(1))
                "l" -> e.putLong(k, a.optLong(1))
                "f" -> e.putFloat(k, a.optDouble(1).toFloat())
                "ss" -> {
                    val arr = a.optJSONArray(1) ?: JSONArray()
                    e.putStringSet(k, (0 until arr.length()).map { arr.optString(it) }.toSet())
                }
            }
        }
        return e.commit()
    }
}
