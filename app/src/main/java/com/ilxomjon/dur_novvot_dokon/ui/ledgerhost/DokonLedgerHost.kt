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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /** Takrorlash manbai. */
    class Tag(val deal: DealMode, val usd: Boolean, val orderId: String?, val clientId: String)

    override val ledgerData: StateFlow<LedgerData> by lazy {
        ServiceLocator.share.ledger.map { LedgerData(it.entries, it.clientName, it.version, it.loaded) }
            .stateIn(ServiceLocator.appScope, SharingStarted.Eagerly, LedgerData(emptyList(), "", -1L, false))
    }

    override val receiptHeader: String get() = ServiceLocator.shareStore.receiptHeader
    override val fuzzySearch: Boolean get() = ServiceLocator.appPrefs.fFuzzySearch

    override fun repeatLabel(doc: LedgerDoc): Int = when {
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
        val c = ServiceLocator.share.currentClient()
        val (ot, opts) = options(ctx, c?.storeId.orEmpty())
        val deal = if (doc.kind == EntryKind.RETURN) DealMode.RETURN else DealMode.SALE
        return RepeatPlan(
            title = ctx.getString(if (deal == DealMode.RETURN) R.string.lgh_repeat_return else R.string.lgh_repeat_sale) + " · " + Stamp.formatDate(doc.stamp),
            lines = lines(src), showPriceType = true, defaultWholesale = ServiceLocator.draft.state.value.priceType == PriceType.WHOLESALE,
            showNote = false, confirmLabel = R.string.lgh_to_cart, hint = ctx.getString(R.string.lgh_repeat_hint, c?.name.orEmpty()),
            tag = Tag(deal, doc.hasUsd && !doc.hasSom, null, c?.id.orEmpty()), options = opts, optionsTitle = ot,
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
        val c: ShareClient = ServiceLocator.shareStore.client(tag.clientId) ?: throw IllegalStateException(ctx.getString(R.string.cl_need_store, ""))
        val st = c.storeId.takeIf { it.isNotEmpty() }?.let { ServiceLocator.catalog.storeById(it) }
            ?: throw IllegalStateException(ctx.getString(R.string.cl_need_store, c.name))
        val r = ServiceLocator.repeatSale.repeatItems(
            st.id, st.name, tag.deal, if (wholesale) PriceType.WHOLESALE else PriceType.CASH,
            if (tag.usd) Currency.USD else Currency.SOM, lines.map { RepeatSale.Item(it.productId, it.qty) }, modeFor(ctx, st.id, option),
        )
        if (r.added == 0) throw IllegalStateException(ctx.getString(R.string.repeat_nothing))
        tag.orderId?.let { id -> runCatching { ServiceLocator.share.setOrderStatus(c.id, id, OrderRequest.Status.TAKEN) } }
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
