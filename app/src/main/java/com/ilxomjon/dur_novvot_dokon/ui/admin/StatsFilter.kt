package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.content.Context
import android.widget.LinearLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Stats

/**
 * v5.0.3: statistikani mijoz bo'yicha ajratish. Do'kondagi tovarlarning katta qismini bitta mijoz olgani uchun
 * ("Dadaxon aka" kabi) hamma ko'rsatkichni UNSIZ ham ko'rish kerak: Hammasi / Faqat shu / Shusiz.
 * Tanlov saqlanadi — ekranlar o'rtasida bir xil bo'ladi.
 */
object StatsFilter {
    private fun prefs() = ServiceLocator.appPrefs

    val who: Stats.Who
        get() = when (prefs().statsWho) {
            "only" -> Stats.Who.ONLY
            "without" -> Stats.Who.WITHOUT
            else -> Stats.Who.ALL
        }

    var client: String
        get() = prefs().statsClient
        set(v) { prefs().statsClient = v }

    /** Mijoz tanlanmagan bo'lsa — 2-ilovadagi mijoz (odatda do'konning eng katta xaridori). */
    fun defaultClient(): String {
        val cur = prefs().statsClient
        if (cur.isNotEmpty()) return cur
        val c = ServiceLocator.shareStore.clients().firstOrNull { it.storeId.isNotEmpty() }?.name.orEmpty()
        if (c.isNotEmpty()) prefs().statsClient = c
        return c
    }

    fun apply(entries: List<LedgerEntry>): List<LedgerEntry> = Stats.only(entries, prefs().statsClient, who)

    /**
     * Filtr qatori: mijoz nomi (bosilsa ro'yxatdan tanlanadi) va uch tanlov.
     * [onChange] — tanlov o'zgarganda ekran qayta chiziladi.
     */
    fun view(ctx: Context, names: () -> List<String>, onChange: () -> Unit): LinearLayout {
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun draw() {
            box.removeAllViews()
            val name = defaultClient()
            box.addView(
                Ui.item(ctx, ctx.getString(R.string.stt_client, name.ifEmpty { ctx.getString(R.string.stt_client_none) }), "", icon = R.drawable.ic_person) {
                    val list = names()
                    if (list.isEmpty()) return@item
                    Ui.menuSheet(
                        ctx, ctx.getString(R.string.stt_client_pick),
                        list.map { n -> Ui.MenuRow(n, "", R.drawable.ic_person, if (n.equals(name, true)) 1 else 0) { client = n; draw(); onChange() } },
                    )
                },
            )
            box.addView(
                Ui.toggle(
                    ctx,
                    listOf(ctx.getString(R.string.stt_who_all), ctx.getString(R.string.stt_who_only), ctx.getString(R.string.stt_who_without)),
                    when (prefs().statsWho) { "only" -> 1; "without" -> 2; else -> 0 }, 4,
                ) { i ->
                    prefs().statsWho = when (i) { 1 -> "only"; 2 -> "without"; else -> "all" }
                    onChange()
                },
            )
        }
        draw()
        return box
    }
}
