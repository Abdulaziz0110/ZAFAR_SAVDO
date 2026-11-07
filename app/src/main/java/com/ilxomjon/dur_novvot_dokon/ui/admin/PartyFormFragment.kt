package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.PartyField
import uz.imsoft.shared.onec.WriteCodec
import uz.imsoft.shared.onec.WriteKind
import uz.imsoft.shared.onec.WriteRequest

/**
 * v4.1 Admin: yangi mijoz / ta'minotchi (Справочник.Контрагенты) yoki mavjudini tahrirlash (nom, telefon, manzil,
 * region). Tahrirlashda faqat o'zgargan maydon yuboriladi. Kompyuter 1C'ga kiritadi; 1C'dagi kabi papka
 * (Клиенты / Поставщики) tanlanadi.
 */
class PartyFormFragment : CodeFragment() {
    private val kind: String get() = arguments?.getString(ARG_KIND) ?: WriteKind.PARTY_NEW
    private val isEdit get() = kind == WriteKind.PARTY_EDIT
    private var group = PartyField.CLIENTS
    private var party = ""
    private var partyCode = ""
    private var region = ""
    private val inputs = LinkedHashMap<String, TextInputEditText>()
    private val before = HashMap<String, String>()
    private lateinit var tvParty: TextView
    private lateinit var tvRegion: TextView
    private var sending = false

    override fun build(inflater: LayoutInflater): View {
        val p = page(if (isEdit) R.string.kf_edit_title else R.string.kf_new_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(if (isEdit) R.string.kf_edit_hint else R.string.kf_new_hint), 4))
        if (isEdit) {
            c.addView(Ui.section(ctx, getString(R.string.kf_who)))
            tvParty = Ui.title(ctx, "", 4).also { c.addView(it) }
            c.addView(Ui.buttonRow(ctx,
                Ui.button(ctx, getString(R.string.kf_pick_client), outlined = true, top = 0, icon = R.drawable.ic_person) { StorePickerSheet.show(childFragmentManager, REQ_STORE) },
                Ui.button(ctx, getString(R.string.kf_pick_supplier), outlined = true, top = 0, icon = R.drawable.ic_truck) { pickSupplier() },
            ))
        } else {
            c.addView(Ui.section(ctx, getString(R.string.kf_group)))
            c.addView(Ui.toggle(ctx, listOf(getString(R.string.kf_clients), getString(R.string.kf_suppliers)), 0, 4) { i ->
                group = if (i == 1) PartyField.SUPPLIERS else PartyField.CLIENTS
            })
        }
        c.addView(Ui.section(ctx, getString(R.string.kf_data)))
        inputs[PartyField.NAME] = Ui.field(ctx, getString(R.string.kf_name)).also { c.addView(it.first) }.second
        inputs[PartyField.PHONE] = Ui.field(ctx, getString(R.string.kf_phone), InputType.TYPE_CLASS_PHONE).also { c.addView(it.first) }.second
        inputs[PartyField.PHONE2] = Ui.field(ctx, getString(R.string.kf_phone2), InputType.TYPE_CLASS_PHONE).also { c.addView(it.first) }.second
        inputs[PartyField.ADDRESS] = Ui.field(ctx, getString(R.string.kf_address), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).also { c.addView(it.first) }.second
        // v5.1: 1C kartochkasidagi qolgan maydonlar
        inputs[PartyField.INN] = Ui.field(ctx, getString(R.string.kf_inn), InputType.TYPE_CLASS_NUMBER).also { c.addView(it.first) }.second
        inputs[PartyField.LIMIT] = Ui.field(ctx, getString(R.string.kf_limit), Ui.NUMBER).also { c.addView(it.first) }.second
        c.addView(Ui.text(ctx, getString(R.string.kf_region), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 10))
        tvRegion = Ui.text(ctx, "", top = 2).also { c.addView(it) }
        c.addView(Ui.button(ctx, getString(R.string.kf_pick_region), outlined = true, icon = R.drawable.ic_list) { pickRegion() })
        if (isEdit) c.addView(Ui.hint(ctx, getString(R.string.kf_edit_note), 8))
        c.addView(Ui.button(ctx, getString(R.string.wf_confirm), top = 14, icon = R.drawable.ic_check) { confirm() })
        c.addView(Ui.hint(ctx, getString(R.string.wf_safety), 8))
        render()
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, b ->
            val st = ServiceLocator.catalog.storeById(b.getString(StorePickerSheet.KEY_ID).orEmpty()) ?: return@setFragmentResultListener
            choose(st.name, st.id, st.regionName)
        }
        launchUi { ServiceLocator.onec.load() }
    }

    private fun render() {
        if (isEdit && ::tvParty.isInitialized) tvParty.text = party.ifEmpty { getString(R.string.wf_not_chosen) }
        if (::tvRegion.isInitialized) tvRegion.text = region.ifEmpty { getString(R.string.kf_region_none) }
    }

    /** Tahrirlash uchun tanlandi: hozirgi qiymatlar maydonlarga (o'zgarmaganlari yuborilmaydi). */
    private fun choose(name: String, code: String, reg: String, phone: String = "") {
        party = name
        partyCode = code
        before.clear()
        before[PartyField.NAME] = name
        before[PartyField.PHONE] = phone
        inputs[PartyField.NAME]?.setText(name)
        inputs[PartyField.PHONE]?.setText(phone)
        region = reg
        before[PartyField.REGION] = reg
        render()
    }

    private fun pickSupplier() {
        val list = ServiceLocator.onec.state.value.suppliers?.list.orEmpty()
        if (list.isEmpty()) { snack(getString(R.string.kf_no_suppliers)); return }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_supplier).setItems(list.map { it.name }.toTypedArray()) { _, i ->
            choose(list[i].name, list[i].code, "", list[i].phone)
        }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Regionlar — mijozlar ro'yxatidagilar (1C'dagi Регион nomlari). */
    private fun pickRegion() {
        val regions = ServiceLocator.catalog.state.value.stores.map { it.regionName.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        if (regions.isEmpty()) { snack(getString(R.string.kf_no_regions)); return }
        val items = (listOf(getString(R.string.kf_region_none)) + regions).toTypedArray()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.kf_region).setItems(items) { _, i ->
            region = if (i == 0) "" else regions[i - 1]
            render()
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun fields(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, e) in inputs) {
            val v = e.text?.toString()?.trim()?.let { if (k == PartyField.NAME) it.replace(Regex("\\s+"), " ") else it }.orEmpty()
            if (isEdit) {
                if (v.isEmpty() || v == before[k]) continue
            } else if (v.isEmpty()) continue
            out[k] = v
        }
        if (region.isNotEmpty() && (!isEdit || region != before[PartyField.REGION])) out[PartyField.REGION] = region
        if (!isEdit) out[PartyField.GROUP] = group
        return out
    }

    private fun confirm() {
        if (sending) return
        val r = WriteRequest(WriteCodec.newId(), kind, System.currentTimeMillis(), party, partyCode, fields = fields())
        when (r.problem()) {
            null -> Unit
            "party" -> { snack(getString(R.string.wf_err_party)); return }
            "name" -> { snack(getString(R.string.kf_err_name)); return }
            "phone" -> { snack(getString(R.string.kf_err_phone)); return }
            "empty" -> { snack(getString(R.string.kf_err_empty)); return }
            else -> { snack(getString(R.string.kf_err_other)); return }
        }
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        val names = mapOf(PartyField.NAME to R.string.kf_name, PartyField.PHONE to R.string.kf_phone, PartyField.PHONE2 to R.string.kf_phone2,
            PartyField.ADDRESS to R.string.kf_address, PartyField.REGION to R.string.kf_region,
            PartyField.INN to R.string.kf_inn, PartyField.LIMIT to R.string.kf_limit)
        val summary = buildString {
            if (isEdit) append(party).append('\n')
            else append(getString(if (group == PartyField.SUPPLIERS) R.string.kf_suppliers else R.string.kf_clients)).append('\n')
            for ((k, v) in r.fields) {
                val res = names[k] ?: continue
                val old = before[k]?.takeIf { isEdit && it.isNotEmpty() }
                append("• ").append(getString(res)).append(": ").append(if (old != null) "$old → $v" else v).append('\n')
            }
            append('\n').append(getString(R.string.wf_confirm_text))
        }
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_confirm_title).setMessage(summary)
            .setPositiveButton(R.string.wf_confirm_yes) { _, _ ->
                sending = true
                launchUi {
                    try {
                        val item = ServiceLocator.onec.submit(r, "Admin")
                        snack(getString(if (item.sendError.isEmpty()) R.string.wf_sent else R.string.wf_sent_later))
                        navigator().pop()
                        navigator().push(WritesFragment())
                    } finally {
                        sending = false
                    }
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    companion object {
        private const val ARG_KIND = "kind"
        private const val REQ_STORE = "kf_store"

        fun of(kind: String) = PartyFormFragment().apply { arguments = Bundle().apply { putString(ARG_KIND, kind) } }
    }
}

