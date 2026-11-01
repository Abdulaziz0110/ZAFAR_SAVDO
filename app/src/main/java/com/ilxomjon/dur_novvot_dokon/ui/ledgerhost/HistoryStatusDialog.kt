package com.ilxomjon.dur_novvot_dokon.ui.ledgerhost

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.share.ShareWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** v3.6 Admin: "1C tarixi" holati — qayerdan, qachon keladi; hozir yangilash; server javobi o'qilmasa — faylni ulashish. */
object HistoryStatusDialog {
    fun show(f: Fragment) {
        val ctx = f.requireContext()
        val h = ServiceLocator.share.history
        val fail = h.failFile()
        val b = MaterialAlertDialogBuilder(ctx).setTitle(R.string.sh_title).setMessage(ServiceLocator.adminHistory.status.value + "\n\n" + h.details())
            .setPositiveButton(R.string.sh_refresh) { _, _ ->
                f.viewLifecycleOwner.lifecycleScope.launch {
                    h.bumpAll()
                    // Kompyuterdan ham oxirgi 30 kun (kompyuter 30 soniyagacha bajaradi)
                    runCatching {
                        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                        val now = System.currentTimeMillis()
                        ServiceLocator.onec.requestData("sales", fmt.format(java.util.Date(now - 29 * 86_400_000L)), fmt.format(java.util.Date(now)))
                    }
                    ShareWorker.kick(ctx.applicationContext, 1)
                    f.view?.let { Snackbar.make(it, R.string.sh_refresh_started, Snackbar.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton(R.string.lg_close, null)
        if (fail.isFile) b.setNeutralButton(R.string.sh_share_fail) { _, _ -> shareFail(f, fail) }
        b.show()
    }

    private fun shareFail(f: Fragment, src: File) {
        val ctx = f.requireContext()
        f.viewLifecycleOwner.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                File(dir, "server_javobi.html").also { src.copyTo(it, overwrite = true) }
            }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            f.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/html"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, null))
        }
    }
}
