package uz.imsoft.mijoz.ui.extra

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
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.data.MijozAuto
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.sched.WorkHours
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** v3.2 Mijoz: ish vaqti bo'yicha avtomatik yangilash va kunlik zaxira. */
class AutoFragment : MFragment() {
    private val p get() = Locator.prefs
    private lateinit var tvWork: TextView
    private lateinit var tvBackup: TextView
    private val mins = listOf(1, 2, 5, 15)

    override fun build(inflater: LayoutInflater): View {
        val pg = page(R.string.au_title)
        val c = pg.content
        c.addView(Ui.hint(ctx, getString(R.string.au_hint), 4))
        c.addView(Ui.switch(ctx, getString(R.string.au_auto), p.autoRefresh) { p.autoRefresh = it; changed() })
        c.addView(Ui.section(ctx, getString(R.string.au_every)))
        c.addView(Ui.toggle(ctx, mins.map { getString(R.string.au_min, it) }, mins.indexOf(p.liveMinutes).coerceAtLeast(1)) { i -> p.liveMinutes = mins[i] })
        c.addView(Ui.section(ctx, getString(R.string.au_work)))
        c.addView(Ui.switch(ctx, getString(R.string.au_work_on), p.workHours.on) { p.workHours = p.workHours.copy(on = it); changed() })
        tvWork = Ui.text(ctx, "", top = 6).also { c.addView(it) }
        val bStart = Ui.button(ctx, getString(R.string.au_start), outlined = true) { pick(true) }
        val bEnd = Ui.button(ctx, getString(R.string.au_end), outlined = true) { pick(false) }
        c.addView(Ui.buttonRow(ctx, bStart, bEnd, top = 4))
        c.addView(Ui.hint(ctx, getString(R.string.au_final_hint)))
        if (Build.VERSION.SDK_INT >= 31) c.addView(Ui.button(ctx, getString(R.string.au_exact), outlined = true) {
            runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
        })
        c.addView(Ui.section(ctx, getString(R.string.au_backup)))
        c.addView(Ui.switch(ctx, getString(R.string.au_backup_on), p.dailyBackup) { p.dailyBackup = it })
        tvBackup = Ui.hint(ctx, "", 4).also { c.addView(it) }
        val bNow = Ui.button(ctx, getString(R.string.au_backup_now), outlined = true) {
            launchUi { MijozAuto.backupIfDue(ctx.applicationContext, force = true); renderBackup(); snack(p.lastBackupError.ifEmpty { getString(R.string.au_backup_done) }) }
        }
        val bRestore = Ui.button(ctx, getString(R.string.au_restore), outlined = true) {
            val f = MijozAuto.dailyFile(ctx)
            if (!f.isFile) { snack(getString(R.string.au_backup_never)); return@button }
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.au_restore).setMessage(getString(R.string.au_restore_text, fmt(f.lastModified())))
                .setPositiveButton(R.string.au_restore) { _, _ -> launchUi { val n = MijozAuto.restore(ctx.applicationContext); snack(getString(R.string.au_restored, n)) } }
                .setNegativeButton(R.string.cancel, null).show()
        }
        c.addView(Ui.buttonRow(ctx, bNow, bRestore, top = 4))
        return pg.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { renderWork(); renderBackup() }

    private fun fmt(ms: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(ms))

    private fun renderWork() {
        val w = p.workHours
        tvWork.text = if (w.on) getString(R.string.au_work_text, WorkHours.format(w.start), WorkHours.format(w.end), WorkHours.format(w.finalMinute)) else getString(R.string.au_work_off)
    }

    private fun renderBackup() {
        tvBackup.text = buildString {
            append(if (p.lastBackupAt > 0) getString(R.string.au_backup_last, fmt(p.lastBackupAt)) else getString(R.string.au_backup_never))
            append('\n').append(getString(R.string.au_backup_where, MijozAuto.dailyFile(ctx).absolutePath))
            if (p.lastBackupError.isNotEmpty()) append('\n').append(getString(R.string.x_error, p.lastBackupError))
        }
    }

    private fun changed() { MijozAuto.apply(ctx); renderWork() }

    private fun pick(start: Boolean) {
        val w = p.workHours
        val m = if (start) w.start else w.end
        val picker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(m / 60).setMinute(m % 60)
            .setTitleText(if (start) R.string.au_start else R.string.au_end).build()
        picker.addOnPositiveButtonClickListener {
            val v = picker.hour * 60 + picker.minute
            p.workHours = if (start) w.copy(start = v) else w.copy(end = v)
            changed()
        }
        picker.show(childFragmentManager, "time")
    }
}

/** v3.2 Mijoz: chek qo'shimchalari. Do'kon nomi (brend) o'zgarmaydi — 1-ilovadan keladi. */
class ReceiptExtrasFragment : MFragment() {
    override fun build(inflater: LayoutInflater): View {
        val pg = page(R.string.rx_title)
        val c = pg.content
        val p = Locator.prefs
        val shop = Locator.data.state.value.snapshot?.shopName.orEmpty()
        c.addView(Ui.hint(ctx, getString(R.string.rx_hint), 4))
        c.addView(Ui.section(ctx, getString(R.string.rx_brand)))
        c.addView(Ui.title(ctx, shop.ifBlank { "—" }, 4))
        val (t1, e1) = Ui.field(ctx, getString(R.string.rx_sub), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE, p.receiptSub, 12)
        val (t2, e2) = Ui.field(ctx, getString(R.string.rx_footer), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE, p.receiptFooter)
        val (t3, e3) = Ui.field(ctx, getString(R.string.rx_thanks), value = p.receiptThanks)
        c.addView(t1); c.addView(Ui.hint(ctx, getString(R.string.rx_sub_hint)))
        c.addView(t2); c.addView(Ui.hint(ctx, getString(R.string.rx_footer_hint)))
        c.addView(t3)
        c.addView(Ui.button(ctx, getString(R.string.rx_save), top = 12) {
            p.receiptSub = e1.text?.toString()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3)?.joinToString("\n").orEmpty()
            p.receiptFooter = e2.text?.toString()?.trim().orEmpty().take(200)
            p.receiptThanks = e3.text?.toString()?.trim().orEmpty().take(120)
            snack(getString(R.string.rx_saved))
        })
        c.addView(Ui.button(ctx, getString(R.string.rx_test), outlined = true) {
            launchUi {
                val labels = posLabels()
                val sale = uz.imsoft.shared.pos.PosSale(0, uz.imsoft.shared.pos.Pos.stampOf(System.currentTimeMillis()), 0,
                    listOf(uz.imsoft.shared.pos.PosLine(getString(R.string.rx_test_item), "", 1.0, 1000.0)), 0.0, 1000.0)
                printReceipt(uz.imsoft.shared.pos.Pos.receipt(shop.ifBlank { getString(R.string.app_name) }, sale, null, null, labels))
            }
        })
        return pg.root
    }
}
