package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.share.ImportUi
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.sched.WorkHours

/**
 * v3.2 Admin: kompyuter (1C) — holati, yangilash tezligi (1 daqiqagacha), to'liq hisobot oralig'i, ish vaqti va
 * kompyuter sozlamasi (forwarder.ini). Hammasi bulut orqali — kompyuter har 30 soniyada o'qiydi.
 */
class PcFragment : CodeFragment() {
    private lateinit var tvStatus: TextView
    private lateinit var tvWork: TextView
    private val minutes = listOf(0, 1, 5, 15, 30, 60)
    private val hours = listOf(1, 2, 3)
    private val days = listOf(7, 14, 30)

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.ad_pc)
        val c = p.content
        val share = ServiceLocator.share
        val st = ServiceLocator.shareStore
        tvStatus = Ui.text(ctx, "", top = 4).also { c.addView(it) }
        c.addView(Ui.button(ctx, getString(R.string.pc_refresh_status), outlined = true, icon = R.drawable.ic_sync) { launchUi { share.refreshPcStatus() } })

        c.addView(Ui.section(ctx, getString(R.string.pc_live)))
        c.addView(Ui.hint(ctx, getString(R.string.pc_live_hint)))
        c.addView(Ui.toggle(ctx, minutes.map { if (it == 0) getString(R.string.pc_off) else getString(R.string.pc_min, it) }, minutes.indexOf(st.pcMinutes).coerceAtLeast(0)) { i ->
            save(minutes = minutes[i])
        })

        c.addView(Ui.section(ctx, getString(R.string.pc_full)))
        c.addView(Ui.hint(ctx, getString(R.string.pc_full_hint)))
        c.addView(Ui.toggle(ctx, hours.map { getString(R.string.pc_hours, it) }, hours.indexOf(st.pcHours).coerceAtLeast(1)) { i -> save(hours = hours[i]) })
        c.addView(Ui.toggle(ctx, days.map { getString(R.string.pc_days, it) }, days.indexOf(st.pcDays).coerceAtLeast(0)) { i -> save(days = days[i]) })

        c.addView(Ui.section(ctx, getString(R.string.pc_work)))
        tvWork = Ui.text(ctx, "", top = 4).also { c.addView(it) }
        c.addView(Ui.button(ctx, getString(R.string.pc_work_change), outlined = true, icon = R.drawable.ic_calendar) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.settings.AutoUpdateFragment()) })

        c.addView(Ui.section(ctx, getString(R.string.pc_setup)))
        c.addView(Ui.hint(ctx, getString(R.string.pc_setup_hint)))
        c.addView(Ui.button(ctx, getString(R.string.share_pc_config), icon = R.drawable.ic_computer) { pcConfig() })
        c.addView(Ui.button(ctx, getString(R.string.pc_codes_now), outlined = true, icon = R.drawable.ic_download) {
            launchUi { ServiceLocator.labels.requestCodes(); snack(getString(R.string.oc_request_sent)) }
        })
        c.addView(Ui.hint(ctx, getString(R.string.pc_write_hint), 12))
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchOnStarted {
            ServiceLocator.share.state.collect { s ->
                tvStatus.text = when {
                    !s.cloudReady -> getString(R.string.share_st_no_firebase)
                    s.pcAt > 0 -> getString(if (s.pcOk) R.string.pc_st_ok else R.string.pc_st_err, formatDateTime(s.pcAt), s.pcStatus)
                    else -> getString(R.string.pc_st_never)
                }
            }
        }
        val w = ServiceLocator.appPrefs.workHours
        tvWork.text = if (w.on) getString(R.string.pc_work_on, WorkHours.format(w.start), WorkHours.format(w.end), WorkHours.format(w.finalMinute))
        else getString(R.string.pc_work_off)
        launchUi { ServiceLocator.share.refreshPcStatus() }
    }

    private fun save(minutes: Int = ServiceLocator.shareStore.pcMinutes, hours: Int = ServiceLocator.shareStore.pcHours, days: Int = ServiceLocator.shareStore.pcDays) {
        launchUi {
            ServiceLocator.share.setPcSchedule(hours, days, minutes)
            snack(getString(R.string.pc_saved))
        }
    }

    private fun pcConfig() {
        val ini = ServiceLocator.share.forwarderIni()
        if (ini.isEmpty()) { snack(getString(R.string.share_st_no_firebase)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.share_pc_config).setMessage(getString(R.string.share_pc_config_text))
            .setPositiveButton(R.string.share_send) { _, _ -> ImportUi.shareText(ctx, ini) }
            .setNegativeButton(R.string.close, null).show()
    }
}
