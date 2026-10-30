package com.ilxomjon.dur_novvot_dokon.ui.scan

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import kotlinx.coroutines.launch
import uz.imsoft.shared.Fold

/**
 * Skanerlash natijasini savatchaga qo'shish (kamera ham, ulanadigan skaner ham shu yerdan o'tadi).
 * Narx — savatchadagi qatorniki yoki odatdagi qoida (savdo turi, valyuta, kurs); qoldiq tekshirilmaydi — qo'lda qo'shgandek.
 */
object ScanUi {
    class Added(val product: Product, val delta: Double, val lineQty: Double, val weighed: Boolean)

    private var tone: ToneGenerator? = null

    fun add(p: Product, weight: Double?): Added {
        val draft = ServiceLocator.draft
        val d = draft.state.value
        val price = d.lineFor(p.id)?.price ?: Pricing.unitPrice(p, d.priceType, d.currency, ServiceLocator.catalog.state.value.rate)
        val delta = weight ?: 1.0
        draft.adjust(p, delta, price)
        ServiceLocator.kpi.tap()
        return Added(p, delta, (d.lineFor(p.id)?.qty ?: 0.0) + delta, weight != null)
    }

    fun undo(a: Added) {
        val d = ServiceLocator.draft.state.value
        val price = d.lineFor(a.product.id)?.price ?: return
        ServiceLocator.draft.adjust(a.product, -a.delta, price)
    }

    fun describe(ctx: Context, a: Added): String {
        val d = ServiceLocator.draft.state.value
        val line = d.lineFor(a.product.id)
        val price = line?.price ?: 0.0
        val head = if (a.weighed) ctx.getString(R.string.scan_added_weight, Money.qtyDisplay(a.delta)) else ctx.getString(R.string.scan_added_one)
        return head + " · " + ctx.getString(R.string.scan_in_cart, Money.qtyDisplay(line?.qty ?: a.lineQty)) + if (price > 0) " · " + ctx.money(price, d.currency) else ""
    }

    fun feedback(ctx: Context, ok: Boolean) {
        val repo = ServiceLocator.barcodes
        if (repo.beep) runCatching {
            val t = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).also { tone = it }
            t.startTone(if (ok) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_NACK, if (ok) 90 else 250)
        }
        if (repo.vibrate) runCatching {
            @Suppress("DEPRECATION")
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(if (ok) 40L else 200L, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(if (ok) 40L else 200L)
        }
    }

    /**
     * Skanerlangan matnni qayta ishlaydi. Topilsa — savatchaga qo'shib [onAdded]; topilmasa — so'raydi (tovar tanlash yoki
     * tarozi kodi taklifini tasdiqlash), tanlov eslab qolinadi. [onIdle] — dialog yopilgach (kamera qayta ishlashi uchun).
     */
    fun handle(f: Fragment, raw: String, onAdded: (Added) -> Unit, onIdle: () -> Unit = {}) {
        val ctx = f.requireContext()
        f.viewLifecycleOwner.lifecycleScope.launch {
            when (val s = ServiceLocator.barcodes.resolve(raw)) {
                is BarcodeRepository.Scan.Found -> {
                    feedback(ctx, true)
                    onAdded(add(s.product, s.qty))
                    onIdle()
                }
                is BarcodeRepository.Scan.Missing -> {
                    feedback(ctx, false)
                    unknown(f, BarcodeRepository.Scan.Unknown(uz.imsoft.shared.barcode.BarcodeResolver.Result.Unknown(s.raw, null, null), null), onAdded, onIdle)
                }
                is BarcodeRepository.Scan.Unknown -> {
                    feedback(ctx, false)
                    unknown(f, s, onAdded, onIdle)
                }
            }
        }
    }

    private fun unknown(f: Fragment, s: BarcodeRepository.Scan.Unknown, onAdded: (Added) -> Unit, onIdle: () -> Unit) {
        if (f.view == null) return
        val ctx = f.requireContext()
        val r = s.result
        val learnAndAdd = { p: Product ->
            f.viewLifecycleOwner.lifecycleScope.launch {
                ServiceLocator.barcodes.learn(r, p.id)
                onAdded(add(p, r.qty))
                onIdle()
            }
            Unit
        }
        val weight = r.qty?.let { " · " + ctx.getString(R.string.scan_weight, Money.qtyDisplay(it)) }.orEmpty()
        val sug = s.suggestion
        val b = MaterialAlertDialogBuilder(ctx).setOnCancelListener { onIdle() }
        if (sug != null && r.scaleCode != null) {
            b.setTitle(R.string.scan_scale_confirm_title)
                .setMessage(ctx.getString(R.string.scan_scale_confirm, r.scaleCode, sug.name) + weight)
                .setPositiveButton(R.string.scan_yes) { _, _ -> learnAndAdd(sug) }
                .setNeutralButton(R.string.scan_pick_other) { _, _ -> pickProduct(f, r.raw, onIdle, learnAndAdd) }
                .setNegativeButton(R.string.cancel) { _, _ -> onIdle() }
        } else {
            b.setTitle(R.string.scan_unknown_title)
                .setMessage(ctx.getString(if (r.scaleCode != null) R.string.scan_unknown_scale else R.string.scan_unknown, r.raw, r.scaleCode.orEmpty()) + weight)
                .setPositiveButton(R.string.scan_pick) { _, _ -> pickProduct(f, r.raw, onIdle, learnAndAdd) }
                .setNegativeButton(R.string.cancel) { _, _ -> onIdle() }
        }
        b.show()
    }

    /** Tovar tanlash oynasi (qidiruv bilan). */
    fun pickProduct(f: Fragment, subtitle: String, onCancel: () -> Unit, onPick: (Product) -> Unit) {
        val ctx = f.requireContext()
        val all = ServiceLocator.catalog.state.value.products.sortedBy { it.name.lowercase() }
        val keys = all.map { Fold.key(it.name) }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 8, 48, 0) }
        val til = TextInputLayout(ctx).apply { hint = ctx.getString(R.string.search_hint) }
        val edt = TextInputEditText(til.context).apply { isSingleLine = true }
        til.addView(edt)
        box.addView(til)
        var shown = all
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, ArrayList(all.map { it.name }))
        val list = ListView(ctx).apply { this.adapter = adapter }
        box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (ctx.resources.displayMetrics.heightPixels * 0.45).toInt()))
        edt.onTextChanged { q ->
            val k = Fold.key(q)
            shown = if (k.isBlank()) all else all.filterIndexed { i, _ -> keys[i].contains(k) || all[i].id == q.trim() }
            adapter.clear(); adapter.addAll(shown.map { it.name }); adapter.notifyDataSetChanged()
        }
        val d = MaterialAlertDialogBuilder(ctx).setTitle(R.string.scan_pick_title).setMessage(subtitle).setView(box)
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
        list.setOnItemClickListener { _, _, pos, _ ->
            shown.getOrNull(pos)?.let { d.dismiss(); onPick(it) }
        }
        edt.requestFocus()
        list.visibility = View.VISIBLE
    }

    /** Ulanadigan skaner (klaviatura kabi ishlaydi) yozgan matn shtrix kodmi: 8+ raqam yoki harf-raqam (bo'shliqsiz). */
    fun looksScanned(text: String): Boolean {
        val t = text.trim()
        if (t.length < 8 || t.any { it.isWhitespace() }) return false
        return t.all { it.isDigit() } || (t.any { it.isDigit() } && t.all { it.isLetterOrDigit() || it in "-./" } && t.count { it.isDigit() } >= t.length / 2)
    }
}
