package com.ilxomjon.dur_novvot_dokon.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.InputType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupException
import com.ilxomjon.dur_novvot_dokon.data.repo.BackupPreview
import com.ilxomjon.dur_novvot_dokon.ui.MainActivity

/** Zaxira oynalari (Sozlamalar va Admin'ning kirish ekrani uchun umumiy). */
object BackupUi {
    /** Parol so'raydi. [optional] bo'lsa bo'sh qoldirish mumkin (shifrsiz). */
    fun askPassword(ctx: Context, title: Int, hint: Int, optional: Boolean, done: (CharArray?) -> Unit) {
        val til = TextInputLayout(ctx).apply {
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            setPadding(48, 16, 48, 0)
        }
        val edt = TextInputEditText(til.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHint(hint)
        }
        til.addView(edt)
        MaterialAlertDialogBuilder(ctx).setTitle(title).setView(til)
            .setPositiveButton(R.string.ok) { _, _ ->
                val t = edt.text?.toString().orEmpty()
                if (t.isEmpty() && !optional) return@setPositiveButton
                done(if (t.isEmpty()) null else t.toCharArray())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun previewText(ctx: Context, p: BackupPreview): String {
        val base = ctx.getString(
            R.string.restore_preview, if (p.createdAt > 0) com.ilxomjon.dur_novvot_dokon.ui.formatDateTime(p.createdAt) else "—",
            p.products, p.stores, p.orders, p.payments, p.unsent,
        )
        return if (p.hasSettings) base + "\n\n" + ctx.getString(R.string.restore_has_settings) else base
    }

    fun errorText(ctx: Context, t: Throwable): String = when ((t as? BackupException)?.kind) {
        BackupException.Kind.WRONG_PASSWORD -> ctx.getString(R.string.backup_err_password)
        BackupException.Kind.NOT_BACKUP -> ctx.getString(R.string.backup_err_not_backup)
        BackupException.Kind.TOO_NEW -> ctx.getString(R.string.backup_err_too_new)
        BackupException.Kind.CORRUPT -> ctx.getString(R.string.backup_err_corrupt)
        BackupException.Kind.DISK_FULL -> ctx.getString(R.string.backup_err_disk_full)
        else -> t.message.orEmpty().take(120)
    }

    /** Sozlamalar tiklangandan keyin: xotiradagi holat eski, ilova qayta ishga tushadi. */
    fun askRestart(activity: Activity) {
        MaterialAlertDialogBuilder(activity).setTitle(R.string.restore_restart_title).setMessage(R.string.restore_restart_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.restore_restart) { _, _ -> restart(activity) }
            .show()
    }

    fun restart(activity: Activity) {
        val intent = Intent.makeRestartActivityTask(ComponentName(activity, MainActivity::class.java))
        activity.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
