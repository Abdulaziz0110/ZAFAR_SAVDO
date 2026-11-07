package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.GoodsField
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest

/**
 * v5.0.3: dollar kursi faqat MAHALLIY. 1C'dagi kursga umuman qaralmaydi — ilova internetdagi bozor kursini
 * o'zi oladi (bank kursi, bo'lmasa Markaziy bank) va shu kursda hisoblaydi. Kursni qo'lda ham yozish mumkin.
 * "1C'ga ham yozish" — alohida tugma: 1C'dagi keyingi hujjatlar uchun, faqat Admin tasdig'i bilan.
 */
class RateFragment : CodeFragment() {
    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.rt_title)
        val c = p.content
        val prefs = ServiceLocator.appPrefs
        c.addView(Ui.hint(ctx, getString(R.string.rt_hint), 4))

        val cur = prefs.localRate
        c.addView(
            Ui.title(
                ctx,
                getString(R.string.rt_now, if (cur > 0) som(cur) else "—") +
                    (if (prefs.localRateAt > 0) " · " + formatDateTime(prefs.localRateAt) else ""),
                12,
            ),
        )
        val (til, e) = Ui.field(ctx, getString(R.string.rt_new), Ui.NUMBER, if (cur > 0) Money.plain(cur) else "")
        c.addView(til)

        // Ilovada ishlatiladigan kurs
        c.addView(
            Ui.button(ctx, getString(R.string.rt_save_local), top = 12, icon = R.drawable.ic_check) {
                val v = Money.parseUser(e.text?.toString()) ?: 0.0
                if (v < 1000 || v > 1_000_000) { snack(getString(R.string.rt_bad)); return@button }
                prefs.localRate = v
                prefs.localRateManual = true
                launchUi {
                    runCatching { ServiceLocator.catalog.load() }
                    snack(getString(R.string.rt_saved, som(v)))
                    navigator().pop()
                }
            },
        )
        // 1C'ga yozish — ixtiyoriy (ilovaning o'z hisobiga ta'sir qilmaydi)
        c.addView(
            Ui.button(ctx, getString(R.string.rt_to_1c), outlined = true, top = 8, icon = R.drawable.ic_sync_alt) {
                val v = Money.parseUser(e.text?.toString()) ?: 0.0
                val r = WriteRequest(WriteCodec.newId(), WriteKind.RATE, System.currentTimeMillis(), fields = mapOf(GoodsField.RATE to Money.plain(v)))
                if (r.problem() != null) { snack(getString(R.string.rt_bad)); return@button }
                if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return@button }
                MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_confirm_title).setMessage(getString(R.string.rt_confirm_1c, som(v)))
                    .setPositiveButton(R.string.wf_confirm_yes) { _, _ ->
                        launchUi {
                            ServiceLocator.onec.submit(r, "Admin")
                            snack(getString(R.string.wf_sent))
                            navigator().pop()
                            navigator().push(WritesFragment())
                        }
                    }.setNegativeButton(R.string.cancel, null).show()
            },
        )
        c.addView(Ui.hint(ctx, getString(R.string.wf_safety), 8))
        c.addView(
            Ui.switch(ctx, getString(R.string.rt_auto), !prefs.localRateManual, 10) { on ->
                prefs.localRateManual = !on
                if (on) launchUi { runCatching { ServiceLocator.refreshRate() } }
            },
        )

        // Internetdagi kurs — bosilsa yuqoridagi maydonga tushadi
        c.addView(Ui.section(ctx, getString(R.string.rt_net_title)))
        val box = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL }
        c.addView(box)
        box.addView(Ui.hint(ctx, getString(R.string.rt_net_wait), 0))
        fun render(list: List<uz.imsoft.shared.sched.RateQuote.Quote>) {
            if (view == null) return
            box.removeAllViews()
            if (list.isEmpty()) { box.addView(Ui.hint(ctx, getString(R.string.rt_net_none), 0)); return }
            for (q in list) {
                val sub = if (q.buy > 0 && q.sell > 0) getString(R.string.rt_net_row, som(q.buy), som(q.sell)) else som(q.value)
                val title = q.source + (if (q.date.isNotEmpty()) " · " + q.date else "")
                box.addView(
                    Ui.item(ctx, title, sub, som(q.value), R.drawable.ic_currency) {
                        e.setText(Money.plain(q.value))
                        e.setSelection(e.text?.length ?: 0)
                    },
                )
            }
        }
        render(ServiceLocator.rateOnline.cached())
        launchUi { render(ServiceLocator.rateOnline.load()) }
        return p.root
    }
}
