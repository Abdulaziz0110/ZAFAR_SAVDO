package uz.imsoft.mijoz.ui.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.describe
import uz.imsoft.shared.LinkCode

/** "Kodni joylash": 1-ilova yuborgan ulanish kodini qabul qiladi (bufer avtomatik qo'yiladi). */
object LinkDialog {
    fun show(f: Fragment, onDone: () -> Unit = {}) {
        val ctx = f.requireContext()
        val clip = runCatching {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        }.getOrNull().orEmpty()
        val til = TextInputLayout(ctx).apply { setPadding(48, 16, 48, 0); hint = ctx.getString(R.string.link_hint) }
        val edt = TextInputEditText(til.context).apply {
            minLines = 2
            if (LinkCode.decode(clip) != null) setText(clip)
        }
        til.addView(edt)
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.link_paste).setMessage(R.string.link_explain).setView(til)
            .setPositiveButton(R.string.ok) { _, _ ->
                val code = LinkCode.decode(edt.text?.toString().orEmpty())
                val root = f.view ?: return@setPositiveButton
                if (code == null) {
                    Snackbar.make(root, R.string.link_bad, Snackbar.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                Locator.prefs.setLink(code)
                f.viewLifecycleOwner.lifecycleScope.launch {
                    Locator.data.onLinked()
                    uz.imsoft.mijoz.data.RefreshWorker.schedule(ctx.applicationContext, Locator.prefs.autoMinutes)
                    val msg = try {
                        Locator.data.refresh(askFresh = true)
                        ctx.getString(R.string.link_ok, code.clientName)
                    } catch (e: Exception) {
                        ctx.getString(R.string.link_saved_offline) + "\n" + ctx.describe(e)
                    }
                    f.view?.let { Snackbar.make(it, msg, Snackbar.LENGTH_LONG).show() }
                    onDone()
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
