package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.domain.Store
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui

/**
 * v3.2 (tavsiya): qarzdorlar ro'yxati — server bergan oxirgi qarzlar, kattasi birinchi. Bosilsa: to'lov qabul qilish
 * yoki savdo. Qarz qiymati oxirgi sinxronlash vaqtidagi (sana ko'rsatiladi).
 */
class DebtorsFragment : CodeFragment() {
    private lateinit var lp: Ui.ListPage
    private lateinit var tvInfo: TextView
    private var shown: List<Store> = emptyList()
    private var query = ""

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.ad_debtors) { i -> shown.getOrNull(i)?.let { pick(it) } }
        lp.header.addView(Ui.search(ctx, getString(R.string.dbt_search)) { query = it.trim().lowercase(); render() }.first)
        tvInfo = Ui.hint(ctx, "", 4).also { lp.header.addView(it) }
        lp.header.addView(Ui.button(ctx, getString(R.string.dbt_sync), outlined = true, top = 4) {
            launchUi { ServiceLocator.catalog.sync(); snack(getString(R.string.sync_ok)) }
        })
        lp.empty.text = getString(R.string.dbt_empty)
        return lp.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchOnStarted { ServiceLocator.catalog.state.collect { render() } }
    }

    private fun render() {
        val cat = ServiceLocator.catalog.state.value
        val rate = cat.rate.takeIf { it > 0 } ?: 1.0
        val all = cat.stores.filter { it.debtSom > 0.0 || it.debtUsd > 0.0 }.sortedByDescending { it.debtSom + it.debtUsd * rate }
        shown = all.filter { query.isEmpty() || it.name.lowercase().contains(query) || it.regionName.lowercase().contains(query) }
        lp.empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        lp.adapter.submit(shown.mapIndexed { i, s -> Ui.Row("${i + 1}. ${s.name}", s.regionName, money2(s.debtSom, s.debtUsd)) })
        tvInfo.text = getString(R.string.dbt_info, all.size, money2(all.sumOf { it.debtSom }, all.sumOf { it.debtUsd }),
            if (cat.lastSyncAt > 0) formatDateTime(cat.lastSyncAt) else "—")
    }

    private fun pick(s: Store) {
        MaterialAlertDialogBuilder(ctx).setTitle(s.name).setMessage(money2(s.debtSom, s.debtUsd))
            .setPositiveButton(R.string.dbt_payment) { _, _ ->
                com.ilxomjon.dur_novvot_dokon.ui.UiBus.paymentStore.value = s.id
                navigator().openTab(R.id.nav_payment)
            }
            .setNegativeButton(R.string.close, null).show()
    }
}
