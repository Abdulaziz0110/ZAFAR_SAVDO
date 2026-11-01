package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v4.0 Admin "Boss": har agentning kunlik savdosi va yig'gan puli (Admin orqali serverga yozilgan savdo/to'lovlardan).
 * Telefonda 14 kun saqlanadi. Summa — protokol matnidan (savdo: miqdor x narx; to'lov: naqd + o'tkazma + click).
 */
class AgentStats(private val context: Context) {
    data class Day(val agentId: String, val sales: Double, val payments: Double, val docs: Int)

    private val prefs by lazy { context.getSharedPreferences("agent_stats", Context.MODE_PRIVATE) }
    private val _today = MutableStateFlow<List<Day>>(emptyList())
    val today: StateFlow<List<Day>> = _today.asStateFlow()

    private fun day(ms: Long = System.currentTimeMillis()) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    @Suppress("UNCHECKED_CAST")
    private fun read(): MutableMap<String, MutableMap<String, List<Double>>> = runCatching {
        (Json.parse(prefs.getString("d", "{}").orEmpty()) as? Map<String, Any?>).orEmpty().mapValues { (_, v) ->
            (v as? Map<String, Any?>).orEmpty().mapValues { (_, x) -> (x as? List<Any?>).orEmpty().map { (it as? Number)?.toDouble() ?: 0.0 } }.toMutableMap()
        }.toMutableMap()
    }.getOrDefault(HashMap())

    fun load() { publish(read()) }

    private fun publish(all: Map<String, Map<String, List<Double>>>) {
        _today.value = all[day()].orEmpty().map { (id, v) -> Day(id, v.getOrElse(0) { 0.0 }, v.getOrElse(1) { 0.0 }, v.getOrElse(2) { 0.0 }.toInt()) }
            .sortedByDescending { it.sales + it.payments }
    }

    /** Ko'prik serverga yozgan savdo (t=1) yoki to'lov (t=4). */
    @Synchronized
    fun record(agentId: String, type: String, payload: String) {
        val (sale, pay) = amounts(type, payload)
        if (sale == 0.0 && pay == 0.0) return
        val all = read()
        val d = day()
        val m = all.getOrPut(d) { HashMap() }
        val v = m[agentId] ?: listOf(0.0, 0.0, 0.0)
        m[agentId] = listOf(v[0] + sale, v[1] + pay, v[2] + 1)
        val keep = all.keys.sortedDescending().take(14).toSet()
        all.keys.retainAll(keep)
        prefs.edit().putString("d", Json.write(all)).apply()
        publish(all)
    }

    companion object {
        /** Protokol matnidan summa: savdo (qaytarish manfiy) va to'lov. So'm va dollar alohida emas — so'm qismi. */
        fun amounts(type: String, payload: String): Pair<Double, Double> {
            val head = payload.substringBefore('~').split('^')
            fun d(s: String?) = s?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            return when (type) {
                "1" -> {
                    val lines = payload.substringAfter('~', "").split('@').filter { it.isNotBlank() }
                    val sum = lines.sumOf { l -> val f = l.split('^'); d(f.getOrNull(1)) * d(f.getOrNull(2)) } - d(head.getOrNull(8))
                    val ret = head.getOrNull(10) == "2"
                    (if (ret) -sum else sum) to 0.0
                }
                "4" -> 0.0 to (if (head.size > 9) d(head[7]) + d(head[8]) + d(head[9]) else d(head.getOrNull(2)))
                else -> 0.0 to 0.0
            }
        }
    }
}
