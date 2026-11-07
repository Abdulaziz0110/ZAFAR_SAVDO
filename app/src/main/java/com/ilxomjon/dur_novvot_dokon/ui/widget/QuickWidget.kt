package com.ilxomjon.dur_novvot_dokon.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.MainActivity

/**
 * v5.0.4 vidjet: 6 ta tugma (2 qator x 3 ta). Har tugmaning amali ilovadan tanlanadi
 * (Sozlamalar ▸ Vidjet): savdo, shtrix bilan savdo, to'lov, tarix, hisobot, kun yakuni, navbat,
 * shtrix kodlar, qaytarish va Admin ishlari; yoki tez do'kon ("Do'kon: Dadaxon aka" — darhol shu
 * do'kon bilan savdo). Ranglar ilova mavzusidan olinadi, shuning uchun Admin'da qorong'i ko'rinadi.
 */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ServiceLocator.ensureInit(context.applicationContext)
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    /** Vidjetdagi bitta amal. */
    class Act(val key: String, val title: Int, val icon: Int, val admin: Boolean = false)

    companion object {
        const val EXTRA_STORE = "quick_store"
        const val EXTRA_ACTION = "quick_action"
        const val ACT_SCAN = "scan"
        const val ACT_PAYMENT = "payment"

        /** Do'kon tugmasi kaliti: "store:<id>". */
        const val STORE_PREFIX = "store:"

        const val SLOTS = 6

        val ACTIONS = listOf(
            Act("sale", R.string.home_new_sale, R.drawable.ic_cart),
            Act(ACT_SCAN, R.string.home_scan_sale, R.drawable.ic_scan),
            Act(ACT_PAYMENT, R.string.home_payment, R.drawable.ic_payment),
            Act("history", R.string.nav_history, R.drawable.ic_history),
            Act("report", R.string.home_report, R.drawable.ic_report),
            Act("dayend", R.string.dayend_title, R.drawable.ic_report),
            Act("queue", R.string.queue_title, R.drawable.ic_sync),
            Act("barcodes", R.string.bb_title, R.drawable.ic_barcode),
            Act("return", R.string.home_return, R.drawable.ic_return),
            Act("stats", R.string.stt_title, R.drawable.ic_trending, admin = true),
            Act("clients", R.string.stc_title, R.drawable.ic_groups, admin = true),
            Act("boss", R.string.boss_title, R.drawable.ic_trending, admin = true),
            Act("admin", R.string.ad_title, R.drawable.ic_tune, admin = true),
            Act("labels", R.string.home_labels, R.drawable.ic_label, admin = true),
            Act("scale", R.string.sc_title, R.drawable.ic_scale, admin = true),
            Act("rate", R.string.rt_title, R.drawable.ic_currency, admin = true),
        )

        val DEFAULTS = listOf("sale", ACT_SCAN, ACT_PAYMENT, "history", "report", "barcodes")

        fun actionOf(key: String): Act? = ACTIONS.firstOrNull { it.key == key }

        /** Rolga mos amallar (Agent'da Admin ishlari ko'rinmaydi). */
        fun available(): List<Act> = ACTIONS.filter { AppRole.isAdmin || !it.admin }

        private val BTN = listOf(R.id.wBtn0, R.id.wBtn1, R.id.wBtn2, R.id.wBtn3, R.id.wBtn4, R.id.wBtn5)
        private val ICON = listOf(R.id.wIcon0, R.id.wIcon1, R.id.wIcon2, R.id.wIcon3, R.id.wIcon4, R.id.wIcon5)
        private val TEXT = listOf(R.id.wText0, R.id.wText1, R.id.wText2, R.id.wText3, R.id.wText4, R.id.wText5)

        private fun pi(context: Context, req: Int, fill: Intent.() -> Unit): PendingIntent {
            val i = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                fill()
            }
            return PendingIntent.getActivity(context, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun views(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_quick)
            val keys = runCatching { ServiceLocator.appPrefs.widgetSlots }.getOrDefault(DEFAULTS)
            v.setViewVisibility(R.id.wInfo, View.GONE)
            // Vidjetda mavzu atributlari ishlamaydi — rang to'g'ridan-to'g'ri beriladi
            val cAccent = androidx.core.content.ContextCompat.getColor(context, R.color.brand_on_primary_container)
            val cIcon = androidx.core.content.ContextCompat.getColor(context, R.color.brand_primary)
            val cText = androidx.core.content.ContextCompat.getColor(context, R.color.brand_on_surface)
            for (slot in 0 until SLOTS) {
                val key = keys.getOrNull(slot).orEmpty()
                val storeId = if (key.startsWith(STORE_PREFIX)) key.removePrefix(STORE_PREFIX) else null
                val act = actionOf(key)
                val title: String = when {
                    storeId != null -> runCatching { ServiceLocator.catalog.storeById(storeId)?.name }.getOrNull()
                        ?: context.getString(R.string.wg_fav_empty)
                    act != null -> context.getString(act.title)
                    else -> context.getString(R.string.wg_fav_empty)
                }
                v.setTextViewText(TEXT[slot], title)
                v.setImageViewResource(ICON[slot], if (storeId != null) R.drawable.ic_store else act?.icon ?: R.drawable.ic_cart)
                v.setInt(BTN[slot], "setBackgroundResource", if (slot == 0) R.drawable.widget_btn_accent else R.drawable.widget_btn)
                v.setInt(ICON[slot], "setColorFilter", if (slot == 0) cAccent else cIcon)
                v.setTextColor(TEXT[slot], if (slot == 0) cAccent else cText)
                v.setOnClickPendingIntent(
                    BTN[slot],
                    pi(context, 200 + slot) {
                        if (storeId != null) putExtra(EXTRA_STORE, storeId) else if (act != null) putExtra(EXTRA_ACTION, act.key)
                    },
                )
            }
            v.setOnClickPendingIntent(R.id.wTitle, pi(context, 210) { })
            return v
        }

        /** Sozlama yoki do'konlar o'zgarganda vidjet yangilanadi. */
        fun refresh(context: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(context)
                val ids = m.getAppWidgetIds(ComponentName(context, QuickWidget::class.java))
                if (ids.isNotEmpty()) m.updateAppWidget(ids, views(context))
            }
        }
    }
}
