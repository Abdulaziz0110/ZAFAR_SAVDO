package com.ilxomjon.dur_novvot_dokon.ui.ledgerhost

import android.app.Activity
import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.repo.RepeatSale
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.Navigator
import com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerData
import com.ilxomjon.dur_novvot_dokon.ui.ledger.LedgerHost
import com.ilxomjon.dur_novvot_dokon.ui.ledger.MatchStatus
import com.ilxomjon.dur_novvot_dokon.ui.ledger.PrintTarget
import com.ilxomjon.dur_novvot_dokon.ui.ledger.RepeatLine
import com.ilxomjon.dur_novvot_dokon.ui.ledger.RepeatPlan
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ilxomjon.dur_novvot_dokon.data.share.ClientOrder
import com.ilxomjon.dur_novvot_dokon.data.share.ShareClient
import uz.imsoft.shared.OrderLine
import uz.imsoft.shared.OrderRequest
import uz.imsoft.shared.report.CatalogMatch
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.Stamp

/**
 * 1-ilova uchun 1C tarixi amallari: takrorlash -> savatcha (narx JORIY katalogdan, odatdagi qoidalar bilan),
 * chek -> mijozning chek printeri (bulut navbati), 2-ilova buyurtmasini savatchaga olish.
 * v2.6: ko'p mijozli — hammasi ekranda tanlangan mijoz (yoki buyurtma bergan mijoz) uchun.
 */
object DokonLedgerHost : LedgerHost {
    /** Takrorlash manbai. [storeId] — v3.6 Admin "barcha mijozlar" tarixidan (bo'sh — [clientId] dagi do'kon). */
    class Tag(val deal: DealMode, val usd: Boolean, val orderId: String?, val clientId: String, val storeId: String = "")

    /** v3.6: Admin — barcha mijozlarning 1C tarixi (serverdan, 1 yil); Agent — 2-ilova uchun tanlangan mijoz. */
    private val admin: Boolean get() = com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin

    /** v4.0.1: faqat 1C bazasidagi yozuvlar (telefonda qilingan, hali 1C'ga yetmaganlarsiz) — statistika shundan. */
    val dbData: StateFlow<LedgerData> by lazy { baseFlow().stateIn(ServiceLocator.appScope, SharingStarted.Eagerly, LedgerData(emptyList(), "", -1L, false)) }

    private fun baseFlow(): kotlinx.coroutines.flow.Flow<LedgerData> =
        if (admin) {
            ServiceLocator.adminHistory.data.map { LedgerData(it.entries, "", it.version, it.loaded) }
        } else {
            // v3.7 Agent: Admin yuborgan "1C tarixi" (barcha savdolar, 1 yil); bo'lmasa — avvalgidek tanlangan mijoz
            startAgentSync()
            kotlinx.coroutines.flow.combine(ServiceLocator.share.ledger, ServiceLocator.historyShare.agent) { l, a ->
                if (a.entries.isNotEmpty()) LedgerData(a.entries, "", a.version * 2 + 1, true)
                else LedgerData(l.entries, l.clientName, l.version * 2, l.loaded)
            }
        }

    override val ledgerData: StateFlow<LedgerData> by lazy {
        // v4.0.2 Agent: Admin telefonida qilingan, hali 1C'ga yetmagan savdolar ham (Admin yuboradi)
        val adminPending: kotlinx.coroutines.flow.Flow<List<uz.imsoft.shared.report.LedgerEntry>> =
            if (admin) kotlinx.coroutines.flow.flowOf(emptyList()) else ServiceLocator.historyShare.agent.map { it.pending }
        val base = kotlinx.coroutines.flow.combine(dbData, adminPending.distinctUntilChanged()) { d, p ->
            if (p.isEmpty()) d else LedgerData((d.entries + p).sortedBy { it.stamp }, d.clientName, d.version * 4096 + (p.hashCode() and 0xFFF), d.loaded)
        }
        // v4.0 "Yagona ro'yxat": telefonda qilingan savdo/to'lov 1C'dan kelguncha tepada ("1C'ga ketmoqda")
        val label = ServiceLocator.context.getString(R.string.pending_1c)
        kotlinx.coroutines.flow.combine(base, ServiceLocator.localDocs) { d, locals ->
            if (locals.isEmpty()) d
            else LedgerData(uz.imsoft.shared.report.PendingMerge.merge(d.entries, locals, label), d.clientName, d.version * 65_536 + (locals.hashCode() and 0xFFFF), true)
        }.stateIn(ServiceLocator.appScope, SharingStarted.Eagerly, LedgerData(emptyList(), "", -1L, false))
    }

    private fun startAgentSync() {
        ServiceLocator.appScope.launch {
            var n = 0
            while (true) {
                runCatching { ServiceLocator.historyShare.sync(ServiceLocator.cloudConfig, false) }
                // v5.2 (A7): mahsulot ma'lumotlari — kamroq, 5 daqiqada bir (kichik fayl, kam o'zgaradi)
                if (n % 10 == 0) runCatching { ServiceLocator.bio.sync(ServiceLocator.cloudConfig) }
                n++
                kotlinx.coroutines.delay(30_000L)
            }
        }
    }

    override val syncStatus: StateFlow<String>? by lazy {
        if (!admin) ServiceLocator.historyShare.agent.map { it.status }.stateIn(ServiceLocator.appScope, SharingStarted.Eagerly, "")
        else ServiceLocator.adminHistory.status
    }

    /**
     * v5.2 (A5): Admin tarixdagi savdoni o'zgartira oladi (cheklovsiz — istalgan eski hujjat).
     * Faqat 1C'dan kelgan tovarli hujjat uchun: telefonda qilingan, hali 1C'ga yetmagani uchun emas.
     */
    override fun editLabel(doc: LedgerDoc): Int =
        if (admin && doc.isGoods && onecDoc(doc) != null) R.string.de_edit else 0

    override fun requestEdit(f: androidx.fragment.app.Fragment, doc: LedgerDoc) {
        val d = onecDoc(doc) ?: return
        (f.activity as? Navigator)?.push(com.ilxomjon.dur_novvot_dokon.ui.admin.DocEditFragment.of(d.id))
    }

    /** Tarixdagi yozuvga mos 1C hujjati (raqam va vaqt bo'yicha). */
    private fun onecDoc(doc: LedgerDoc): uz.imsoft.shared.onec.OneCDoc? {
        val number = uz.imsoft.shared.report.LedgerOwner.plain(doc.kindText).substringAfter("\u2116", "").trim()
        if (number.isEmpty()) return null
        val docs = ServiceLocator.onec.state.value.sales?.docs ?: return null
        return docs.firstOrNull { it.number == number && it.stamp == doc.stamp && it.kind in uz.imsoft.shared.onec.DocField.EDITABLE }
    }

    override fun emptyText(ctx: Context): String? = ctx.getString(if (admin) R.string.ah_empty else R.string.ah_agent_empty)

    override fun onSyncStatus(f: androidx.fragment.app.Fragment) {
        if (admin) { HistoryStatusDialog.show(f); return }
        f.view?.let { com.google.android.material.snackbar.Snackbar.make(it, R.string.ah_agent_syncing, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() }
        ServiceLocator.appScope.launch { runCatching { ServiceLocator.historyShare.sync(ServiceLocator.cloudConfig, true) } }
    }

    override val receiptHeader: String get() = ServiceLocator.shareStore.receiptHeader
    override val receiptFooter: String get() = ServiceLocator.shareStore.receiptFooter
    override val receiptStaff: String get() = ServiceLocator.shareStore.receiptStaff
    override val receiptStyle: uz.imsoft.shared.receipt.ReceiptStyle get() = ServiceLocator.share.receiptStyle
    override val receiptLogo: android.graphics.Bitmap? get() = uz.imsoft.shared.android.LogoCache.get(ServiceLocator.shareStore.receiptLogo)
    override val fuzzySearch: Boolean get() = ServiceLocator.appPrefs.fFuzzySearch

    override fun repeatLabel(doc: LedgerDoc): Int = when {
        doc.goods.isEmpty() -> 0
        uz.imsoft.shared.report.LedgerOwner.of(doc.kindText) != null -> when (doc.kind) {
            EntryKind.SALE -> R.string.lgh_repeat_sale
            EntryKind.RETURN -> R.string.lgh_repeat_return
            else -> 0
        }
        ServiceLocator.share.currentClient().let { it == null || it.kind == "s" } -> 0
        doc.kind == EntryKind.SALE -> R.string.lgh_repeat_sale
        doc.kind == EntryKind.RETURN -> R.string.lgh_repeat_return
        else -> 0
    }

    private fun matcher() = CatalogMatch(ServiceLocator.catalog.state.value.products, { it.name }, { it.id })

    private fun lines(src: List<OrderLine>): List<RepeatLine> {
        val m = matcher()
        // Bir xil tovar bir necha qatorda bo'lsa — birlashtiriladi
        val merged = LinkedHashMap<String, OrderLine>()
        for (l in src) {
            val k = l.productId.ifEmpty { l.name }
            merged[k] = merged[k]?.let { it.copy(qty = it.qty + l.qty) } ?: l
        }
        return merged.values.map { l ->
            val r = m.match(l.name, l.productId)
            val p: Product? = r.item
            val st = when (r.level) {
                CatalogMatch.Level.EXACT -> MatchStatus.EXACT
                CatalogMatch.Level.APPROX -> MatchStatus.APPROX
                CatalogMatch.Level.NONE -> MatchStatus.NONE
            }
            RepeatLine(l.name, l.qty, p?.id.orEmpty(), st, p?.name.orEmpty(), checked = st == MatchStatus.EXACT)
        }
    }

    private fun options(ctx: Context, storeId: String): Pair<String, List<String>> {
        val d = ServiceLocator.draft.state.value
        if (d.lines.isEmpty()) return "" to emptyList()
        val o = ArrayList<String>()
        o += ctx.getString(R.string.repeat_replace, d.lines.size)
        if (storeId.isNotEmpty() && d.store?.id == storeId) o += ctx.getString(R.string.repeat_add)
        if (ServiceLocator.appPrefs.fMultiCart) o += ctx.getString(R.string.repeat_park)
        return ctx.getString(R.string.repeat_title) to o
    }

    private fun modeFor(ctx: Context, storeId: String, option: Int): RepeatSale.Mode {
        val (_, opts) = options(ctx, storeId)
        if (option < 0 || option >= opts.size) return RepeatSale.Mode.REPLACE
        val label = opts[option]
        return when (label) {
            ctx.getString(R.string.repeat_add) -> RepeatSale.Mode.ADD
            ctx.getString(R.string.repeat_park) -> RepeatSale.Mode.PARK
            else -> RepeatSale.Mode.REPLACE
        }
    }

    override suspend fun prepareRepeat(ctx: Context, doc: LedgerDoc): RepeatPlan {
        val src = doc.goods.map { OrderLine(it.product, it.qty) }
        val owner = uz.imsoft.shared.report.LedgerOwner.of(doc.kindText)
        val c = if (owner != null) null else ServiceLocator.share.currentClient()
        val ownerStore = owner?.first.orEmpty()
        val (ot, opts) = options(ctx, ownerStore.ifEmpty { c?.storeId.orEmpty() })
        val deal = if (doc.kind == EntryKind.RETURN) DealMode.RETURN else DealMode.SALE
        return RepeatPlan(
            title = ctx.getString(if (deal == DealMode.RETURN) R.string.lgh_repeat_return else R.string.lgh_repeat_sale) + " · " + Stamp.formatDate(doc.stamp),
            lines = lines(src), showPriceType = true, defaultWholesale = ServiceLocator.draft.state.value.priceType == PriceType.WHOLESALE,
            showNote = false, confirmLabel = R.string.lgh_to_cart, hint = ctx.getString(R.string.lgh_repeat_hint, owner?.second ?: c?.name.orEmpty()),
            tag = Tag(deal, doc.hasUsd && !doc.hasSom, null, c?.id.orEmpty(), ownerStore), options = opts, optionsTitle = ot,
        )
    }

    fun planForOrder(ctx: Context, co: ClientOrder): RepeatPlan {
        val o = co.order
        val c = ServiceLocator.shareStore.client(co.clientId)
        val (ot, opts) = options(ctx, c?.storeId.orEmpty())
        val hint = listOfNotNull(
            ctx.getString(R.string.lgh_order_hint, co.clientName),
            o.by.takeIf { it.isNotBlank() }?.let { ctx.getString(R.string.ord_by, it) },
            o.note.takeIf { it.isNotBlank() }?.let { ctx.getString(R.string.ord_note, it) },
        ).joinToString("\n")
        return RepeatPlan(
            title = ctx.getString(R.string.ord_item_title, java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US).format(java.util.Date(o.at))),
            lines = lines(o.lines), showPriceType = true, defaultWholesale = true, showNote = false, confirmLabel = R.string.lgh_to_cart,
            hint = hint, tag = Tag(DealMode.SALE, false, o.id, co.clientId), options = opts, optionsTitle = ot,
        )
    }

    override suspend fun submitRepeat(ctx: Context, plan: RepeatPlan, lines: List<RepeatLine>, wholesale: Boolean, note: String, option: Int): String {
        val tag = plan.tag as? Tag ?: Tag(DealMode.SALE, false, null, ServiceLocator.share.currentClient()?.id.orEmpty())
        val c: ShareClient? = ServiceLocator.shareStore.client(tag.clientId)
        val sid = tag.storeId.ifEmpty { c?.storeId.orEmpty() }
        val st = sid.takeIf { it.isNotEmpty() }?.let { ServiceLocator.catalog.storeById(it) }
            ?: throw IllegalStateException(ctx.getString(R.string.cl_need_store, c?.name.orEmpty()))
        val r = ServiceLocator.repeatSale.repeatItems(
            st.id, st.name, tag.deal, if (wholesale) PriceType.WHOLESALE else PriceType.CASH,
            if (tag.usd) Currency.USD else Currency.SOM, lines.map { RepeatSale.Item(it.productId, it.qty) }, modeFor(ctx, st.id, option),
        )
        if (r.added == 0) throw IllegalStateException(ctx.getString(R.string.repeat_nothing))
        if (c != null) tag.orderId?.let { id -> runCatching { ServiceLocator.share.setOrderStatus(c.id, id, OrderRequest.Status.TAKEN) } }
        val parts = ArrayList<String>()
        parts += ctx.getString(R.string.repeat_done, r.added)
        if (r.missing > 0) parts += ctx.getString(R.string.sale_repeat_missing, r.missing)
        return parts.joinToString("\n")
    }

    override fun afterSubmit(activity: Activity, plan: RepeatPlan) {
        (activity as? Navigator)?.openTab(R.id.nav_sale)
    }

    override fun printTargets(ctx: Context): List<PrintTarget> {
        val s = ServiceLocator.shareStore
        val c = s.current()
        if (!s.isConfigured || c == null || c.queueKey.isEmpty()) return emptyList()
        return listOf(PrintTarget(ctx.getString(R.string.lgh_branch_printer, c.name)) { bytes ->
            ServiceLocator.share.sendToBranchPrinter(bytes, c.id)
            ctx.getString(R.string.lgh_branch_sent)
        })
    }
}
