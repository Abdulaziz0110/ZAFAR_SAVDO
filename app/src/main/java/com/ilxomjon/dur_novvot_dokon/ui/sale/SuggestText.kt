package com.ilxomjon.dur_novvot_dokon.ui.sale

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.domain.Suggestion
import kotlin.math.roundToInt

/** Taklif izohi. Hech qachon fakt sifatida emas: tarix kam bo'lsa "taxminiy". */
object SuggestText {
    fun of(ctx: Context, s: Suggestion): String {
        val qty = Money.qtyDisplay(s.qty)
        val iv = s.intervalDays
        val since = s.daysSince
        return when {
            iv != null && since != null && s.confident ->
                ctx.getString(R.string.sug_explain, iv.roundToInt().coerceAtLeast(1), qty, since.roundToInt())
            iv != null && since != null -> ctx.getString(R.string.sug_explain_rough, iv.roundToInt().coerceAtLeast(1), qty, since.roundToInt())
            s.orders == 0 -> ctx.getString(R.string.sug_popular, qty)
            else -> ctx.getString(R.string.sug_once, qty)
        }
    }

    fun map(ctx: Context, list: List<Suggestion>): Map<String, String> = list.associate { it.productId to of(ctx, it) }
}
