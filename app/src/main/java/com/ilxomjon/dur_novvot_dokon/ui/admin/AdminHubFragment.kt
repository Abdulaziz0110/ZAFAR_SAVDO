package com.ilxomjon.dur_novvot_dokon.ui.admin

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

/**
 * v3.2 Admin "Boshqaruv": hamma qo'shimcha imkoniyatlar bo'limlarga ajratilgan (1C, mijozlar, agentlar, yorliqlar, ilova).
 * Har bo'lim ostida qisqa holat — qaysi ish kutib turgani darhol ko'rinadi.
 */
class AdminHubFragment : CodeFragment() {
    private lateinit var info1c: LinearLayout
    private var tvSales: android.widget.TextView? = null
    private var tvSupp: android.widget.TextView? = null
    private var tvWrites: android.widget.TextView? = null
    private var tvReq: android.widget.TextView? = null

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.ad_title)
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.ad_hint), 4))
        val W = uz.imsoft.shared.onec.WriteKind

        // 1C: ko'rish
        c.addView(Ui.section(ctx, getString(R.string.ad_sec_1c)))
        val (card1, col1) = Ui.card(ctx)
        info1c = col1
        col1.addView(Ui.item(ctx, getString(R.string.ad_sales), getString(R.string.ad_sales_sub), icon = R.drawable.ic_receipt) { navigator().push(OneCSalesFragment()) })
        tvSales = Ui.hint(ctx, "", 0).also { col1.addView(it) }
        col1.addView(Ui.divider(ctx))
        col1.addView(Ui.item(ctx, getString(R.string.ad_supp), getString(R.string.ad_supp_sub), icon = R.drawable.ic_truck) { navigator().push(SuppliersFragment()) })
        tvSupp = Ui.hint(ctx, "", 0).also { col1.addView(it) }
        col1.addView(Ui.divider(ctx))
        col1.addView(Ui.item(ctx, getString(R.string.ad_pc), getString(R.string.ad_pc_sub), icon = R.drawable.ic_computer) { navigator().push(PcFragment()) })
        c.addView(card1)

        // v3.12: 1C'ga yozish — hamma imkoniyat bir joyda
        c.addView(Ui.section(ctx, getString(R.string.ad_sec_write)))
        val (cardW, colW) = Ui.card(ctx)
        colW.addView(Ui.item(ctx, getString(R.string.ad_writes), getString(R.string.ad_writes_sub), icon = R.drawable.ic_sync_alt, tone = 1) { navigator().push(WritesFragment()) })
        tvWrites = Ui.hint(ctx, "", 0).also { colW.addView(it) }
        val writes = listOf(
            Triple(R.string.bb_title, R.string.bb_sub, R.drawable.ic_barcode) to { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodeBatchFragment()) },
            Triple(R.string.of_goods_edit, R.string.of_goods_edit_sub, R.drawable.ic_price) to { navigator().push(OneCFormFragment.of(W.GOODS_EDIT)) },
            Triple(R.string.of_new_goods, R.string.of_new_goods_sub, R.drawable.ic_add_box) to { navigator().push(OneCFormFragment.of(W.NEW_GOODS)) },
            Triple(R.string.ad_new_receipt, R.string.ad_new_receipt_sub, R.drawable.ic_inventory) to { navigator().push(WriteFormFragment.of(W.RECEIPT, "", "")) },
            Triple(R.string.of_client_pay, R.string.of_client_pay_sub, R.drawable.ic_wallet) to { navigator().push(OneCFormFragment.of(W.CLIENT_PAY)) },
            Triple(R.string.ad_client_return, R.string.ad_client_return_sub, R.drawable.ic_assignment_return) to { navigator().push(WriteFormFragment.of(W.CLIENT_RETURN, "", "")) },
            Triple(R.string.wf_pay, R.string.of_supp_pay_sub, R.drawable.ic_payments) to { navigator().push(WriteFormFragment.of(W.SUPPLIER_PAY, "", "")) },
            Triple(R.string.wf_sreturn, R.string.of_supp_return_sub, R.drawable.ic_undo) to { navigator().push(WriteFormFragment.of(W.SUPPLIER_RETURN, "", "")) },
            Triple(R.string.of_cash_in, R.string.of_cash_in_sub, R.drawable.ic_cash_in) to { navigator().push(OneCFormFragment.of(W.CASH_IN)) },
            Triple(R.string.of_cash_out, R.string.of_cash_out_sub, R.drawable.ic_cash_out) to { navigator().push(OneCFormFragment.of(W.CASH_OUT)) },
        )
        for ((t, go) in writes) {
            colW.addView(Ui.divider(ctx))
            colW.addView(Ui.item(ctx, getString(t.first), getString(t.second), icon = t.third) { go() })
        }
        c.addView(cardW)

        c.addView(Ui.section(ctx, getString(R.string.ad_sec_clients)))
        val (card2, col2) = Ui.card(ctx)
        col2.addView(Ui.item(ctx, getString(R.string.ad_requests), getString(R.string.ad_requests_sub), icon = R.drawable.ic_notifications) { navigator().push(ClientRequestsFragment()) })
        tvReq = Ui.hint(ctx, "", 0).also { col2.addView(it) }
        col2.addView(Ui.divider(ctx))
        col2.addView(Ui.item(ctx, getString(R.string.ad_debtors), getString(R.string.ad_debtors_sub), icon = R.drawable.ic_person_search) { navigator().push(DebtorsFragment()) })
        col2.addView(Ui.divider(ctx))
        col2.addView(Ui.item(ctx, getString(R.string.ad_share), getString(R.string.ad_share_sub), icon = R.drawable.ic_storefront) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.share.ShareFragment()) })
        col2.addView(Ui.divider(ctx))
        col2.addView(Ui.item(ctx, getString(R.string.ad_receipt_style), getString(R.string.ad_receipt_style_sub), icon = R.drawable.ic_print) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.share.ReceiptStyleFragment())
        })
        c.addView(card2)

        c.addView(Ui.section(ctx, getString(R.string.ad_sec_agents)))
        val (card3, col3) = Ui.card(ctx)
        col3.addView(Ui.item(ctx, getString(R.string.ad_relay), getString(if (ServiceLocator.shareStore.relayOn) R.string.rl_on_short else R.string.rl_off_short), icon = R.drawable.ic_hub) {
            navigator().push(com.ilxomjon.dur_novvot_dokon.ui.share.RelayFragment())
        })
        c.addView(card3)

        c.addView(Ui.section(ctx, getString(R.string.ad_sec_goods)))
        val (card4, col4) = Ui.card(ctx)
        col4.addView(Ui.item(ctx, getString(R.string.ad_labels), getString(R.string.ad_labels_sub), icon = R.drawable.ic_label) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.labels.LabelsFragment()) })
        col4.addView(Ui.divider(ctx))
        col4.addView(Ui.item(ctx, getString(R.string.sc_title), getString(R.string.sc_sub), icon = R.drawable.ic_scale) { navigator().push(ScaleFragment()) })
        col4.addView(Ui.divider(ctx))
        col4.addView(Ui.item(ctx, getString(R.string.ad_barcodes), getString(R.string.ad_barcodes_sub), icon = R.drawable.ic_qr) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.scan.BarcodesFragment()) })
        c.addView(card4)

        c.addView(Ui.section(ctx, getString(R.string.ad_sec_app)))
        val (card5, col5) = Ui.card(ctx)
        col5.addView(Ui.item(ctx, getString(R.string.au_title), getString(R.string.au_sub), icon = R.drawable.ic_download) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.settings.AutoUpdateFragment()) })
        col5.addView(Ui.divider(ctx))
        col5.addView(Ui.item(ctx, getString(R.string.ad_settings), getString(R.string.ad_settings_sub), icon = R.drawable.ic_settings) { navigator().push(com.ilxomjon.dur_novvot_dokon.ui.settings.SettingsFragment()) })
        c.addView(card5)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        launchOnStarted {
            combine(ServiceLocator.onec.state, ServiceLocator.clientRequests.state) { a, b -> a to b }.collect { (o, r) ->
                tvSales?.text = o.sales?.let { getString(R.string.ad_sales_st, it.docs.size, formatDateTime(it.at.takeIf { t -> t > 0 } ?: 0L)) } ?: getString(R.string.ad_none_yet)
                tvSupp?.text = o.suppliers?.let { getString(R.string.ad_supp_st, it.list.size) } ?: getString(R.string.ad_none_yet)
                val pending = o.writes.count { it.result == null }
                val failed = o.writes.count { it.result?.ok == false }
                tvWrites?.text = getString(R.string.ad_writes_st, pending, failed)
                tvReq?.text = if (r.newCount > 0) getString(R.string.ad_requests_new, r.newCount) else getString(R.string.ad_requests_none)
            }
        }
        launchUi { ServiceLocator.clientRequests.refresh() }
        launchUi { ServiceLocator.onec.refreshResults() }
    }
}
