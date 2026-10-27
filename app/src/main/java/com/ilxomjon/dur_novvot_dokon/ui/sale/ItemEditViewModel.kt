package com.ilxomjon.dur_novvot_dokon.ui.sale

import androidx.lifecycle.ViewModel
import com.ilxomjon.dur_novvot_dokon.data.repo.CatalogRepository
import com.ilxomjon.dur_novvot_dokon.data.repo.DraftRepository
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.Product

/** Bitta tovar qatorini tahrirlash: miqdor, blok va narx. Narx hamma savdo turida erkin o'zgartiriladi. */
class ItemEditViewModel(
    productId: String,
    catalog: CatalogRepository,
    private val draft: DraftRepository,
) : ViewModel() {
    val product: Product? = catalog.state.value.productsById[productId]
    private val d = draft.state.value
    private val existing = d.lineFor(productId)
    private val rate = catalog.state.value.rate

    val currency: Currency = d.currency
    val exists: Boolean = existing != null
    val initialQty: Double = existing?.qty ?: 1.0
    val initialPrice: Double = existing?.price
        ?: product?.let { Pricing.unitPrice(it, d.priceType, d.currency, rate) }
        ?: 0.0
    val costPrice: Double = product?.let { Pricing.costPrice(it, d.currency, rate) } ?: 0.0

    fun save(qty: Double, price: Double) {
        product?.let { draft.setLine(it, qty, price) }
    }

    fun remove() {
        product?.let { draft.removeLine(it.id) }
    }
}
