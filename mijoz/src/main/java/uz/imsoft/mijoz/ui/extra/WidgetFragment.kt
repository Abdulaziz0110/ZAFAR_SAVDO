package uz.imsoft.mijoz.ui.extra

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.shared.android.Ui

/** v5.0.4: vidjetdagi 6 ta tugmaning har biri nima qilishini shu yerda tanlanadi. */
class WidgetFragment : MFragment() {
    private lateinit var box: LinearLayout

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.wgs_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.wgs_hint), 4))
        box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(box)
        c.addView(
            Ui.button(ctx, getString(R.string.wgs_reset), outlined = true, top = 16) {
                Locator.prefs.widgetSlots = MijozWidget.DEFAULTS
                MijozWidget.refresh(ctx.applicationContext)
                render()
            },
        )
        render()
        return p.root
    }

    private fun render() {
        box.removeAllViews()
        val keys = Locator.prefs.widgetSlots
        val (card, col) = Ui.card(ctx)
        for (slot in 0 until MijozWidget.SLOTS) {
            if (slot > 0) col.addView(Ui.divider(ctx))
            val act = MijozWidget.actionOf(keys.getOrNull(slot).orEmpty())
            col.addView(
                Ui.item(
                    ctx, getString(R.string.wgs_slot, slot + 1), act?.let { getString(it.title) } ?: getString(R.string.wg_no_data),
                    icon = act?.icon ?: R.drawable.ic_cart, tone = if (slot == 0) 1 else 0,
                ) { pick(slot) },
            )
        }
        box.addView(card)
    }

    private fun pick(slot: Int) {
        Ui.menuSheet(
            ctx, getString(R.string.wgs_slot, slot + 1),
            MijozWidget.ACTIONS.map { a -> Ui.MenuRow(getString(a.title), "", a.icon) { set(slot, a.key) } },
        )
    }

    private fun set(slot: Int, key: String) {
        val list = ArrayList(Locator.prefs.widgetSlots)
        while (list.size < MijozWidget.SLOTS) list.add("")
        list[slot] = key
        Locator.prefs.widgetSlots = list
        MijozWidget.refresh(ctx.applicationContext)
        render()
    }
}
