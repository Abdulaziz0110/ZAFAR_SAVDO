package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import kotlinx.coroutines.delay
import uz.imsoft.shared.android.Ui

/**
 * v3.10 Admin: taroziga narx va PLU yuborish — 1C'dagi "Taroziga yuklash" (RONGTA) bilan bir xil. Ishni do'kon
 * kompyuteri bajaradi (1C'dan o'qiydi, tarozi dasturini ishga tushiradi), natija shu yerda ko'rinadi.
 */
class ScaleFragment : CodeFragment() {
    private lateinit var tvResult: TextView

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.sc_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.sc_hint), 4))
        c.addView(Ui.button(ctx, getString(R.string.sc_stock)) { send(false) })
        c.addView(Ui.hint(ctx, getString(R.string.sc_stock_hint)))
        c.addView(Ui.button(ctx, getString(R.string.sc_clear), outlined = true, top = 16) {
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.sc_clear).setMessage(R.string.sc_clear_confirm)
                .setPositiveButton(R.string.sc_clear) { _, _ -> send(true) }.setNegativeButton(R.string.cancel, null).show()
        })
        c.addView(Ui.hint(ctx, getString(R.string.sc_clear_hint)))
        c.addView(Ui.section(ctx, getString(R.string.sc_result)))
        tvResult = Ui.text(ctx, getString(R.string.sc_none), top = 4).also { c.addView(it) }
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        launchUi { show(ServiceLocator.onec.scaleResult(), 0L) }
    }

    private fun show(r: com.ilxomjon.dur_novvot_dokon.data.onec.OneCRepository.ScaleResult?, waitingFor: Long) {
        if (!::tvResult.isInitialized) return
        tvResult.text = when {
            r != null && (waitingFor == 0L || r.req >= waitingFor) -> getString(if (r.ok) R.string.sc_ok else R.string.sc_fail, formatDateTime(r.at), r.text)
            waitingFor > 0 -> getString(R.string.sc_waiting)
            else -> getString(R.string.sc_none)
        }
    }

    private fun send(clear: Boolean) {
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        launchUi {
            val at = ServiceLocator.onec.requestScale(clear)
            show(null, at)
            snack(getString(R.string.sc_sent))
            // Kompyuter 30 soniyagacha oladi; 3 daqiqa davomida natijani kutadi
            repeat(18) {
                delay(10_000L)
                val r = ServiceLocator.onec.scaleResult()
                if (r != null && r.req >= at) { show(r, at); return@launchUi }
            }
            if (isAdded) tvResult.text = getString(R.string.sc_timeout)
        }
    }
}
