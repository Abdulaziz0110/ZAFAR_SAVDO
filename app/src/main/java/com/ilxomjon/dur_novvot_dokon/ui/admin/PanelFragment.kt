package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.delay
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.Panel
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * v5.3: kassadagi savdo oynasining "PANELI" — telefondan.
 *
 * Kompyuterda 8 sahifa, har birida 42 ta tez tovar tugmasi bor (1C'da Константы.тов1 … тов336). Avval ular faqat
 * kompyuterda, bittalab qo'lda yozilar edi. Bu yerda:
 *   * tugma bosiladi -> tovar tanlanadi (yoki bo'shatiladi);
 *   * "Ko'p sotilganlar bilan to'ldirish" — shu sahifadagi bo'sh joylarga eng ko'p sotilgan, panelda yo'q tovarlar qo'yiladi;
 *   * hech narsa darhol yozilmaydi: o'zgarishlar belgilanadi, "Saqlash" bosilganda bitta so'rov bo'lib 1C'ga ketadi.
 * Kassadagi oyna qayta ochilganda (yoki "yangilash" tugmasi) yangi tugmalar ko'rinadi.
 */
class PanelFragment : CodeFragment() {
    private lateinit var grid: LinearLayout
    private lateinit var tvInfo: TextView
    private lateinit var bSave: com.google.android.material.button.MaterialButton
    private var pageIndex = 0
    private val changes = LinkedHashMap<Int, Panel.Slot>()
    private var sending = false

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.pn_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.pn_hint), 4))
        c.addView(Ui.toggle(ctx, (1..Panel.PAGES).map { it.toString() }, pageIndex, 8) { i -> pageIndex = i; render() })
        tvInfo = Ui.hint(ctx, "", 6).also { c.addView(it) }
        grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(ctx, 8) })
        c.addView(Ui.button(ctx, getString(R.string.pn_fill), outlined = true, top = 12, icon = R.drawable.ic_trending) { fill() })
        bSave = Ui.button(ctx, getString(R.string.pn_save, 0), top = 8, icon = R.drawable.ic_check) { save() }
        c.addView(bSave)
        c.addView(Ui.button(ctx, getString(R.string.sp_refresh), outlined = true, top = 8, icon = R.drawable.ic_sync) { refresh() })
        c.addView(Ui.hint(ctx, getString(R.string.pn_note), 8))
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { ServiceLocator.onec.load(); if (ServiceLocator.onec.state.value.suppliers?.panel == null) refresh(quiet = true) }
        launchOnStarted { ServiceLocator.onec.state.collect { render() } }
    }

    private fun panel(): Panel? = ServiceLocator.onec.state.value.suppliers?.panel

    private fun slot(n: Int): Panel.Slot = changes[n] ?: panel()?.slots?.getOrNull(n - 1) ?: Panel.EMPTY

    private fun render() {
        if (!::grid.isInitialized) return
        val pn = panel()
        val s = ServiceLocator.onec.state.value.suppliers
        tvInfo.text = when {
            pn == null -> getString(R.string.pn_none)
            else -> getString(R.string.pn_info, pn.slots.count { !it.isEmpty }, Panel.SLOTS, formatDateTime(s?.at ?: 0L))
        }
        bSave.text = getString(R.string.pn_save, changes.size)
        bSave.isEnabled = changes.isNotEmpty() && !sending
        grid.removeAllViews()
        if (pn == null) return
        val first = pageIndex * Panel.PAGE + 1
        val cols = if (resources.configuration.screenWidthDp >= 600) 6 else 3
        val gap = Ui.dp(ctx, 6)
        for (row in (first until first + Panel.PAGE).chunked(cols)) {
            val r = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, n) in row.withIndex()) {
                r.addView(cell(n), LinearLayout.LayoutParams(0, Ui.dp(ctx, 76), 1f).apply { if (i > 0) marginStart = gap; bottomMargin = gap })
            }
            grid.addView(r)
        }
    }

    private fun cell(n: Int): View {
        val s = slot(n)
        val changed = n in changes
        val card = MaterialCardView(ctx, null, if (s.isEmpty && !changed) com.google.android.material.R.attr.materialCardViewOutlinedStyle else com.google.android.material.R.attr.materialCardViewFilledStyle).apply {
            radius = Ui.dp(ctx, 12).toFloat()
            isClickable = true
            isFocusable = true
            if (changed) setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer))
            setOnClickListener { pick(n) }
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = Ui.dp(ctx, 6)
            setPadding(pad, pad, pad, pad)
        }
        box.addView(
            Ui.text(ctx, if (s.isEmpty) getString(R.string.pn_empty) else s.name, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge).apply {
                gravity = Gravity.CENTER
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                if (s.isEmpty) setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline))
            },
        )
        card.addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return card
    }

    private fun set(n: Int, s: Panel.Slot) {
        val orig = panel()?.slots?.getOrNull(n - 1) ?: Panel.EMPTY
        if (orig.code == s.code && orig.name == s.name) changes.remove(n) else changes[n] = s
        render()
    }

    private fun pick(n: Int) {
        val cur = slot(n)
        val rows = ArrayList<Ui.MenuRow>()
        rows += Ui.MenuRow(getString(R.string.pn_choose), getString(R.string.pn_choose_sub), R.drawable.ic_search, 1) {
            Pickers.product(ctx) { p -> set(n, Panel.Slot(p.id, p.name)) }
        }
        // 5 ta tayyor taklif: ko'p sotilgan, panelda yo'q
        val byId = ServiceLocator.catalog.state.value.productsById
        val have = HashSet(panel()?.codes.orEmpty()).apply { addAll(changes.values.map { it.code }) }
        for (code in topSellers().asSequence().filter { it !in have && byId[it] != null }.take(5)) {
            val p = byId[code] ?: continue
            rows += Ui.MenuRow(p.name, getString(R.string.pn_top), R.drawable.ic_trending) { set(n, Panel.Slot(p.id, p.name)) }
        }
        if (!cur.isEmpty) rows += Ui.MenuRow(getString(R.string.pn_clear), "", R.drawable.ic_delete, 3) { set(n, Panel.EMPTY) }
        if (n in changes) rows += Ui.MenuRow(getString(R.string.pn_revert), "", R.drawable.ic_undo) { changes.remove(n); render() }
        Ui.menuSheet(ctx, getString(R.string.pn_slot, pageIndex + 1, n - pageIndex * Panel.PAGE) + if (cur.isEmpty) "" else " · " + cur.name, rows)
    }

    /**
     * Eng ko'p sotilgan tovarlar (kodlari, ko'pidan kamiga): 1C'dan kelgan savdo hujjatlarida NECHTA CHEKDA uchragani
     * bo'yicha — "tez ketadigan" degani shu (bitta chekdagi katta miqdor emas).
     */
    private fun topSellers(): List<String> {
        val docs = ServiceLocator.onec.state.value.sales?.docs.orEmpty()
        val n = HashMap<String, Int>()
        for (d in docs) {
            if (d.kind != OneCDoc.SALE && d.kind != "z" && d.kind != "m") continue
            for (code in d.lines.mapNotNullTo(HashSet()) { it.code.takeIf { c -> c.isNotEmpty() } }) n[code] = (n[code] ?: 0) + 1
        }
        return n.entries.sortedByDescending { it.value }.map { it.key }
    }

    private fun fill() {
        val pn = panel() ?: run { snack(getString(R.string.pn_none)); return }
        val top = topSellers()
        if (top.isEmpty()) { snack(getString(R.string.pn_no_sales)); return }
        // hozirgi (saqlanmagan) holat ustida hisoblanadi
        val now = Panel((1..Panel.SLOTS).map { slot(it) })
        val byId = ServiceLocator.catalog.state.value.productsById
        val free = now.page(pageIndex).count { it.isEmpty }
        val sug = Panel.suggest(now, top.filter { byId[it] != null }, pageIndex, free)
            .filterKeys { it > pageIndex * Panel.PAGE && it <= (pageIndex + 1) * Panel.PAGE }
        if (sug.isEmpty()) { snack(getString(if (free == 0) R.string.pn_page_full else R.string.pn_no_more)); return }
        for ((slotNo, code) in sug) byId[code]?.let { changes[slotNo] = Panel.Slot(it.id, it.name) }
        render()
        snack(getString(R.string.pn_filled, sug.size))
        if (pn.slots.isEmpty()) return
    }

    private fun save() {
        if (sending || changes.isEmpty()) return
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        val req = WriteRequest(WriteCodec.newId(), WriteKind.PANEL, System.currentTimeMillis(), lines = Panel.lines(changes))
        if (req.problem() != null) { snack(getString(R.string.kf_err_other)); return }
        sending = true
        render()
        launchUi {
            try {
                val item = ServiceLocator.onec.submit(req, "Admin")
                changes.clear()
                snack(getString(if (item.sendError.isEmpty()) R.string.wf_sent else R.string.wf_sent_later))
                // 1C yozgach yangi holatni olib kelamiz
                delay(40_000)
                refresh(quiet = true)
            } finally {
                sending = false
                if (view != null) render()
            }
        }
    }

    private fun refresh(quiet: Boolean = false) {
        if (!ServiceLocator.onec.ready) { if (!quiet) snack(getString(R.string.oc_no_cloud)); return }
        launchUi {
            val f = SimpleDateFormat("yyyyMMdd", Locale.US)
            val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -89) }.time
            ServiceLocator.onec.requestData("supp", f.format(from), f.format(java.util.Date()))
            if (!quiet) snack(getString(R.string.oc_request_sent))
            repeat(12) {
                delay(15_000)
                runCatching { ServiceLocator.share.processInbox() }
                if ((ServiceLocator.onec.state.value.suppliers?.at ?: 0L) > ServiceLocator.onec.state.value.requestedAt) return@launchUi
            }
        }
    }
}
