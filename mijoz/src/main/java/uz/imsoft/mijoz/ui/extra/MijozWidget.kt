package uz.imsoft.mijoz.ui.extra

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.money

/**
 * v5.0.4 vidjet: tepada mijoz nomi va qarzi, pastda 6 ta tugma (2 qator x 3 ta).
 * Har tugmaning amali ilovadan tanlanadi (Sozlamalar ▸ Vidjet). Ranglar ilova mavzusidan.
 */
class MijozWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Locator.prefs(context)
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    /** Vidjetdagi bitta amal. */
    class Act(val key: String, val title: Int, val icon: Int)

    companion object {
        const val SLOTS = 6

        val ACTIONS = listOf(
            Act("sale", R.string.ms_new_sale, R.drawable.ic_cart),
            Act("prices", R.string.nav_prices, R.drawable.ic_price),
            Act("stats", R.string.ms_tile_stats, R.drawable.ic_trending),
            Act("history", R.string.ms_tile_history, R.drawable.ic_history),
            Act("report", R.string.ms_tile_report, R.drawable.ic_report),
            Act("pay", R.string.ms_tile_pay, R.drawable.ic_wallet),
            Act("myreport", R.string.nav_report, R.drawable.ic_person),
            Act("settings", R.string.nav_settings, R.drawable.ic_settings),
        )

        val DEFAULTS = listOf("sale", "prices", "stats", "history", "report", "pay")

        fun actionOf(key: String): Act? = ACTIONS.firstOrNull { it.key == key }

        private val BTN = listOf(R.id.wBtn0, R.id.wBtn1, R.id.wBtn2, R.id.wBtn3, R.id.wBtn4, R.id.wBtn5)
        private val ICON = listOf(R.id.wIcon0, R.id.wIcon1, R.id.wIcon2, R.id.wIcon3, R.id.wIcon4, R.id.wIcon5)
        private val TEXT = listOf(R.id.wText0, R.id.wText1, R.id.wText2, R.id.wText3, R.id.wText4, R.id.wText5)

        private fun pi(ctx: Context, req: Int, action: String?): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (action != null) putExtra(MainActivity.EXTRA_ACTION, action)
            }
            return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_quick)
            val snap = runCatching { Locator.data.state.value.snapshot }.getOrNull()
            v.setTextViewText(R.id.wTitle, snap?.clientName?.ifBlank { null } ?: ctx.getString(R.string.app_name))
            val debt = snap?.debt?.let { uz.imsoft.shared.report.Money2(it.som, it.usd) } ?: snap?.reportBalance?.second
            if (debt != null) {
                val parts = ArrayList<String>()
                if (debt.som != 0.0) parts += ctx.money(debt.som, false)
                if (debt.usd != 0.0) parts += ctx.money(debt.usd, true)
                v.setTextViewText(R.id.wInfo, if (parts.isEmpty()) ctx.money(0.0, false) else parts.joinToString(" + "))
                v.setViewVisibility(R.id.wInfo, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.wInfo, View.GONE)
            }
            // Vidjetda mavzu atributlari ishlamaydi — rang to'g'ridan-to'g'ri beriladi
            val cAccent = androidx.core.content.ContextCompat.getColor(ctx, R.color.brand_on_primary_container)
            val cIcon = androidx.core.content.ContextCompat.getColor(ctx, R.color.brand_primary)
            val cText = androidx.core.content.ContextCompat.getColor(ctx, R.color.brand_on_surface)
            val keys = runCatching { Locator.prefs(ctx).widgetSlots }.getOrDefault(DEFAULTS)
            for (slot in 0 until SLOTS) {
                val act = actionOf(keys.getOrNull(slot).orEmpty())
                v.setTextViewText(TEXT[slot], act?.let { ctx.getString(it.title) } ?: ctx.getString(R.string.wg_no_data))
                v.setImageViewResource(ICON[slot], act?.icon ?: R.drawable.ic_cart)
                v.setInt(BTN[slot], "setBackgroundResource", if (slot == 0) R.drawable.widget_btn_accent else R.drawable.widget_btn)
                v.setInt(ICON[slot], "setColorFilter", if (slot == 0) cAccent else cIcon)
                v.setTextColor(TEXT[slot], if (slot == 0) cAccent else cText)
                v.setOnClickPendingIntent(BTN[slot], pi(ctx, 200 + slot, act?.key))
            }
            v.setOnClickPendingIntent(R.id.wTitle, pi(ctx, 210, null))
            v.setOnClickPendingIntent(R.id.wInfo, pi(ctx, 211, null))
            return v
        }

        fun refresh(ctx: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, MijozWidget::class.java))
                if (ids.isNotEmpty()) m.updateAppWidget(ids, views(ctx))
            }
        }
    }
}
