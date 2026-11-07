package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import com.ilxomjon.dur_novvot_dokon.ui.widget.QuickWidget
import uz.imsoft.shared.android.Ui

/**
 * v5.0.4: vidjetdagi 6 ta tugmaning har biri nima qilishini shu yerda tanlanadi.
 * Tez do'konlar ham tanlanadi — tugma bosilsa darhol shu do'kon bilan savdo ochiladi.
 */
class WidgetFragment : CodeFragment() {
    private lateinit var box: LinearLayout

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.wgs_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.wgs_hint), 4))
        box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(box)
        c.addView(
            Ui.button(ctx, getString(R.string.wgs_reset), outlined = true, top = 16) {
                ServiceLocator.appPrefs.widgetSlots = QuickWidget.DEFAULTS
                QuickWidget.refresh(ctx)
                render()
            },
        )
        render()
        return p.root
    }

    private fun render() {
        box.removeAllViews()
        val prefs = ServiceLocator.appPrefs
        val keys = prefs.widgetSlots
        val (card, col) = Ui.card(ctx)
        for (slot in 0 until QuickWidget.SLOTS) {
            if (slot > 0) col.addView(Ui.divider(ctx))
            val key = keys.getOrNull(slot).orEmpty()
            val store = if (key.startsWith(QuickWidget.STORE_PREFIX)) ServiceLocator.catalog.storeById(key.removePrefix(QuickWidget.STORE_PREFIX)) else null
            val act = QuickWidget.actionOf(key)
            val name = store?.name ?: act?.let { getString(it.title) } ?: getString(R.string.wg_fav_empty)
            val icon = if (store != null) R.drawable.ic_store else act?.icon ?: R.drawable.ic_cart
            col.addView(
                Ui.item(ctx, getString(R.string.wgs_slot, slot + 1), name, icon = icon, tone = if (slot == 0) 1 else 0) {
                    pick(slot)
                },
            )
        }
        box.addView(card)
    }

    /** Amal yoki tez do'kon tanlash. */
    private fun pick(slot: Int) {
        val prefs = ServiceLocator.appPrefs
        val rows = ArrayList<Ui.MenuRow>()
        for (a in QuickWidget.available()) {
            rows += Ui.MenuRow(getString(a.title), "", a.icon) { set(slot, a.key) }
        }
        for (id in prefs.favoriteStoreIds) {
            val st = ServiceLocator.catalog.storeById(id) ?: continue
            rows += Ui.MenuRow(st.name, getString(R.string.wgs_store_sub), R.drawable.ic_store) { set(slot, QuickWidget.STORE_PREFIX + id) }
        }
        Ui.menuSheet(ctx, getString(R.string.wgs_slot, slot + 1), rows)
    }

    private fun set(slot: Int, key: String) {
        val prefs = ServiceLocator.appPrefs
        val list = ArrayList(prefs.widgetSlots)
        while (list.size < QuickWidget.SLOTS) list.add("")
        list[slot] = key
        prefs.widgetSlots = list
        QuickWidget.refresh(ctx)
        render()
    }
}
