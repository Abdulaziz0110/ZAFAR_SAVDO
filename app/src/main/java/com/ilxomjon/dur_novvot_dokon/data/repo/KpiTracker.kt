package com.ilxomjon.dur_novvot_dokon.data.repo

import com.ilxomjon.dur_novvot_dokon.data.AppPrefs
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.data.local.KpiRow

/**
 * Asosiy KPI: do'kon tanlangandan buyurtma navbatga tushgunicha qancha vaqt va necha bosish ketdi.
 * Faqat shu telefonda saqlanadi, do'kon nomi/ID'si emas, faqat xesh. Sozlamada yoqiladi (odatda o'chiq).
 */
class KpiTracker(private val db: HistoryDb, private val prefs: AppPrefs) {
    private var start = 0L
    private var taps = 0
    private var store = ""

    @Synchronized fun startIfIdle(storeId: String) {
        if (!prefs.fKpi || storeId.isEmpty()) return
        if (start == 0L || store != storeId) { start = System.currentTimeMillis(); taps = 0; store = storeId }
    }

    @Synchronized fun tap() { if (start != 0L) taps++ }

    @Synchronized fun cancel() { start = 0L; taps = 0; store = "" }

    @Synchronized fun finish(items: Int) {
        if (!prefs.fKpi || start == 0L) { cancel(); return }
        val s = start; val t = taps + 1; val h = store.hashCode().toString(16)
        cancel()
        runCatching { db.addKpi(s, System.currentTimeMillis(), t, items, h) }
    }

    data class Summary(val count: Int, val medianSeconds: Double, val medianTaps: Double, val last: List<KpiRow>)

    fun summary(): Summary {
        val rows = runCatching { db.kpi(50) }.getOrDefault(emptyList())
        fun med(v: List<Double>) = if (v.isEmpty()) 0.0 else v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
        return Summary(rows.size, med(rows.map { (it.end - it.start) / 1000.0 }), med(rows.map { it.taps.toDouble() }), rows.take(10))
    }
}
