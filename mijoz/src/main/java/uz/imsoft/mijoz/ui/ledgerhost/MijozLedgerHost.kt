package uz.imsoft.mijoz.ui.ledgerhost

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.printer.UsbPrinter
import uz.imsoft.mijoz.ui.ledger.LedgerData
import uz.imsoft.mijoz.ui.ledger.LedgerHost
import uz.imsoft.mijoz.ui.ledger.MatchStatus
import uz.imsoft.mijoz.ui.ledger.PrintTarget
import uz.imsoft.mijoz.ui.ledger.RepeatLine
import uz.imsoft.mijoz.ui.ledger.RepeatPlan
import uz.imsoft.shared.OrderLine
import uz.imsoft.shared.report.ClientSnapshot
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.Stamp

/**
 * 2-ilova uchun 1C tarixi amallari: "Qayta buyurtma" (1-ilovaga so'rov — savdo EMAS), chek -> USB printer.
 */
object MijozLedgerHost : LedgerHost {
    override val ledgerData: StateFlow<LedgerData> by lazy {
        Locator.data.state.map { s ->
            val snap = s.snapshot
            LedgerData(snap?.ledger.orEmpty(), snap?.clientName ?: Locator.prefs.link?.clientName.orEmpty(), s.publishedAt * 31 + (snap?.createdAt ?: 0L), s.loaded)
        }.stateIn(Locator.scope, SharingStarted.Eagerly, LedgerData(emptyList(), "", -1L, false))
    }

    override val receiptHeader: String get() = Locator.data.state.value.snapshot?.shopName.orEmpty()
    /** v3.2: chek osti (telefon) — 2-filial o'zinikini yozsa shu, aks holda do'kon egasiniki. Do'kon nomi o'zgarmaydi. */
    override val receiptFooter: String get() = Locator.prefs.receiptFooter.trim().ifEmpty { Locator.data.state.value.snapshot?.shopFooter.orEmpty() }

    /** v5.2 (A3): 1C chekidagi "Agent" maydoni. */
    override val receiptStaff: String get() = Locator.prefs.receiptStaff
    override val receiptStyle: uz.imsoft.shared.receipt.ReceiptStyle
        get() {
            val base = uz.imsoft.shared.receipt.ReceiptStyle.STANDARD
            val p = Locator.prefs
            val sub = p.receiptSub.trim()
            val thanks = p.receiptThanks.trim()
            if (sub.isEmpty() && thanks.isEmpty()) return base
            return base.copy(
                sub = if (sub.isEmpty()) base.sub else listOf(sub, base.sub).filter { it.isNotBlank() }.joinToString("\n"),
                thanks = thanks.ifEmpty { base.thanks },
            ).normalized()
        }
    override val receiptLogo: android.graphics.Bitmap? get() = uz.imsoft.shared.android.LogoCache.fromBase64(Locator.data.state.value.snapshot?.receiptLogo)
    override val fuzzySearch: Boolean get() = Locator.prefs.fuzzy

    fun ordersEnabled() = Locator.data.state.value.snapshot?.features?.contains(ClientSnapshot.FEATURE_ORDERS) == true

    override fun repeatLabel(doc: LedgerDoc): Int = if (doc.kind == EntryKind.SALE && ordersEnabled()) R.string.lgm_reorder else 0

    override suspend fun prepareRepeat(ctx: Context, doc: LedgerDoc): RepeatPlan {
        val ids = Locator.data.state.value.snapshot?.products.orEmpty().associate { LedgerMerge.nameKey(it.name) to it.id }
        val merged = LinkedHashMap<String, RepeatLine>()
        for (e in doc.goods) {
            val k = LedgerMerge.nameKey(e.product)
            val cur = merged[k]
            if (cur != null) cur.qty += e.qty
            else merged[k] = RepeatLine(e.product, e.qty, ids[k].orEmpty(), MatchStatus.UNCHECKED, "", checked = true)
        }
        return RepeatPlan(
            title = ctx.getString(R.string.lgm_reorder) + " · " + Stamp.formatDate(doc.stamp),
            lines = merged.values.toList(), showPriceType = false, defaultWholesale = true, showNote = true,
            confirmLabel = R.string.lgm_send, hint = ctx.getString(R.string.lgm_reorder_hint), tag = doc.key,
        )
    }

    override suspend fun submitRepeat(ctx: Context, plan: RepeatPlan, lines: List<RepeatLine>, wholesale: Boolean, note: String, option: Int): String {
        val src = plan.tag as? String ?: ""
        Locator.data.sendOrder(note, src, lines.map { OrderLine(it.name, it.qty, it.productId) })
        if (src == CART) Locator.cart.clear()
        return ctx.getString(R.string.lgm_sent)
    }

    const val CART = "cart"

    /** "Narxlar" dan yig'ilgan savatcha -> buyurtma oynasi (miqdorni tekshirish/o'zgartirish, izoh). */
    fun planForCart(ctx: Context): RepeatPlan = RepeatPlan(
        title = ctx.getString(R.string.cart_title),
        lines = Locator.cart.items.value.map { RepeatLine(it.name, it.qty, it.productId, MatchStatus.UNCHECKED, "", checked = true) },
        showPriceType = false, defaultWholesale = true, showNote = true,
        confirmLabel = R.string.lgm_send, hint = ctx.getString(R.string.cart_hint), tag = CART,
    )

    /** v3.2: savdo chekini tuzatishni so'rash (Admin tasdiqlaydi). */
    override fun editLabel(doc: LedgerDoc): Int = if (doc.kind == EntryKind.SALE && Locator.prefs.link != null) R.string.ed_request else 0

    override fun requestEdit(f: androidx.fragment.app.Fragment, doc: LedgerDoc) = uz.imsoft.mijoz.ui.extra.RequestsFragment.askEdit(f, doc)

    override fun printTargets(ctx: Context): List<PrintTarget> {
        if (!Locator.prefs.printerSection) return emptyList()
        return listOf(PrintTarget(ctx.getString(R.string.lgm_usb_printer)) { bytes ->
            withContext(Dispatchers.IO) { UsbPrinter.send(ctx.applicationContext, bytes) }
            ctx.getString(R.string.lgm_printed)
        })
    }
}
