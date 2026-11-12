package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.BulkField
import uz.imsoft.shared.onec.BulkOp
import uz.imsoft.shared.onec.BulkPlan
import uz.imsoft.shared.onec.GoodsField
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest

/**
 * v5.2 (A11): KO'P tovarga birdan amal — nomdagi belgini olib tashlash, nomga matn qo'shish,
 * maydonni o'zgartirish. Kompyuter COM orqali bajaradi (1C oynasida tugma bosilmaydi), telefon
 * o'chsa ham oxirigacha boradi.
 *
 * Tartib: amalni tuzish -> KO'RIB CHIQISH (nechta tovar, 20 ta misol) -> tasdiq -> bajarish.
 * Eski qiymatlar saqlanadi: "Yozuvlar" ro'yxatidan bitta tugma bilan qaytariladi.
 */
class BulkFragment : CodeFragment() {
    private var op = BulkOp.REPLACE
    private var where = "end"
    private var fieldKey = GoodsField.MARKUP
    private lateinit var box: LinearLayout
    private lateinit var tvPreview: android.widget.TextView
    private var eFind: TextInputEditText? = null
    private var eTo: TextInputEditText? = null
    private var eText: TextInputEditText? = null
    private var eIndex: TextInputEditText? = null
    private var eValue: TextInputEditText? = null
    private var eQty: TextInputEditText? = null
    private var eGroup: TextInputEditText? = null
    private var eMatch: TextInputEditText? = null
    private var eCodes: TextInputEditText? = null

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.bk_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.bk_hint), 4))
        c.addView(Ui.section(ctx, getString(R.string.bk_op)))
        c.addView(
            Ui.toggle(
                ctx,
                listOf(
                    getString(R.string.bk_op_repl), getString(R.string.bk_op_add),
                    getString(R.string.bk_op_field), getString(R.string.bk_op_stock),
                ),
                0, 4,
            ) { i ->
                op = listOf(BulkOp.REPLACE, BulkOp.ADD, BulkOp.FIELD, BulkOp.STOCK)[i]
                renderOp()
            },
        )
        box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(box)

        c.addView(Ui.section(ctx, getString(R.string.bk_scope)))
        c.addView(Ui.hint(ctx, getString(R.string.bk_scope_hint), 0))
        eGroup = Ui.field(ctx, getString(R.string.bk_s_group)).also { c.addView(it.first) }.second
        eMatch = Ui.field(ctx, getString(R.string.bk_s_match)).also { c.addView(it.first) }.second
        eCodes = Ui.field(ctx, getString(R.string.bk_s_codes)).also { c.addView(it.first) }.second

        tvPreview = Ui.text(ctx, "", top = 12).also { c.addView(it) }
        c.addView(
            Ui.buttonRow(
                ctx,
                Ui.button(ctx, getString(R.string.bk_preview), outlined = true, icon = R.drawable.ic_info) { preview(false) },
                Ui.button(ctx, getString(R.string.bk_run), icon = R.drawable.ic_check) { preview(true) },
            ),
        )
        c.addView(Ui.hint(ctx, getString(R.string.bk_undo_note), 8))
        renderOp()
        return p.root
    }

    private fun renderOp() {
        box.removeAllViews()
        when (op) {
            BulkOp.REPLACE -> {
                eFind = Ui.field(ctx, getString(R.string.bk_find)).also { box.addView(it.first) }.second
                box.addView(Ui.hint(ctx, getString(R.string.bk_find_hint), 0))
                eTo = Ui.field(ctx, getString(R.string.bk_to)).also { box.addView(it.first) }.second
            }
            BulkOp.ADD -> {
                eText = Ui.field(ctx, getString(R.string.bk_text)).also { box.addView(it.first) }.second
                box.addView(
                    Ui.toggle(
                        ctx,
                        listOf(getString(R.string.bk_w_start), getString(R.string.bk_w_end), getString(R.string.bk_w_index)),
                        1, 4,
                    ) { i ->
                        where = listOf("start", "end", "idx")[i]
                        eIndex?.let { e -> (e.parent?.parent as? View)?.visibility = if (where == "idx") View.VISIBLE else View.GONE }
                    },
                )
                val til = Ui.field(ctx, getString(R.string.bk_index), InputType.TYPE_CLASS_NUMBER)
                eIndex = til.second
                til.first.visibility = View.GONE
                box.addView(til.first)
            }
            BulkOp.STOCK -> {
                eQty = Ui.field(ctx, getString(R.string.bk_qty), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                    .also { box.addView(it.first) }.second
                box.addView(Ui.hint(ctx, getString(R.string.bk_qty_hint), 0))
            }
            else -> {
                val keys = BulkField.FIELDS.toList()
                box.addView(
                    Ui.item(ctx, getString(R.string.bk_field), fieldName(fieldKey), icon = R.drawable.ic_tune) {
                        Ui.menuSheet(
                            ctx, getString(R.string.bk_field),
                            keys.map { k -> Ui.MenuRow(fieldName(k), "", R.drawable.ic_tune, if (k == fieldKey) 1 else 0) { fieldKey = k; renderOp() } },
                        )
                    },
                )
                eValue = Ui.field(ctx, getString(R.string.bk_value)).also { box.addView(it.first) }.second
            }
        }
    }

    private fun fieldName(key: String): String = getString(
        when (key) {
            GoodsField.GROUP -> R.string.ng_group
            GoodsField.BRAND -> R.string.ng_brand
            GoodsField.UNIT -> R.string.ng_unit
            GoodsField.VOLUME -> R.string.ng_volume
            GoodsField.PACK -> R.string.ng_pack
            GoodsField.MARKUP -> R.string.ng_markup
            GoodsField.SCALE -> R.string.ng_scale
            GoodsField.COST -> R.string.of_cost
            GoodsField.P1 -> R.string.of_p1
            GoodsField.P2 -> R.string.of_p2
            GoodsField.P3 -> R.string.of_p3
            else -> R.string.of_p4
        },
    )

    private fun txt(e: TextInputEditText?): String = e?.text?.toString()?.trim().orEmpty()

    private fun fields(): Map<String, String> {
        val f = HashMap<String, String>()
        f[BulkField.OP] = op
        when (op) {
            BulkOp.REPLACE -> { f[BulkField.FIND] = txt(eFind); f[BulkField.TO] = txt(eTo) }
            BulkOp.ADD -> {
                f[BulkField.TEXT] = txt(eText)
                f[BulkField.WHERE] = if (where == "idx") txt(eIndex).ifEmpty { "0" } else where
            }
            BulkOp.STOCK -> f[BulkField.QTY] = txt(eQty)
            else -> { f[BulkField.KEY] = fieldKey; f[BulkField.VALUE] = txt(eValue) }
        }
        f[BulkField.GROUP] = txt(eGroup)
        f[BulkField.MATCH] = txt(eMatch)
        f[BulkField.CODES] = txt(eCodes)
        return f
    }

    /** Katalogdagi tovarlar (kod, nom, guruh nomi) — ko'rib chiqish telefonda hisoblanadi. */
    private fun items(): List<Triple<String, String, String>> {
        val st = ServiceLocator.catalog.state.value
        val groups = st.groups.associate { it.id to it.name }
        return st.products.map { Triple(it.id, it.name, groups[it.groupId].orEmpty()) }
    }

    private fun preview(run: Boolean) {
        val f = fields()
        val why = BulkPlan.problem(f)
        if (why != null) { snack(getString(R.string.bk_bad, why)); return }
        val p = BulkPlan.preview(f, items())
        val sb = StringBuilder(getString(R.string.bk_result, p.changed, p.total)).append("\n\n")
        for ((code, before, after) in p.samples) {
            sb.append(code).append(") ").append(before).append("\n   → ").append(after).append('\n')
        }
        if (p.changed > p.samples.size) sb.append(getString(R.string.bk_more, p.changed - p.samples.size))
        tvPreview.text = sb.toString()
        if (!run) return
        if (p.nothing) { snack(getString(R.string.bk_nothing)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.bk_confirm_title)
            .setMessage(getString(R.string.bk_confirm, p.changed))
            .setPositiveButton(R.string.wf_confirm_yes) { _, _ -> send(f) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun send(f: Map<String, String>) {
        val req = WriteRequest(WriteCodec.newId(), WriteKind.BULK, System.currentTimeMillis(), fields = f)
        launchUi {
            val item = ServiceLocator.onec.submit(req, "Admin")
            snack(getString(if (item.sendError.isEmpty()) R.string.wf_sent else R.string.wf_sent_later))
            navigator().pop()
            navigator().push(WritesFragment())
        }
    }
}
