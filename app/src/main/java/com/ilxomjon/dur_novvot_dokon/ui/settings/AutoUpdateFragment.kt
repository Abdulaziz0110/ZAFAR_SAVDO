package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.AutoUpdate
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.sched.WorkHours

/**
 * v3.2: avtomatik yangilash, ish vaqti (tugashidan 1 daqiqa oldin oxirgi to'liq yuklab olish) va kunlik zaxira.
 * Agent va Admin'da bir xil; Admin'da ish vaqti kompyuterga ham yuboriladi.
 */
class AutoUpdateFragment : CodeFragment() {
    private val prefs get() = ServiceLocator.appPrefs
    private lateinit var tvWork: TextView
    private lateinit var tvBackup: TextView
    private val mins = listOf(1, 5, 15, 30)

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.au_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.au_hint), 4))
        c.addView(Ui.switch(ctx, getString(R.string.au_auto), prefs.autoUpdate) { prefs.autoUpdate = it; changed() })
        c.addView(Ui.section(ctx, getString(R.string.au_every)))
        c.addView(Ui.toggle(ctx, mins.map { getString(R.string.pc_min, it) }, mins.indexOf(prefs.updateMinutes).coerceAtLeast(1)) { i -> prefs.updateMinutes = mins[i]; changed() })

        c.addView(Ui.section(ctx, getString(R.string.pc_work)))
        c.addView(Ui.switch(ctx, getString(R.string.au_work_on), prefs.workHours.on) { prefs.workHours = prefs.workHours.copy(on = it); changed() })
        tvWork = Ui.text(ctx, "", top = 6).also { c.addView(it) }
        val bStart = Ui.button(ctx, getString(R.string.au_start), outlined = true) { pickTime(true) }
        val bEnd = Ui.button(ctx, getString(R.string.au_end), outlined = true) { pickTime(false) }
        c.addView(Ui.buttonRow(ctx, bStart, bEnd, top = 4))
        c.addView(Ui.hint(ctx, getString(R.string.au_final_hint)))
        if (Build.VERSION.SDK_INT >= 31) {
            c.addView(Ui.button(ctx, getString(R.string.au_exact), outlined = true) {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
            })
        }

        c.addView(Ui.section(ctx, getString(R.string.au_backup)))
        c.addView(Ui.switch(ctx, getString(R.string.au_backup_on), prefs.dailyBackup) { prefs.dailyBackup = it; changed() })
        tvBackup = Ui.hint(ctx, "", 4).also { c.addView(it) }
        val bNow = Ui.button(ctx, getString(R.string.au_backup_now), outlined = true) {
            launchUi {
                prefs.lastDailyBackupAt = 0L
                AutoUpdate.dailyBackupIfDue()
                renderBackup()
                snack(if (prefs.lastDailyBackupError.isEmpty()) getString(R.string.au_backup_done) else prefs.lastDailyBackupError)
            }
        }
        val bRestore = Ui.button(ctx, getString(R.string.au_restore), outlined = true) { restore() }
        c.addView(Ui.buttonRow(ctx, bNow, bRestore, top = 4))
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        renderWork()
        renderBackup()
    }

    private fun renderWork() {
        val w = prefs.workHours
        tvWork.text = if (w.on) getString(R.string.pc_work_on, WorkHours.format(w.start), WorkHours.format(w.end), WorkHours.format(w.finalMinute))
        else getString(R.string.pc_work_off)
    }

    private fun renderBackup() {
        val f = ServiceLocator.backup.dailyFile()
        tvBackup.text = buildString {
            append(if (prefs.lastDailyBackupAt > 0) getString(R.string.au_backup_last, formatDateTime(prefs.lastDailyBackupAt)) else getString(R.string.au_backup_never))
            append('\n').append(getString(R.string.au_backup_where, f.absolutePath))
            if (prefs.lastDailyBackupError.isNotEmpty()) append('\n').append(getString(R.string.ad_error, prefs.lastDailyBackupError))
        }
    }

    private fun changed() {
        AutoUpdate.apply(ctx)
        renderWork()
        if (AppRole.isAdmin && ServiceLocator.shareStore.isCloudReady) launchUi {
            val st = ServiceLocator.shareStore
            ServiceLocator.share.setPcSchedule(st.pcHours, st.pcDays, st.pcMinutes)
        }
    }

    private fun pickTime(start: Boolean) {
        val w = prefs.workHours
        val m = if (start) w.start else w.end
        val picker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(m / 60).setMinute(m % 60)
            .setTitleText(if (start) R.string.au_start else R.string.au_end).build()
        picker.addOnPositiveButtonClickListener {
            val v = picker.hour * 60 + picker.minute
            prefs.workHours = if (start) w.copy(start = v) else w.copy(end = v)
            changed()
        }
        picker.show(childFragmentManager, "time")
    }

    private fun restore() {
        val f = ServiceLocator.backup.dailyFile()
        if (!f.isFile) { snack(getString(R.string.au_backup_never)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.au_restore).setMessage(getString(R.string.au_restore_text, formatDateTime(f.lastModified())))
            .setPositiveButton(R.string.au_restore) { _, _ ->
                launchUi {
                    val n = ServiceLocator.backup.import(Uri.fromFile(f), null)
                    snack(getString(R.string.au_restored, n))
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
