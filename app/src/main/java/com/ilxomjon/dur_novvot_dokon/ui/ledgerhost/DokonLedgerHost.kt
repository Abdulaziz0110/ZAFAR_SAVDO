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
import uz.imsoft.shared.OrderLine
import uz.imsoft.shared.OrderRequest
import uz.imsoft.shared.report.CatalogMatch
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.Stamp

/**
 * 1-ilova uchun 1C tarixi amallari: takrorlash -> savatcha (narx JORIY katalogdan, odatdagi qoidalar bilan),
 * chek -> 2-filial printeri (bulut navbati), 2-ilova buyurtmasini savatchaga olish.
 */
object DokonLedgerHost : LedgerHost {
    /** Takrorlash manbai. */
    class Tag(val deal: DealMode, val usd: Boolean, val orderId: String?)

    override val ledgerData: StateFlow<LedgerData> by lazy {
        ServiceLocator.share.ledger.map { LedgerData(it.entries, it.clientName, it.version, it.loaded) }
            .stateIn(ServiceLocator.appScope, SharingStarted.Eagerly, LedgerData(emptyList(), "", -1L, false))
    }

    override val receiptHeader: String get() = ServiceLocator.shareStore.receiptHeader
    override val fuzzySearch: Boolean get() = ServiceLocator.appPrefs.fFuzzySearch

    override fun repeatLabel(doc: LedgerDoc): Int = when {
        ServiceLocator.shareStore.clientId.isEmpty() -> 0
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

    private fun options(ctx: Context): Pair<String, List<String>> {
        val d = ServiceLocator.draft.state.value
        if (d.lines.isEmpty()) return "" to emptyList()
        val o = ArrayList<String>()
        o += ctx.getString(R.string.repeat_replace, d.lines.size)
        if (d.store?.id == ServiceLocator.shareStore.clientId) o += ctx.getString(R.string.repeat_add)
        if (ServiceLocator.appPrefs.fMultiCart) o += ctx.getString(R.string.repeat_park)
        return ctx.getString(R.string.repeat_title) to o
    }

    private fun modeFor(ctx: Context, option: Int): RepeatSale.Mode {
        val (_, opts) = options(ctx)
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
        val (ot, opts) = options(ctx)
        val deal = if (doc.kind == EntryKind.RETURN) DealMode.RETURN else DealMode.SALE
        return RepeatPlan(
            title = ctx.getString(if (deal == DealMode.RETURN) R.string.lgh_repeat_return else R.string.lgh_repeat_sale) + " · " + Stamp.formatDate(doc.stamp),
            lines = lines(src), showPriceType = true, defaultWholesale = ServiceLocator.draft.state.value.priceType == PriceType.WHOLESALE,
            showNote = false, confirmLabel = R.string.lgh_to_cart, hint = ctx.getString(R.string.lgh_repeat_hint, ServiceLocator.shareStore.clientName),
            tag = Tag(deal, doc.hasUsd && !doc.hasSom, null), options = opts, optionsTitle = ot,
        )
    }

    fun planForOrder(ctx: Context, o: OrderRequest): RepeatPlan {
        val (ot, opts) = options(ctx)
        val hint = listOfNotNull(
            ctx.getString(R.string.lgh_order_hint, ServiceLocator.shareStore.clientName),
            o.note.takeIf { it.isNotBlank() }?.let { ctx.getString(R.string.ord_note, it) },
        ).joinToString("\n")
        return RepeatPlan(
            title = ctx.getString(R.string.ord_item_title, java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US).format(java.util.Date(o.at))),
            lines = lines(o.lines), showPriceType = true, defaultWholesale = true, showNote = false, confirmLabel = R.string.lgh_to_cart,
            hint = hint, tag = Tag(DealMode.SALE, false, o.id), options = opts, optionsTitle = ot,
        )
    }

    override suspend fun submitRepeat(ctx: Context, plan: RepeatPlan, lines: List<RepeatLine>, wholesale: Boolean, note: String, option: Int): String {
        val tag = plan.tag as? Tag ?: Tag(DealMode.SALE, false, null)
        val store = ServiceLocator.shareStore
        val r = ServiceLocator.repeatSale.repeatItems(
            store.clientId, store.clientName, tag.deal, if (wholesale) PriceType.WHOLESALE else PriceType.CASH,
            if (tag.usd) Currency.USD else Currency.SOM, lines.map { RepeatSale.Item(it.productId, it.qty) }, modeFor(ctx, option),
        )
        if (r.added == 0) throw IllegalStateException(ctx.getString(R.string.repeat_nothing))
        tag.orderId?.let { id -> runCatching { ServiceLocator.share.setOrderStatus(id, OrderRequest.Status.TAKEN) } }
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
        if (!s.isConfigured || s.queueKey.isEmpty()) return emptyList()
        return listOf(PrintTarget(ctx.getString(R.string.lgh_branch_printer)) { bytes ->
            ServiceLocator.share.sendToBranchPrinter(bytes)
            ctx.getString(R.string.lgh_branch_sent)
        })
    }
}
