package com.ilxomjon.dur_novvot_dokon.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.share.ImportPreview
import com.ilxomjon.dur_novvot_dokon.data.share.NotACardException
import com.ilxomjon.dur_novvot_dokon.data.share.ShareWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.Stamp
import uz.imsoft.shared.report.TableReader

/** Excel/1C faylini o'qish, ko'rib chiqish oynasi va saqlash (Mijozlar va bitta mijoz ekranlari uchun umumiy). */
object ImportUi {
    private const val MAX_FILE = 30 * 1024 * 1024

    fun import(f: Fragment, uri: Uri, targetId: String?, onDone: () -> Unit = {}) {
        f.viewLifecycleOwner.lifecycleScope.launch {
            val ctx = f.requireContext()
            try {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val n = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: uri.lastPathSegment.orEmpty()
                    val b = ctx.contentResolver.openInputStream(uri)?.use { s ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val k = s.read(buf)
                            if (k < 0) break
                            out.write(buf, 0, k)
                            if (out.size() > MAX_FILE) throw IllegalArgumentException("too_big")
                        }
                        out.toByteArray()
                    } ?: throw java.io.IOException("open")
                    b to n
                }
                showPreview(f, ServiceLocator.share.previewFile(bytes, name, targetId), onDone)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: NotACardException) {
                showNotACard(f, e)
            } catch (e: TableReader.UnsupportedFormat) {
                snack(f, ctx.getString(if (e.message == "mxl") R.string.share_err_mxl else R.string.share_err_xlsx))
            } catch (e: Exception) {
                snack(f, ctx.getString(R.string.share_import_fail) + "\n" + (e.message ?: e.javaClass.simpleName).take(120))
            }
        }
    }

    private fun money2(ctx: Context, m: Money2): String {
        val parts = ArrayList<String>()
        if (m.som != 0.0 || m.usd == 0.0) parts += Money.display(m.som) + " " + ctx.getString(R.string.unit_som)
        if (m.usd != 0.0) parts += Money.display(m.usd) + " $"
        return parts.joinToString(" + ")
    }

    fun showPreview(f: Fragment, p: ImportPreview, onDone: () -> Unit = {}) {
        if (f.view == null) return
        val ctx = f.requireContext()
        val r = p.report
        val sale = r.entries.filter { it.kind == EntryKind.SALE }
        val pay = r.entries.filter { it.kind == EntryKind.PAYMENT }
        val ret = r.entries.filter { it.kind == EntryKind.RETURN }
        fun sum(list: List<LedgerEntry>, c: Col) = money2(ctx, Money2(list.sumOf { it.v(c).som }, list.sumOf { it.v(c).usd }))
        val sb = StringBuilder()
        sb.append(
            if (p.target.existingId != null) ctx.getString(R.string.cl_pv_to, p.targetName)
            else ctx.getString(R.string.cl_pv_new, p.targetName),
        ).append("\n\n")
        sb.append(ctx.getString(R.string.share_pv_period, r.periodFrom?.let { Stamp.formatDate(it) } ?: "—", r.periodTo?.let { Stamp.formatDate(it) } ?: "—")).append('\n')
        sb.append(ctx.getString(R.string.share_pv_counts, r.entries.size, sale.size, pay.size, ret.size)).append('\n')
        sb.append(ctx.getString(R.string.share_pv_sales, sum(sale, Col.SUM))).append('\n')
        sb.append(ctx.getString(R.string.share_pv_payments, sum(r.entries, Col.PAYMENT))).append('\n')
        r.opening?.let { sb.append(ctx.getString(R.string.share_pv_opening, Stamp.formatDate(it.first), money2(ctx, it.second))).append('\n') }
        r.closing?.let { sb.append(ctx.getString(R.string.share_pv_closing, Stamp.formatDate(it.first), money2(ctx, it.second))).append('\n') }
        if (r.subtotalRows > 0) sb.append(ctx.getString(R.string.share_pv_subtotals, r.subtotalRows)).append('\n')
        if (r.skippedRows > 0) sb.append(ctx.getString(R.string.share_pv_skipped, r.skippedRows)).append('\n')
        sb.append(ctx.getString(R.string.share_pv_products, p.matchedProducts, p.matchedProducts + p.unmatchedProducts.size)).append('\n')
        if (p.unmatchedProducts.isNotEmpty()) sb.append(ctx.getString(R.string.share_pv_unmatched, p.unmatchedProducts.take(8).joinToString(", "))).append('\n')
        sb.append('\n').append(ctx.getString(R.string.share_pv_merge, p.merge.added, p.merge.removed, p.merge.same))
        r.warnings.forEach { sb.append("\n⚠ ").append(it) }
        val save = { toCurrent: Boolean ->
            f.viewLifecycleOwner.lifecycleScope.launch {
                ServiceLocator.share.commit(p, toCurrent)
                snack(f, ctx.getString(R.string.share_saved))
                onDone()
                val repo = ServiceLocator.share
                if (repo.state.value.configured && repo.state.value.autoPublish) {
                    val ok = repo.publish()
                    if (!ok) ShareWorker.kick(ctx.applicationContext)
                }
            }
            Unit
        }
        val d = MaterialAlertDialogBuilder(ctx).setTitle(ctx.getString(R.string.share_pv_title, p.name)).setMessage(sb.toString())
            .setNegativeButton(R.string.cancel, null)
        if (r.entries.isNotEmpty()) {
            d.setPositiveButton(R.string.share_pv_save) { _, _ -> save(false) }
            val cur = ServiceLocator.share.currentClient()
            if (p.target.existingId == null && cur != null) d.setNeutralButton(ctx.getString(R.string.cl_pv_to_current, cur.name)) { _, _ -> save(true) }
        }
        d.show()
    }

    fun showNotACard(f: Fragment, e: NotACardException) {
        val ctx = f.requireContext()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.share_not_card_title)
            .setMessage(ctx.getString(R.string.share_not_card_msg) + "\n\n" + e.dump.take(1500))
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.share_send) { _, _ ->
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, e.message + "\n\n" + e.dump) }, ctx.getString(R.string.share_chooser)))
            }.show()
    }

    fun snack(f: Fragment, t: String) {
        val v: View = f.view ?: return
        Snackbar.make(v, t, Snackbar.LENGTH_LONG).show()
    }

    fun shareText(ctx: Context, text: String) {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, ctx.getString(R.string.share_chooser)))
    }
}
