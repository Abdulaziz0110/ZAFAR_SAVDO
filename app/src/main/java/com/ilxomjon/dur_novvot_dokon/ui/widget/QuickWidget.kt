package com.ilxomjon.dur_novvot_dokon.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.MainActivity

/**
 * v3.2 vidjet "Tez savdo": 2 ta sevimli do'kon (Sozlamalar ▸ Tez do'konlar, masalan "Naqd" va "Dadaxon aka") —
 * bosilsa shu do'kon bilan savdo ochiladi; "Skaner" va "To'lov".
 */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ServiceLocator.ensureInit(context.applicationContext)
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    companion object {
        const val EXTRA_STORE = "quick_store"
        const val EXTRA_ACTION = "quick_action"
        const val ACT_SCAN = "scan"
        const val ACT_PAYMENT = "payment"

        private fun pi(context: Context, req: Int, fill: Intent.() -> Unit): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                fill()
            }
            return PendingIntent.getActivity(context, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun views(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_quick)
            val favs = runCatching { ServiceLocator.appPrefs.favoriteStoreIds }.getOrDefault(emptyList())
            val ids = listOf(R.id.wFav0, R.id.wFav1)
            for ((slot, viewId) in ids.withIndex()) {
                val storeId = favs.getOrNull(slot)
                val name = storeId?.let { runCatching { ServiceLocator.catalog.storeById(it)?.name }.getOrNull() }
                v.setTextViewText(viewId, name ?: context.getString(R.string.wg_fav_empty))
                v.setOnClickPendingIntent(viewId, pi(context, 100 + slot) { if (storeId != null) putExtra(EXTRA_STORE, storeId) })
            }
            v.setOnClickPendingIntent(R.id.wScan, pi(context, 110) { putExtra(EXTRA_ACTION, ACT_SCAN) })
            v.setOnClickPendingIntent(R.id.wPay, pi(context, 111) { putExtra(EXTRA_ACTION, ACT_PAYMENT) })
            v.setOnClickPendingIntent(R.id.wTitle, pi(context, 112) { })
            return v
        }

        /** Sevimli do'konlar o'zgarganda vidjet yangilanadi. */
        fun refresh(context: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(context)
                val ids = m.getAppWidgetIds(ComponentName(context, QuickWidget::class.java))
                if (ids.isNotEmpty()) m.updateAppWidget(ids, views(context))
            }
        }
    }
}
