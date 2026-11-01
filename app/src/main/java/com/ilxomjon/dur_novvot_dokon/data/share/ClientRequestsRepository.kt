package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.data.local.HistoryDb
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uz.imsoft.shared.ClientReqCodec
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.EditLine
import uz.imsoft.shared.EditRequest
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.PayRequest
import uz.imsoft.shared.ReqStatus
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Money2

data class ClientPay(val clientId: String, val clientName: String, val storeId: String, val req: PayRequest)
data class ClientEdit(val clientId: String, val clientName: String, val req: EditRequest)
data class ClientRequests(val pays: List<ClientPay> = emptyList(), val edits: List<ClientEdit> = emptyList()) {
    val newCount: Int get() = pays.count { !it.req.status.isFinal && it.req.status.state != ReqStatus.ACCEPTED } + edits.count { !it.req.status.isFinal }
}

/**
 * v3.2 Admin: 2-ilovadan kelgan to'lov va chek tuzatish so'rovlari.
 * To'lov: "Tasdiqlash" -> odatdagi to'lov navbatiga (server) qo'yiladi; holat "Admin qabul qildi", server ID kelgach
 * "Tasdiqlandi" (2-ilovada chek shundan keyin chiqadi). Tuzatish: tasdiqlansa 2-ilovadagi chek o'zgaradi.
 */
class ClientRequestsRepository(
    private val context: Context,
    private val store: ShareStore,
    private val history: HistoryDb,
    private val sendPayment: suspend (storeId: String, storeName: String, cash: Double, transfer: Double, click: Double, note: String) -> Long,
) {
    private val _state = MutableStateFlow(ClientRequests())
    val state: StateFlow<ClientRequests> = _state.asStateFlow()

    private fun cloud() = CloudDb(store.dbUrl, store.secret)

    /** Hammasini o'qiydi, yangilariga "ko'rildi" qo'yadi, tasdiqlangan to'lovlar holatini serverdan kuzatadi. @return yangilar soni. */
    suspend fun refresh(notify: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (!store.isConfigured) return@withContext 0
        val db = cloud()
        val pays = ArrayList<ClientPay>()
        val edits = ArrayList<ClientEdit>()
        val now = System.currentTimeMillis()
        for (c in store.clients()) {
            val rawPay = runCatching { db.get("s/${c.readKey}/pay") }.getOrNull()
            for (p in ClientReqCodec.all(rawPay) { k, v -> ClientReqCodec.payFromCloud(k, v) }) {
                if (p.at in 1 until now - 60L * 86_400_000L) { runCatching { db.delete("s/${c.readKey}/pay/${p.id}") }; continue }
                var q = p
                if (p.status.state == ReqStatus.SENT) {
                    val st = ReqStatus(ReqStatus.SEEN)
                    if (runCatching { db.put("s/${c.readKey}/pay/${p.id}/st", ClientReqCodec.statusToCloud(st)) }.isSuccess) q = p.copy(status = st.copy(at = now))
                }
                pays += ClientPay(c.id, c.name, c.storeId, q)
            }
            val rawEd = runCatching { db.get("s/${c.readKey}/ed") }.getOrNull()
            for (e in ClientReqCodec.all(rawEd) { k, v -> ClientReqCodec.editFromCloud(k, v) }) {
                if (e.at in 1 until now - 60L * 86_400_000L) { runCatching { db.delete("s/${c.readKey}/ed/${e.id}") }; continue }
                var q = e
                if (e.status.state == ReqStatus.SENT) {
                    val st = ReqStatus(ReqStatus.SEEN)
                    if (runCatching { db.put("s/${c.readKey}/ed/${e.id}/st", ClientReqCodec.statusToCloud(st)) }.isSuccess) q = e.copy(status = st.copy(at = now))
                }
                edits += ClientEdit(c.id, c.name, q)
            }
        }
        // Tasdiqlangan to'lovlar: server ID kelganmi
        val links = payLinks()
        for ((reqId, link) in links.toMap()) {
            val p = pays.firstOrNull { it.req.id == reqId } ?: continue
            if (p.req.status.state != ReqStatus.ACCEPTED) { if (p.req.status.isFinal) links.remove(reqId); continue }
            val e = history.entry(link) ?: continue
            if (e.state == OutboxState.SENT && !e.serverRowId.isNullOrBlank()) {
                val c = store.client(p.clientId) ?: continue
                val st = ReqStatus(ReqStatus.OK, now, e.serverRowId.orEmpty())
                if (runCatching { db.put("s/${c.readKey}/pay/$reqId/st", ClientReqCodec.statusToCloud(st)) }.isSuccess) {
                    links.remove(reqId)
                    val i = pays.indexOf(p)
                    if (i >= 0) pays[i] = p.copy(req = p.req.copy(status = st))
                }
            }
        }
        savePayLinks(links)
        pays.sortByDescending { it.req.at }
        edits.sortByDescending { it.req.at }
        val prev = _state.value
        val next = ClientRequests(pays, edits)
        _state.value = next
        val fresh = next.pays.count { p -> p.req.status.state == ReqStatus.SEEN && prev.pays.none { it.req.id == p.req.id } } +
            next.edits.count { e -> e.req.status.state == ReqStatus.SEEN && prev.edits.none { it.req.id == e.req.id } }
        if (notify && fresh > 0 && next.newCount > 0) {
            com.ilxomjon.dur_novvot_dokon.data.extras.Extras.notify(
                context, 4107, context.getString(R.string.cr_notif_title), context.getString(R.string.cr_notif_text, next.newCount),
            )
        }
        fresh
    }

    /** To'lovni tasdiqlash: serverga to'lov navbati. 2-ilovada "Admin qabul qildi", server ID kelgach "Tasdiqlandi". */
    suspend fun acceptPay(p: ClientPay) {
        require(p.storeId.isNotEmpty()) { context.getString(R.string.cr_err_no_store) }
        val c = store.client(p.clientId) ?: throw IllegalStateException(context.getString(R.string.cr_err_no_client))
        val transfer = if (p.req.method == "p") p.req.som else 0.0
        val click = if (p.req.method == "c") p.req.som else 0.0
        val cash = if (transfer == 0.0 && click == 0.0) p.req.som else 0.0
        val note = (context.getString(R.string.cr_pay_note) + " " + p.req.note).trim().take(200)
        val id = sendPayment(p.storeId, p.clientName, cash, transfer, click, note)
        val links = payLinks()
        links[p.req.id] = id
        savePayLinks(links)
        withContext(Dispatchers.IO) { cloud().put("s/${c.readKey}/pay/${p.req.id}/st", ClientReqCodec.statusToCloud(ReqStatus(ReqStatus.ACCEPTED))) }
        refresh()
    }

    suspend fun reject(clientId: String, kind: String, id: String, reason: String) {
        val c = store.client(clientId) ?: return
        withContext(Dispatchers.IO) { cloud().put("s/${c.readKey}/$kind/$id/st", ClientReqCodec.statusToCloud(ReqStatus(ReqStatus.REJECTED, error = reason))) }
        refresh()
    }

    /** Chek tuzatishini tasdiqlash: 2-ilovadagi shu chek qatorlari almashtiriladi (keyingi yuborishda). */
    suspend fun acceptEdit(e: ClientEdit) {
        val c = store.client(e.clientId) ?: throw IllegalStateException(context.getString(R.string.cr_err_no_client))
        saveEdit(e.clientId, e.req.doc, e.req.lines, e.req.note)
        store.update(e.clientId) { it.copy(dirty = true) }
        withContext(Dispatchers.IO) { cloud().put("s/${c.readKey}/ed/${e.req.id}/st", ClientReqCodec.statusToCloud(ReqStatus(ReqStatus.OK))) }
        refresh()
    }

    // ---- tuzatishlar (ShareRepository.buildSnapshot qo'llaydi) ----

    @Suppress("UNCHECKED_CAST")
    private fun editsMap(): MutableMap<String, MutableMap<String, Any?>> {
        val raw = runCatching { Json.parse(store.edits.ifBlank { "{}" }) as? Map<String, Any?> }.getOrNull().orEmpty()
        val out = LinkedHashMap<String, MutableMap<String, Any?>>()
        for ((k, v) in raw) out[k] = LinkedHashMap((v as? Map<String, Any?>).orEmpty())
        return out
    }

    private fun saveEdit(clientId: String, doc: String, lines: List<EditLine>, note: String) {
        val m = editsMap()
        val cm = m.getOrPut(clientId) { LinkedHashMap() }
        cm[doc] = linkedMapOf("l" to lines.map { listOf(it.name, it.qty, it.priceSom, it.priceUsd) }, "n" to note, "at" to System.currentTimeMillis())
        // Juda eskilari (200 dan ortig'i) tashlanadi
        while (cm.size > 200) cm.remove(cm.keys.first())
        store.edits = Json.write(m)
    }

    /** Mijoz yozuvlariga tasdiqlangan tuzatishlarni qo'llaydi: hujjat qatorlari yangi qatorlar bilan almashadi. */
    @Suppress("UNCHECKED_CAST")
    fun apply(clientId: String, entries: List<LedgerEntry>): List<LedgerEntry> {
        val cm = editsMap()[clientId] ?: return entries
        if (cm.isEmpty()) return entries
        val out = ArrayList<LedgerEntry>(entries.size)
        val done = HashSet<String>()
        for (e in entries) {
            val key = "${e.stamp}|${e.kind.code}|${e.kindText}"
            val ed = cm[key] as? Map<String, Any?>
            if (ed == null) { out += e; continue }
            if (!done.add(key)) continue
            val lines = (ed["l"] as? List<Any?>).orEmpty().mapNotNull { x ->
                val l = x as? List<Any?> ?: return@mapNotNull null
                fun d(i: Int) = (l.getOrNull(i) as? Number)?.toDouble() ?: 0.0
                EditLine(l.getOrNull(0)?.toString().orEmpty(), d(1), d(2), d(3))
            }
            for (l in lines) {
                if (l.qty <= 0.0) continue
                out += LedgerEntry(
                    e.stamp, e.kind, e.kindText, l.name, l.qty,
                    mapOf(Col.PRICE to Money2(l.priceSom, l.priceUsd), Col.SUM to Money2(l.qty * l.priceSom, l.qty * l.priceUsd)),
                    mapOf(EDITED to Money2(1.0, 0.0)),
                )
            }
        }
        return out
    }

    private fun payLinks(): MutableMap<String, Long> {
        val o = runCatching { JObj.parse(store.payLinks.ifBlank { "{}" }) }.getOrNull() ?: return LinkedHashMap()
        val m = LinkedHashMap<String, Long>()
        for ((k, v) in o.map) (v as? Number)?.toLong()?.let { m[k] = it }
        return m
    }

    private fun savePayLinks(m: Map<String, Long>) { store.payLinks = Json.write(m) }

    companion object {
        const val EDITED = "tahrir"
    }
}
