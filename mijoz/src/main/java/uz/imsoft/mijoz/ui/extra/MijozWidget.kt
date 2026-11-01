package uz.imsoft.mijoz.ui.extra

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.money

/** v3.2 vidjet: qarz (oxirgi ma'lumot), "Narxlar" va "Kassa". */
class MijozWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Locator.prefs(context)
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    companion object {
        private fun pi(ctx: Context, req: Int, tab: String?): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (tab != null) putExtra(MainActivity.EXTRA_TAB, tab)
            }
            return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun views(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_mijoz)
            val snap = runCatching { Locator.data.state.value.snapshot }.getOrNull()
            v.setTextViewText(R.id.wName, snap?.clientName?.ifBlank { null } ?: ctx.getString(R.string.app_name))
            val debt = snap?.debt?.let { uz.imsoft.shared.report.Money2(it.som, it.usd) } ?: snap?.reportBalance?.second
            v.setTextViewText(R.id.wDebt, when {
                debt != null -> {
                    val parts = ArrayList<String>()
                    if (debt.som != 0.0) parts += ctx.money(debt.som, false)
                    if (debt.usd != 0.0) parts += ctx.money(debt.usd, true)
                    ctx.getString(R.string.wg_debt, if (parts.isEmpty()) ctx.money(0.0, false) else parts.joinToString(" + "))
                }
                else -> ctx.getString(R.string.wg_no_data)
            })
            v.setOnClickPendingIntent(R.id.wRoot, pi(ctx, 200, null))
            v.setOnClickPendingIntent(R.id.wDebt, pi(ctx, 200, null))
            v.setOnClickPendingIntent(R.id.wPrices, pi(ctx, 201, MainActivity.TAB_PRICES))
            v.setOnClickPendingIntent(R.id.wPos, pi(ctx, 202, MainActivity.TAB_POS))
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
