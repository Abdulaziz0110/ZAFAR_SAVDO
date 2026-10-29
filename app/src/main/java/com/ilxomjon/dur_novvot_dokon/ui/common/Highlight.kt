package com.ilxomjon.dur_novvot_dokon.ui.common

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.google.android.material.color.MaterialColors

/** Qidiruvda topilgan qismlarni qalin va asosiy rangda ko'rsatadi. */
object Highlight {
    fun apply(text: String, ranges: List<IntRange>?, ctx: Context): CharSequence {
        if (ranges.isNullOrEmpty()) return text
        val color = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, 0)
        val s = SpannableString(text)
        for (r in ranges) {
            val a = r.first.coerceIn(0, text.length)
            val b = (r.last + 1).coerceIn(a, text.length)
            if (a >= b) continue
            s.setSpan(StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (color != 0) s.setSpan(ForegroundColorSpan(color), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }
}
