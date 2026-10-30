package com.ilxomjon.dur_novvot_dokon.ui.share

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R

/**
 * Qadamma-qadam yo'riqnoma (oyna ichida "Orqaga / Keyingi"). Ilovadan chiqmasdan o'qiladi;
 * do'stlarga ham shu ilova bilan o'rnatib berish oson bo'lsin.
 */
object Guide {
    private class Page(val title: Int, val text: Int)

    private val FIREBASE = listOf(
        Page(R.string.gd_fb1_t, R.string.gd_fb1),
        Page(R.string.gd_fb2_t, R.string.gd_fb2),
        Page(R.string.gd_fb3_t, R.string.gd_fb3),
        Page(R.string.gd_fb4_t, R.string.gd_fb4),
    )
    private val PC = listOf(
        Page(R.string.gd_pc1_t, R.string.gd_pc1),
        Page(R.string.gd_pc2_t, R.string.gd_pc2),
        Page(R.string.gd_pc3_t, R.string.gd_pc3),
        Page(R.string.gd_pc4_t, R.string.gd_pc4),
    )
    private val MEMBERS = listOf(
        Page(R.string.gd_mb1_t, R.string.gd_mb1),
        Page(R.string.gd_mb2_t, R.string.gd_mb2),
    )

    fun firebase(ctx: Context) = show(ctx, FIREBASE, 0)
    fun pc(ctx: Context) = show(ctx, PC, 0)
    fun members(ctx: Context) = show(ctx, MEMBERS, 0)

    private fun show(ctx: Context, pages: List<Page>, i: Int) {
        val p = pages[i]
        val d = MaterialAlertDialogBuilder(ctx)
            .setTitle(ctx.getString(R.string.gd_page, i + 1, pages.size) + " · " + ctx.getString(p.title))
            .setMessage(p.text)
        if (i + 1 < pages.size) d.setPositiveButton(R.string.lg_tour_next) { _, _ -> show(ctx, pages, i + 1) }
        else d.setPositiveButton(R.string.lg_tour_done, null)
        if (i > 0) d.setNegativeButton(R.string.back) { _, _ -> show(ctx, pages, i - 1) }
        else d.setNegativeButton(R.string.close, null)
        d.show()
    }
}
