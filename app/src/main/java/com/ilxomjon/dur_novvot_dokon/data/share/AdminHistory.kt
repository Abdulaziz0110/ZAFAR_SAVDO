package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.data.onec.OneCRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.imsoft.shared.onec.OneCLedger
import uz.imsoft.shared.onec.OneCSales
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.LedgerOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v3.7 Admin "1C tarixi": do'kondagi BARCHA 1C savdolari bitta ro'yxatda.
 *  • Kompyuter (1C'dan): barcha savdo, mobil savdo, chakana va mijoz qaytargan hujjatlari — oxirgi 1 yil (OneCRepository).
 *  • Server (mijoz kartochkalari): savdo, vozvrat va TO'LOVLAR (StoreHistory).
 * Bir hujjat ikki marta chiqmasligi uchun: mijozning server kartochkasi qamragan vaqtdagi kompyuter hujjati ko'rsatilmaydi
 * (kartochkada bor). Qolgani (chakana, kartochkasi hali yuklanmagan mijozlar, eng so'nggi daqiqalar) — kompyuterdan.
 */
class AdminHistory(
    private val context: Context,
    private val history: StoreHistory,
    private val onec: OneCRepository,
    private val share: ShareRepository,
    private val catalog: CatalogRepository,
    scope: CoroutineScope,
) {
    class Data(val entries: List<LedgerEntry>, val version: Long, val loaded: Boolean)

    private var ver = 0L

    init {
        scope.launch { runCatching { history.load() }; runCatching { onec.load() } }
    }

    val data: StateFlow<Data> = combine(history.state.distinctUntilChanged { a, b -> a.version == b.version && a.loaded == b.loaded }, onec.state.map { it.sales }.distinctUntilChanged()) { h, sales -> build(h, sales) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, Data(emptyList(), -1L, false))

    val status: StateFlow<String> = combine(
        history.state.map { it.status }.distinctUntilChanged(),
        onec.state.map { it.sales }.distinctUntilChanged(),
        share.state.map { Triple(it.pcAt, it.pcStatus, it.pcOk) }.distinctUntilChanged(),
    ) { hs, sales, pc -> pcLine(sales, pc) + "\n" + hs.ifEmpty { context.getString(R.string.ah_server_wait) } }
        .stateIn(scope, SharingStarted.Eagerly, context.getString(R.string.ah_loading))

    private fun build(h: StoreHistory.State, sales: OneCSales?): Data {
        val out = ArrayList<LedgerEntry>(h.entries.size + (sales?.docs?.size ?: 0) * 4)
        // v3.8: server (akt sverki) savdo/qaytarishni tovarlarsiz beradi — kompyuter shu vaqtni qamragan bo'lsa, savdo va
        // qaytarish kompyuterdan (tovarlari bilan), to'lovlar serverdan. Kompyuter qamramagan vaqt — hammasi serverdan.
        val cov = sales?.let { OneCLedger.covered(it, System.currentTimeMillis()) }
        for (e in h.entries) {
            val goods = e.kind == uz.imsoft.shared.report.EntryKind.SALE || e.kind == uz.imsoft.shared.report.EntryKind.RETURN
            if (goods && cov != null && e.stamp in cov.first..cov.second) continue
            out += e
        }
        if (sales != null) {
            val byName = HashMap<String, String>()
            for (s in catalog.state.value.stores) {
                val key = LedgerMerge.nameKey(s.name)
                if (!byName.containsKey(key)) byName[key] = s.id      // putIfAbsent — API 24+, minSdk 23
            }
            val retail = context.getString(R.string.ah_retail)
            for (d in sales.docs) {
                if (!OneCLedger.isClientDoc(d.kind)) continue
                val sid = byName[LedgerMerge.nameKey(d.party)].orEmpty()
                val owner = d.party.ifBlank { retail }
                for (e in OneCLedger.entries(d)) out += e.copy(kindText = LedgerOwner.tag(e.kindText, sid, owner))
            }
        }
        out.sortBy { it.stamp }
        ver++
        return Data(out, ver, h.loaded)
    }

    private fun hm(ms: Long) = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(ms))

    private fun pcLine(sales: OneCSales?, pc: Triple<Long, String, Boolean>): String {
        if (sales == null) {
            val last = if (pc.first > 0) context.getString(R.string.ah_pc_last, hm(pc.first), pc.second) else context.getString(R.string.ah_pc_never)
            return context.getString(R.string.ah_pc_none) + "\n" + last
        }
        val n = sales.docs.count { OneCLedger.isClientDoc(it.kind) }
        val sb = StringBuilder(
            context.getString(R.string.ah_pc, uz.imsoft.shared.report.Stamp.formatDate(sales.from), uz.imsoft.shared.report.Stamp.formatDate(sales.to), n, hm(sales.at)),
        )
        val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, -362) }
        val yearAgo = uz.imsoft.shared.report.Stamp.of(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
        if (sales.from > yearAgo) sb.append(" · ").append(context.getString(R.string.ah_pc_filling))
        if (pc.first > 0 && !pc.third) sb.append('\n').append(context.getString(R.string.ah_pc_last, hm(pc.first), pc.second))
        return sb.toString()
    }
}
