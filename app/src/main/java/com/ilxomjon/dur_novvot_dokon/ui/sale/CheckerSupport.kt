package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.domain.CartChecker
import com.ilxomjon.dur_novvot_dokon.domain.CartWarning
import com.ilxomjon.dur_novvot_dokon.domain.DraftState
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.Suggestion
import com.ilxomjon.dur_novvot_dokon.domain.Suggestions
import com.ilxomjon.dur_novvot_dokon.ui.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Savatcha tekshiruvchisi uchun ma'lumot yig'ish va matn. "Shunday qoldir" bosilganlar shu savatchada qayta chiqmaydi. */
object CheckerSupport {
    /** Savatcha davomida yashirilgan ogohlantirishlar (do'kon almashganda tozalanadi). */
    private val dismissed = HashSet<String>()
    private var dismissedFor = ""

    @Synchronized fun dismissedFor(storeId: String): Set<String> {
        if (storeId != dismissedFor) { dismissed.clear(); dismissedFor = storeId }
        return HashSet(dismissed)
    }

    @Synchronized fun dismiss(keys: Collection<String>) { dismissed.addAll(keys) }

    class Ctx(val orders: List<SentOrder>, val suggestions: List<Suggestion>)

    suspend fun load(storeId: String): Ctx {
        val h = ServiceLocator.history
        val orders = h.storeOrders(storeId)
        val sug = withContext(Dispatchers.Default) { Suggestions.build(orders, h.recentSaleOrders(), System.currentTimeMillis(), feedback = h.feedback(storeId)) }
        return Ctx(orders, sug)
    }

    fun warnings(d: DraftState, c: Ctx?, includeMissing: Boolean): List<CartWarning> {
        val store = d.store ?: return emptyList()
        if (c == null || !ServiceLocator.appPrefs.fCartChecker) return emptyList()
        val names = ServiceLocator.catalog.state.value.productsById.mapValues { it.value.name }
        return CartChecker.check(d.lines, d.currency, d.mode, c.orders, c.suggestions, names, dismissedFor(store.id), includeMissing)
    }

    fun text(ctx: Context, d: DraftState, list: List<CartWarning>): String = list.joinToString("\n") { w ->
        when (w) {
            is CartWarning.BigQty -> ctx.getString(R.string.check_big_qty, w.name, Money.qtyDisplay(w.qty), Money.qtyDisplay(w.usual))
            is CartWarning.PriceDiff -> ctx.getString(R.string.check_price, w.name, ctx.money(w.price, d.currency), ctx.money(w.last, d.currency))
            is CartWarning.MissingUsual -> ctx.getString(R.string.check_missing, w.names.joinToString(", "))
        }
    }
}
