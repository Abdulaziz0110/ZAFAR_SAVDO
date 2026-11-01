package com.ilxomjon.dur_novvot_dokon.ui.scan

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeBatch
import com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import com.ilxomjon.dur_novvot_dokon.ui.admin.Pickers
import com.ilxomjon.dur_novvot_dokon.ui.admin.WritesFragment
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui

/**
 * v3.12 "Shtrix kod → 1C": istalgan shtrix kodni skanerlab (kamera yoki skaner qurilmasi), tovarini tanlash —
 * ro'yxatga yig'iladi; bitta "1C'ga yozish" tugmasi bilan hammasi 1C'dagi o'sha tovarning shtrix kodlari ro'yxatiga
 * (Справочник.Штрих va tovarning shtrix kodlar jadvali) yoziladi. Admin — kompyuter orqali, Agent — Admin orqali.
 */
class BarcodeBatchFragment : CodeFragment() {
    private val batch get() = ServiceLocator.barcodeBatch
    private lateinit var list: LinearLayout
    private lateinit var tvCount: TextView
    private lateinit var btnSend: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var eCode: TextInputEditText
    private var sending = false

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.bb_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(if (AppRole.isAdmin) R.string.bb_hint_admin else R.string.bb_hint_agent), 4))
        c.addView(Ui.button(ctx, getString(R.string.bb_scan), icon = R.drawable.ic_barcode, top = 12) { navigator().push(ScanFragment.batch()) })
        val (til, e) = Ui.field(ctx, getString(R.string.bb_manual), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        eCode = e
        til.setEndIconDrawable(R.drawable.ic_add)
        til.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM
        til.setEndIconOnClickListener { manual() }
        til.helperText = getString(R.string.bb_manual_hint)
        e.imeOptions = EditorInfo.IME_ACTION_DONE
        // Skaner qurilmasi (USB/Bluetooth) kodni yozib Enter bosadi
        e.setOnEditorActionListener { _, action, ev ->
            if (action == EditorInfo.IME_ACTION_DONE || (ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) { manual(); true } else false
        }
        c.addView(til)
        tvCount = Ui.section(ctx, "").also { c.addView(it) }
        val (card, col) = Ui.card(ctx)
        list = col
        c.addView(card)
        btnSend = Ui.button(ctx, "", icon = R.drawable.ic_check, top = 16) { send() }
        c.addView(btnSend)
        btnClear = Ui.button(ctx, getString(R.string.bb_clear), outlined = true, icon = R.drawable.ic_delete) { clearAll() }
        c.addView(btnClear)
        if (AppRole.isAdmin) {
            c.addView(Ui.item(ctx, getString(R.string.ad_writes), getString(R.string.bb_results_sub), icon = R.drawable.ic_receipt) { navigator().push(WritesFragment()) })
        }
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { batch.load(); ServiceLocator.barcodes.load() }
        launchOnStarted { batch.items.collect { render(it) } }
    }

    private fun render(items: List<BarcodeBatch.Item>) {
        if (!::list.isInitialized) return
        list.removeAllViews()
        tvCount.text = getString(R.string.bb_list, items.size)
        if (items.isEmpty()) list.addView(Ui.hint(ctx, getString(R.string.bb_empty), 0))
        val byId = ServiceLocator.catalog.state.value.productsById
        items.forEachIndexed { i, it ->
            if (i > 0) list.addView(Ui.divider(ctx))
            val name = byId[it.productId]?.name ?: it.name
            list.addView(Ui.item(ctx, name, getString(R.string.bb_item_sub, it.code), icon = R.drawable.ic_barcode) { _ -> itemMenu(it) })
        }
        btnSend.text = getString(R.string.bb_send, items.size)
        btnSend.isEnabled = items.isNotEmpty() && !sending
        btnClear.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun itemMenu(it: BarcodeBatch.Item) {
        MaterialAlertDialogBuilder(ctx).setTitle(it.code)
            .setItems(arrayOf(getString(R.string.bb_change_product), getString(R.string.wf_remove))) { _, w ->
                if (w == 0) Pickers.product(ctx) { p -> launchUi { batch.put(it.code, p.id, p.name) } }
                else launchUi { batch.remove(it.code) }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Qo'lda yoki skaner qurilmasidan kiritilgan kod: avval tekshiradi, keyin tovar tanlanadi. */
    private fun manual() {
        val raw = eCode.text?.toString().orEmpty()
        val code = BarcodeBatch.clean(raw)
        if (!BarcodeBatch.valid(code)) { snack(getString(R.string.bb_bad_code)); return }
        eCode.setText("")
        launchUi {
            when (val s = ServiceLocator.barcodes.resolve(code)) {
                is BarcodeRepository.Scan.Found -> { snack(getString(R.string.link_known, s.product.name)); return@launchUi }
                else -> Unit
            }
            val suggestion = (ServiceLocator.barcodes.resolve(code) as? BarcodeRepository.Scan.Unknown)?.suggestion
            if (suggestion != null) askSuggestion(code, suggestion) else pickFor(code)
        }
    }

    private fun askSuggestion(code: String, p: Product) {
        MaterialAlertDialogBuilder(ctx).setTitle(code).setMessage(getString(R.string.scan_pending_suggest, p.name))
            .setPositiveButton(R.string.scan_yes) { _, _ -> launchUi { batch.put(code, p.id, p.name) } }
            .setNegativeButton(R.string.bb_pick_other) { _, _ -> pickFor(code) }.show()
    }

    private fun pickFor(code: String) = Pickers.product(ctx) { p -> launchUi { batch.put(code, p.id, p.name); snack(getString(R.string.bb_added, p.name)) } }

    private fun clearAll() {
        MaterialAlertDialogBuilder(ctx).setMessage(R.string.bb_clear_q)
            .setPositiveButton(R.string.bb_clear) { _, _ -> launchUi { batch.clear() } }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun send() {
        val items = batch.items.value
        if (items.isEmpty() || sending) return
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.bb_confirm_title).setMessage(getString(R.string.bb_confirm, items.size))
            .setPositiveButton(R.string.bb_confirm_yes) { _, _ -> doSend(items) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun doSend(items: List<BarcodeBatch.Item>) {
        sending = true
        btnSend.isEnabled = false
        launchUi {
            try {
                val n = try {
                    ServiceLocator.queueBarcodes(items)
                } catch (e: com.ilxomjon.dur_novvot_dokon.data.remote.ServerException) {
                    snack(getString(if (e.kind == com.ilxomjon.dur_novvot_dokon.data.remote.ServerError.QUERY) R.string.bb_admin_old else R.string.bb_admin_offline))
                    return@launchUi
                }
                if (n < 0) { snack(getString(R.string.bb_no_route)); return@launchUi }
                // Shu telefonda darhol ishlasin (1C'dan kodlar qaytib kelguncha)
                ServiceLocator.barcodes.learnSilently(items.map { it.code to it.productId })
                batch.removeAll(items.map { it.code })
                if (AppRole.isAdmin) {
                    snack(getString(R.string.bb_sent_admin, n))
                    navigator().push(WritesFragment())
                } else snack(getString(R.string.bb_sent_agent, n))
            } finally {
                sending = false
                if (::btnSend.isInitialized) btnSend.isEnabled = batch.items.value.isNotEmpty()
            }
        }
    }
}
