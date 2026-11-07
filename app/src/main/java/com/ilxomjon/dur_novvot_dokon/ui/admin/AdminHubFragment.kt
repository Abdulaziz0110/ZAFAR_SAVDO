package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.flow.combine
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.WriteKind

/**
 * v5.0 "Boshqaruv": tepada qidiruv — kerakli ishni nomi bilan topasiz ("narx", "kirim", "qurilma").
 * Pastida beshta bo'lim: 1C'dan ko'rish, 1C'ga yozish, Odamlar, Tovarlar, Ilova.
 * Har ish faqat bitta joyda turadi.
 */
class AdminHubFragment : CodeFragment() {
    private class Act(val title: String, val sub: String, val icon: Int, val tone: Int = 0, val go: () -> Unit)

    private lateinit var listBox: LinearLayout
    private var query = ""
    private var tvSales: android.widget.TextView? = null
    private var tvWrites: android.widget.TextView? = null

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.ad_title)
        val c = p.content
        val (til, e) = Ui.field(ctx, getString(R.string.ad_search), InputType.TYPE_CLASS_TEXT, top = 4)
        c.addView(til)
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s?.toString().orEmpty().trim()
                render()
            }
        })
        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(listBox)
        render()
        return p.root
    }

    private fun sections(): List<Pair<Int, List<Act>>> {
        val W = WriteKind
        fun go(f: () -> androidx.fragment.app.Fragment): () -> Unit = { navigator().push(f()) }
        val view = listOf(
            Act(getString(R.string.ad_sales), getString(R.string.ad_sales_sub), R.drawable.ic_receipt, 1, go { OneCSalesFragment() }),
            Act(getString(R.string.rp_title), getString(R.string.rp_sub), R.drawable.ic_trending, 1, go { ReportsFragment() }),
            Act(getString(R.string.ad_supp), getString(R.string.ad_supp_sub), R.drawable.ic_truck, 0, go { SuppliersFragment() }),
            Act(getString(R.string.ad_writes), getString(R.string.ad_writes_sub), R.drawable.ic_sync_alt, 0, go { WritesFragment() }),
            Act(getString(R.string.ad_pc), getString(R.string.ad_pc_sub), R.drawable.ic_computer, 0, go { PcFragment() }),
        )
        val write = listOf(
            Act(getString(R.string.of_new_goods), getString(R.string.of_new_goods_sub), R.drawable.ic_add_box, 1, go { OneCFormFragment.of(W.NEW_GOODS) }),
            Act(getString(R.string.of_goods_edit), getString(R.string.of_goods_edit_sub), R.drawable.ic_price, 0, go { OneCFormFragment.of(W.GOODS_EDIT) }),
            Act(getString(R.string.ps_title), getString(R.string.ps_sub), R.drawable.ic_calculate, 1, go { PriceSetFragment() }),
            Act(getString(R.string.ad_new_receipt), getString(R.string.ad_new_receipt_sub), R.drawable.ic_inventory, 1, go { WriteFormFragment.of(W.RECEIPT, "", "") }),
            Act(getString(R.string.of_client_pay), getString(R.string.of_client_pay_sub), R.drawable.ic_wallet, 0, go { OneCFormFragment.of(W.CLIENT_PAY) }),
            Act(getString(R.string.ad_client_return), getString(R.string.ad_client_return_sub), R.drawable.ic_assignment_return, 0, go { WriteFormFragment.of(W.CLIENT_RETURN, "", "") }),
            Act(getString(R.string.wf_pay), getString(R.string.of_supp_pay_sub), R.drawable.ic_payments, 0, go { WriteFormFragment.of(W.SUPPLIER_PAY, "", "") }),
            Act(getString(R.string.wf_sreturn), getString(R.string.of_supp_return_sub), R.drawable.ic_undo, 0, go { WriteFormFragment.of(W.SUPPLIER_RETURN, "", "") }),
            Act(getString(R.string.of_cash_in), getString(R.string.of_cash_in_sub), R.drawable.ic_cash_in, 0, go { OneCFormFragment.of(W.CASH_IN) }),
            Act(getString(R.string.of_cash_out), getString(R.string.of_cash_out_sub), R.drawable.ic_cash_out, 0, go { OneCFormFragment.of(W.CASH_OUT) }),
            Act(getString(R.string.wo_title), getString(R.string.wo_sub), R.drawable.ic_delete, 3, go { WriteFormFragment.of(W.WRITE_OFF, "", "") }),
            Act(getString(R.string.kf_new_title), getString(R.string.kf_new_sub), R.drawable.ic_person_add, 0, go { PartyFormFragment.of(W.PARTY_NEW) }),
            Act(getString(R.string.kf_edit_title), getString(R.string.kf_edit_sub), R.drawable.ic_person_search, 0, go { PartyFormFragment.of(W.PARTY_EDIT) }),
            Act(getString(R.string.rt_title), getString(R.string.rt_sub), R.drawable.ic_currency, 0, go { RateFragment() }),
            Act(getString(R.string.bb_title), getString(R.string.bb_sub), R.drawable.ic_barcode, 0, go { com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment() }),
        )
        val people = listOf(
            Act(getString(R.string.dv_title), getString(R.string.dv_sub), R.drawable.ic_groups, 1, go { DevicesFragment() }),
            Act(getString(R.string.ad_requests), getString(R.string.ad_requests_sub), R.drawable.ic_notifications, 0, go { ClientRequestsFragment() }),
            Act(getString(R.string.ad_debtors), getString(R.string.ad_debtors_sub), R.drawable.ic_person_search, 0, go { DebtorsFragment() }),
            Act(getString(R.string.ad_share), getString(R.string.ad_share_sub), R.drawable.ic_storefront, 0, go { com.ilxomjon.dur_novvot_dokon.ui.share.ShareFragment() }),
            Act(getString(R.string.ad_receipt_style), getString(R.string.ad_receipt_style_sub), R.drawable.ic_print, 0, go { com.ilxomjon.dur_novvot_dokon.ui.share.ReceiptStyleFragment() }),
            Act(getString(R.string.ad_relay), getString(if (ServiceLocator.shareStore.relayOn) R.string.rl_on_short else R.string.rl_off_short), R.drawable.ic_hub, 0, go { com.ilxomjon.dur_novvot_dokon.ui.share.RelayFragment() }),
        )
        val goods = listOf(
            Act(getString(R.string.ad_labels), getString(R.string.ad_labels_sub), R.drawable.ic_label, 0, go { com.ilxomjon.dur_novvot_dokon.ui.labels.LabelsFragment() }),
            Act(getString(R.string.sc_title), getString(R.string.sc_sub), R.drawable.ic_scale, 0, go { ScaleFragment() }),
            Act(getString(R.string.ad_barcodes), getString(R.string.ad_barcodes_sub), R.drawable.ic_qr, 0, go { com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodesFragment() }),
        )
        val app = listOf(
            Act(getString(R.string.boss_title), getString(R.string.boss_card_sub), R.drawable.ic_trending, 1, go { BossFragment() }),
            Act(getString(R.string.stt_title), getString(R.string.sg_stats_sub), R.drawable.ic_trending, 0, go { StatsFragment() }),
            Act(getString(R.string.ad_settings), getString(R.string.ad_settings_sub), R.drawable.ic_settings, 0, go { com.ilxomjon.dur_novvot_dokon.ui.settings.SettingsHubFragment() }),
            Act(getString(R.string.au_title), getString(R.string.au_sub), R.drawable.ic_backup, 0, go { com.ilxomjon.dur_novvot_dokon.ui.settings.AutoUpdateFragment() }),
        )
        return listOf(
            R.string.ad_sec_1c to view,
            R.string.ad_sec_write to write,
            R.string.ad_sec_clients to people,
            R.string.ad_sec_goods to goods,
            R.string.ad_sec_app to app,
        )
    }

    /**
     * v5.0.3: har bo'lim plitka panjarasi (ekran keng bo'lsa 3 ustun, aks holda 2). Plitka uzoq bosilsa
     * to'liq izoh chiqadi. 1C savdolari va yozuvlar holati bo'lim ostida bir qatorda turadi.
     */
    private fun render() {
        listBox.removeAllViews()
        tvSales = null
        tvWrites = null
        val q = uz.imsoft.shared.Fold.key(query)
        val columns = if (resources.configuration.screenWidthDp >= 420) 3 else 2
        var shown = 0
        for ((title, acts) in sections()) {
            val list = if (q.isEmpty()) acts else acts.filter { uz.imsoft.shared.Fold.key(it.title + " " + it.sub).contains(q) }
            if (list.isEmpty()) continue
            shown += list.size
            listBox.addView(Ui.section(ctx, getString(title)))
            listBox.addView(
                Ui.tiles(ctx, list.map { a -> Ui.Tile(a.title, a.icon, a.sub, a.tone) { a.go() } }, columns, 4) { t ->
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle(t.title.toString()).setMessage(t.help).setPositiveButton(R.string.close, null).show()
                },
            )
            if (q.isEmpty() && title == R.string.ad_sec_1c) {
                tvSales = Ui.hint(ctx, "", 0).also { listBox.addView(it) }
                tvWrites = Ui.hint(ctx, "", 0).also { listBox.addView(it) }
            }
        }
        if (shown == 0) listBox.addView(Ui.hint(ctx, getString(R.string.ad_search_none), 16))
        updateInfo()
    }

    private fun updateInfo() {
        val o = ServiceLocator.onec.state.value
        tvSales?.text = o.sales?.let { getString(R.string.ad_sales_st, it.docs.size, formatDateTime(it.at.takeIf { t -> t > 0 } ?: 0L)) } ?: getString(R.string.ad_none_yet)
        tvWrites?.text = getString(R.string.ad_writes_st, o.writes.count { it.result == null }, o.writes.count { it.result?.ok == false })
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        launchOnStarted {
            combine(ServiceLocator.onec.state, ServiceLocator.clientRequests.state) { a, b -> a to b }.collect { updateInfo() }
        }
        launchUi { ServiceLocator.clientRequests.refresh() }
        launchUi { ServiceLocator.onec.refreshResults() }
    }
}
