package uz.imsoft.mijoz.ui.ledger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.DialogReceiptPreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.android.ReceiptBitmap
import uz.imsoft.shared.receipt.Receipt
import uz.imsoft.shared.receipt.ReceiptBuilder
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.Stamp
import java.io.File

/** Chek: ko'rib chiqish, printerga yuborish, rasm/matn ulashish, PDF. */
object ReceiptActions {
    /** Chop etish tugaguncha WebView yo'qolmasin. */
    private var printView: WebView? = null

    fun preview(f: Fragment, doc: LedgerDoc, host: LedgerHost?, client: String) {
        val ctx = f.requireContext()
        val style = host?.receiptStyle ?: uz.imsoft.shared.receipt.ReceiptStyle.STANDARD
        val r = style.apply(LedgerUi.receipt(ctx, doc, host?.receiptHeader.orEmpty(), client, host?.receiptFooter.orEmpty(), host?.receiptStaff.orEmpty()))
        f.viewLifecycleOwner.lifecycleScope.launch {
            val logo = host?.receiptLogo
            val bmp = withContext(Dispatchers.Default) { ReceiptBitmap.render(r, style, logo) }
            val vb = DialogReceiptPreviewBinding.inflate(LayoutInflater.from(ctx))
            vb.image.setImageBitmap(bmp)
            val targets = host?.printTargets(ctx).orEmpty()
            val d = MaterialAlertDialogBuilder(ctx).setTitle(R.string.lg_receipt).setView(vb.root)
                .setNegativeButton(R.string.lg_pdf) { _, _ -> printPdf(ctx, r, "Chek_" + Stamp.formatDate(doc.stamp)) }
                .setNeutralButton(R.string.lg_share_image) { _, _ -> f.viewLifecycleOwner.lifecycleScope.launch { shareImage(ctx, r, bmp) } }
            if (targets.isNotEmpty()) {
                d.setPositiveButton(R.string.lg_print) { _, _ ->
                    if (targets.size == 1) send(f, targets[0], bmp, style.dark)
                    else MaterialAlertDialogBuilder(ctx).setTitle(R.string.lg_print)
                        .setItems(targets.map { it.label }.toTypedArray()) { _, i -> send(f, targets[i], bmp, style.dark) }
                        .setNegativeButton(R.string.lg_cancel, null).show()
                }
            }
            d.show()
        }
    }

    private fun send(f: Fragment, t: PrintTarget, bmp: Bitmap, dark: Int) {
        val act = f.requireActivity()
        val root = act.findViewById<android.view.View>(android.R.id.content)
        act.lifecycleScope.launch {
            val msg = runCatching {
                val bytes = withContext(Dispatchers.Default) { ReceiptBitmap.toEscPos(bmp, dark) }
                t.send(bytes)
            }.getOrElse { e -> act.getString(R.string.lg_error, e.message ?: e.javaClass.simpleName) }
            Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun shareDir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }

    suspend fun shareImage(ctx: Context, r: Receipt, ready: Bitmap? = null, host: LedgerHost? = null) {
        val file = withContext(Dispatchers.IO) {
            val bmp = ready ?: ReceiptBitmap.render(r, host?.receiptStyle ?: uz.imsoft.shared.receipt.ReceiptStyle.STANDARD, host?.receiptLogo)
            shareDir(ctx).listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000L) it.delete() }
            ReceiptBitmap.savePng(bmp, File(shareDir(ctx), "chek_" + System.currentTimeMillis() + ".png"))
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, r.title + " · " + r.dateText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.lg_share)))
    }

    fun shareText(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.lg_share)))
    }

    fun copyText(ctx: Context, text: String) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("chek", text))
    }

    fun printPdf(ctx: Context, r: Receipt, name: String) = printHtml(ctx, ReceiptBuilder.toHtml(r), name)

    fun printHtml(ctx: Context, html: String, name: String) {
        val wv = WebView(ctx)
        printView = wv
        wv.settings.javaScriptEnabled = false
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // Chop etish faqat Activity orqali (o'ralgan kontekstdan emas)
                var c: Context = ctx
                while (c !is android.app.Activity && c is android.content.ContextWrapper) c = c.baseContext
                val pm = c.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val safe = name.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(40).ifEmpty { "chek" }
                pm.print(safe, view.createPrintDocumentAdapter(safe), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
