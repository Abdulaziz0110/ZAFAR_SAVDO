package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.databinding.ItemProductBinding
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.common.Highlight

/**
 * Tezkor ro'yxat adapteri. Narx har qatorda KERAK BO'LGANDA hisoblanadi (minglab qatorni oldindan hisoblamaymiz),
 * savatcha o'zgarganda esa faqat o'zgargan qatorlar yangilanadi.
 */
class ProductAdapter(
    private val onPlus: (Product, Double, Double) -> Unit,
    private val onMinus: (Product, Double) -> Unit,
    private val onEdit: (Product, Boolean) -> Unit,
    /** "−" uzoq bosilsa: tovar savatchadan butunlay olinadi (nol). */
    private val onZero: (Product) -> Unit = {},
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    private var products: List<Product> = emptyList()
    private var positions: HashMap<String, Int> = HashMap()
    private var lines: Map<String, CartLine> = emptyMap()
    private var ctx = PricingCtx(com.ilxomjon.dur_novvot_dokon.domain.PriceType.WHOLESALE, Currency.SOM, 0.0)
    private var showCost = false
    private var suggested: Map<String, Double> = emptyMap()
    private var explanations: Map<String, String> = emptyMap()
    /** Har doim ko'rinadigan izoh (masalan narx yorlig'ida "PLU 501 · shtrix kod bor"). */
    private var notes: Map<String, String> = emptyMap()
    private var highlights: Map<String, List<IntRange>> = emptyMap()
    /** Sozlamalar ▸ Qulayliklar ▸ Tebranish. */
    var haptics: Boolean = true
    /** v2.7: har tovar ostida "Kod 2555 · PLU 501" (PLU — kompyuterdan 1C'dan yoki qo'lda). */
    var showCodes: Boolean = true
    var pluOf: (String) -> String? = { id -> com.ilxomjon.dur_novvot_dokon.core.ServiceLocator.labels.pluFor(id) }
    /** Narx yorliqlari: 1C narx turi tanlangan bo'lsa — ro'yxatdagi narx shundan (null — odatdagi hisob). */
    var priceOverride: ((Product) -> Double?)? = null

    fun setNotes(map: Map<String, String>) {
        if (map == notes) return
        notes = map
        notifyItemRangeChanged(0, itemCount, PAYLOAD_CART)
    }

    /** Qidiruvda mos kelgan qismlar (nom ichida qalin). [setProducts] dan oldin chaqiriladi. */
    fun setHighlights(map: Map<String, List<IntRange>>) { highlights = map }

    /** Taklif izohlari ("Odatda ~7 kunda ~10 · oxirgisi 8 kun oldin"). Bo'sh bo'lsa — eski "Odatda: N". */
    fun setExplanations(map: Map<String, String>) {
        if (map == explanations) return
        explanations = map
        notifyDataSetChanged()
    }

    fun setProducts(list: List<Product>) {
        products = list
        val map = HashMap<String, Int>(list.size * 2)
        for (i in list.indices) map[list[i].id] = i
        positions = map
        notifyDataSetChanged()
    }

    fun setCart(newLines: Map<String, CartLine>) {
        val old = lines
        lines = newLines
        for ((id, l) in newLines) if (old[id] != l) positions[id]?.let { notifyItemChanged(it, PAYLOAD_CART) }
        for (id in old.keys) if (id !in newLines) positions[id]?.let { notifyItemChanged(it, PAYLOAD_CART) }
    }

    fun setSuggested(map: Map<String, Double>) {
        if (map == suggested) return
        suggested = map
        notifyDataSetChanged()
    }

    fun setPricing(newCtx: PricingCtx, showCost: Boolean) {
        if (newCtx == ctx && showCost == this.showCost) return
        ctx = newCtx
        this.showCost = showCost
        notifyDataSetChanged()
    }

    /** Savatchada yo'q tovar birinchi bosishda mijozning odatiy miqdorida qo'shiladi (bo'lmasa 1). */
    private fun firstAmount(p: Product): Double = if (lines[p.id] == null) (suggested[p.id] ?: 1.0) else 1.0

    private fun priceOf(p: Product): Double =
        lines[p.id]?.price ?: Pricing.unitPrice(p, ctx.level, ctx.currency, ctx.rate)

    override fun getItemCount() = products.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(products[position], full = true)

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) holder.bind(products[position], full = true)
        else holder.bind(products[position], full = false)
    }

    inner class VH(private val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.btnPlus.setOnClickListener { v ->
                current()?.let { if (haptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onPlus(it, priceOf(it), firstAmount(it)) }
            }
            // Uzoq bosish: bitta blok (yoki 10 ta) birdan qo'shiladi.
            b.btnPlus.setOnLongClickListener { v ->
                val p = current() ?: return@setOnLongClickListener false
                if (haptics) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onPlus(p, priceOf(p), if (p.hasBlock) p.blockSize else 10.0)
                true
            }
            b.btnMinus.setOnClickListener { current()?.let { onMinus(it, priceOf(it)) } }
            b.btnMinus.setOnLongClickListener { v ->
                val p = current() ?: return@setOnLongClickListener false
                if (haptics) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onZero(p)
                true
            }
            b.tvQty.setOnClickListener { current()?.let { onEdit(it, false) } }
            b.tvPrice.setOnClickListener { current()?.let { onEdit(it, true) } }
            b.root.setOnClickListener { current()?.let { onEdit(it, false) } }
        }

        private fun current(): Product? = products.getOrNull(bindingAdapterPosition)

        fun bind(p: Product, full: Boolean) {
            val ctxt = b.root.context
            val line = lines[p.id]
            if (full) b.tvName.text = Highlight.apply(p.name, highlights[p.id], ctxt)
            val parts = ArrayList<String>(3)
            if (showCodes) {
                val plu = pluOf(p.id)
                parts += if (plu != null) ctxt.getString(R.string.meta_code_plu, p.id, plu) else ctxt.getString(R.string.meta_code, p.id)
            }
            notes[p.id]?.let { parts += it }
            if (line == null) {
                val ex = explanations[p.id]
                if (ex != null) parts += ex else suggested[p.id]?.let { parts += ctxt.getString(R.string.meta_usual, Money.qtyDisplay(it)) }
            }
            if (showCost) parts += ctxt.getString(R.string.meta_cost, ctxt.money(Pricing.costPrice(p, ctx.currency, ctx.rate), ctx.currency))
            if (parts.size <= (if (showCodes) 1 else 0) && p.hasBlock) parts += ctxt.getString(R.string.block_info, Money.qtyDisplay(p.blockSize))
            val meta = parts.joinToString(" · ")
            b.tvMeta.text = meta
            b.tvMeta.show(meta.isNotEmpty())
            b.tvPrice.text = ctxt.money(line?.price ?: priceOverride?.invoke(p) ?: Pricing.unitPrice(p, ctx.level, ctx.currency, ctx.rate), ctx.currency)
            val inCart = line != null
            // Savatchadagi tovar yengil fon bilan ajralib turadi
            if (inCart) {
                val c = com.google.android.material.color.MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorSecondaryContainer)
                b.root.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(c, 140))
            } else b.root.background = null
            b.btnMinus.show(inCart)
            b.tvQty.show(inCart)
            if (line != null) b.tvQty.text = Money.qtyDisplay(line.qty)
        }
    }

    private companion object {
        const val PAYLOAD_CART = "cart"
    }
}
