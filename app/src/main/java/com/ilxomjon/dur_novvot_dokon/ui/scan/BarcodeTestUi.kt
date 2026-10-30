package com.ilxomjon.dur_novvot_dokon.ui.scan

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository
import kotlinx.coroutines.launch
import uz.imsoft.shared.barcode.Ean

/** Shtrix kodni SINASH: ilova uni qanday tushunishini ko'rsatadi (savatchaga qo'shmaydi). */
object BarcodeTestUi {
    suspend fun explain(ctx: Context, raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val lines = ArrayList<String>()
        val n = Ean.numeric(t)
        if (n != null && n.length in setOf(8, 12, 13, 14)) {
            lines += ctx.getString(if (Ean.checkOk(n)) R.string.bc_t_check_ok else R.string.bc_t_check_bad, n.length)
        }
        ServiceLocator.barcodes.scale.parse(t)?.let { h -> lines += ctx.getString(R.string.bc_t_scale, h.itemCode, Money.qtyDisplay(h.qty)) }
        lines += when (val s = ServiceLocator.barcodes.resolve(t)) {
            is BarcodeRepository.Scan.Found -> ctx.getString(R.string.bc_t_found, s.product.name) + (s.qty?.let { " · " + ctx.getString(R.string.scan_weight, Money.qtyDisplay(it)) }.orEmpty())
            is BarcodeRepository.Scan.Missing -> ctx.getString(R.string.bc_t_missing)
            is BarcodeRepository.Scan.Unknown -> s.suggestion?.let { ctx.getString(R.string.bc_t_suggest, it.name) } ?: ctx.getString(R.string.bc_t_unknown)
        }
        return lines.joinToString("\n")
    }

    fun show(f: Fragment, raw: String, onClose: () -> Unit) {
        val ctx = f.requireContext()
        f.viewLifecycleOwner.lifecycleScope.launch {
            val text = explain(ctx, raw)
            MaterialAlertDialogBuilder(ctx).setTitle(raw).setMessage(text)
                .setPositiveButton(R.string.ok) { _, _ -> onClose() }
                .setOnCancelListener { onClose() }.show()
        }
    }
}
