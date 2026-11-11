package com.ilxomjon.dur_novvot_dokon.ui.ledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.util.Pair as UPair
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentLedgerBinding
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.show
import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.report.Stamp
import java.util.Calendar
import java.util.TimeZone

/**
 * 1C tarixi: savdo, qaytarish va to'lovlar ro'yxati (bo'lib o'tgandek), aqlli qidiruv, filtr, jami.
 * Ma'lumot O'ZGARTIRILMAYDI — faqat ko'rish, takrorlash, chek va ulashish.
 */
class LedgerFragment : Fragment() {
    private var _b: FragmentLedgerBinding? = null
    private val b get() = _b!!
    private lateinit var vm: LedgerViewModel
    private val adapter = LedgerAdapter { doc -> DocSheet.show(childFragmentManager, doc.key) }
    private var last = LedgerUiState()

    private val kinds = listOf(KindFilter.ALL to R.string.lg_f_all, KindFilter.SALES to R.string.lg_f_sales, KindFilter.RETURNS to R.string.lg_f_returns, KindFilter.PAYMENTS to R.string.lg_f_payments)
    private val periods = listOf(
        PeriodFilter.ALL to R.string.lg_p_all, PeriodFilter.TODAY to R.string.lg_p_today, PeriodFilter.DAYS7 to R.string.lg_p_7,
        PeriodFilter.MONTH to R.string.lg_p_month, PeriodFilter.LAST_MONTH to R.string.lg_p_last_month, PeriodFilter.DAYS90 to R.string.lg_p_90,
        PeriodFilter.CUSTOM to R.string.lg_p_custom,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentLedgerBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(this)[LedgerViewModel::class.java]
        val host = ledgerHost()
        vm.fuzzy.value = host?.fuzzySearch ?: true
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.recycler.itemAnimator = null
        b.edtSearch.setText(vm.query.value)
        b.edtSearch.onTextChanged { vm.query.value = it }
        b.edtSearch.setOnEditorActionListener { v, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                requireContext().getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
        b.tilSearch.setEndIconOnClickListener { b.edtSearch.setText("") }
        b.btnHelp.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lg_help_title).setMessage(R.string.lg_help_text)
                .setPositiveButton(R.string.lg_close, null).show()
        }
        for ((k, label) in kinds) {
            val chip = layoutInflater.inflate(R.layout.item_group_chip, b.chipKinds, false) as Chip
            chip.setText(label)
            chip.tag = k
            chip.setOnClickListener { vm.kind.value = k }
            b.chipKinds.addView(chip)
        }
        b.chipPeriod.setOnClickListener { pickPeriod() }
        b.btnClearFilters.setOnClickListener {
            b.edtSearch.setText("")
            vm.kind.value = KindFilter.ALL
            vm.period.value = PeriodFilter.ALL
            vm.client.value = ""
        }
        if (host != null) launchOnStarted { host.ledgerData.collect { vm.setData(it) } }
        // v5.2 (A2): yangilanish holati pastdagi uzun matn emas, tepadagi ikonka. Bosilsa — hamma ma'lumot.
        val status = host?.syncStatus
        b.btnSync.show(status != null)
        if (host != null && status != null) {
            b.btnSync.setOnClickListener { host.onSyncStatus(this) }
            launchOnStarted {
                status.collect { t ->
                    val bb = _b ?: return@collect
                    bb.btnSync.show(true)
                    bb.btnSync.setIconResource(if (t.isBlank()) R.drawable.ic_sync else R.drawable.ic_sync_alt)
                    val cd = t.ifBlank { getString(R.string.lg_sync_title) }
                    bb.btnSync.contentDescription = cd
                    bb.btnSync.tooltipText = cd
                }
            }
        }
        // v5.2 (A6): mijoz bo'yicha qidiruv — ro'yxat ma'lumotning o'zidan quriladi
        b.chipClient.setOnClickListener { pickClient() }
        launchOnStarted { vm.state.collect { last = it; render(it) } }
    }

    private fun render(s: LedgerUiState) {
        val bb = _b ?: return
        val ctx = requireContext()
        for (i in 0 until bb.chipKinds.childCount) {
            val c = bb.chipKinds.getChildAt(i) as Chip
            c.isChecked = c.tag == s.kind
        }
        bb.chipPeriod.text = if (s.period == PeriodFilter.CUSTOM) Stamp.formatDate(s.from) + " – " + Stamp.formatDate(s.to)
        else getString(periods.first { it.first == s.period }.second)
        bb.chipPeriod.isChecked = s.period != PeriodFilter.ALL
        bb.chipClient.show(s.clients.size > 1)
        bb.chipClient.text = s.client.ifBlank { getString(R.string.lg_client_all) }
        bb.chipClient.isChecked = s.client.isNotBlank()
        val searching = s.query.isNotBlank()
        adapter.submit(s.rows, searching, s.clientName)
        val r = s.result
        bb.cardSummary.show(s.hasData && r != null && r.hits.isNotEmpty())
        if (r != null) {
            val sum = r.summary
            val lines = ArrayList<String>()
            if (searching || s.kind != KindFilter.ALL || s.period != PeriodFilter.ALL) lines += getString(R.string.lg_found_n, sum.docs)
            if (sum.sales > 0) lines += getString(R.string.lg_sum_sales, sum.sales, LedgerUi.money(ctx, sum.salesTotal))
            if (sum.returns > 0) lines += getString(R.string.lg_sum_returns, sum.returns, LedgerUi.money(ctx, sum.returnsTotal))
            if (sum.payments > 0) lines += getString(R.string.lg_sum_payments, sum.payments, LedgerUi.money(ctx, sum.paymentsTotal))
            if (searching && r.hasLineConds && sum.goodsLines > 0) {
                lines += getString(R.string.lg_sum_goods, sum.goodsLines, NumFmt.qty(sum.goodsQty), LedgerUi.money(ctx, sum.goodsSum))
            }
            bb.tvSummary.text = lines.joinToString("\n")
            bb.tvApproxNote.show(r.approximate && r.hits.isNotEmpty())
        }
        val empty = s.loaded && s.rows.isEmpty()
        bb.emptyState.show(empty)
        bb.tvEmpty.text = if (!s.hasData) ledgerHost()?.emptyText(ctx) ?: getString(R.string.lg_no_data) else getString(R.string.lg_nothing_found)
        bb.btnClearFilters.show(s.hasData && empty)
        bb.tilSearch.helperText = if (!searching && s.hasData) getString(R.string.lg_search_helper) else null
    }

    /** v5.2 (A6): mijozni qidiruvli ro'yxatdan tanlash — o'sha mijozning butun tarixi ko'rinadi. */
    private fun pickClient() {
        val ctx = requireContext()
        val names = last.clients
        if (names.isEmpty()) return
        val rows = ArrayList<uz.imsoft.shared.android.Ui.Row>()
        rows += uz.imsoft.shared.android.Ui.Row(getString(R.string.lg_client_all_rows), icon = R.drawable.ic_groups)
        for (n in names) rows += uz.imsoft.shared.android.Ui.Row(n, icon = R.drawable.ic_person)
        uz.imsoft.shared.android.Ui.pickSearch(
            ctx, getString(R.string.lg_client_pick), rows, getString(R.string.lg_client_search), getString(R.string.lg_cancel),
        ) { i -> vm.client.value = if (i == 0) "" else names[i - 1] }
    }

    private fun pickPeriod() {
        val labels = periods.map { getString(it.second) }.toTypedArray()
        val cur = periods.indexOfFirst { it.first == vm.period.value }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lg_period)
            .setSingleChoiceItems(labels, cur) { d, i ->
                d.dismiss()
                val p = periods[i].first
                if (p == PeriodFilter.CUSTOM) pickRange() else vm.period.value = p
            }
            .setNegativeButton(R.string.lg_cancel, null).show()
    }

    private fun pickRange() {
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText(R.string.lg_p_custom).build()
        picker.addOnPositiveButtonClickListener { sel: UPair<Long, Long> ->
            val a = sel.first ?: return@addOnPositiveButtonClickListener
            val z = sel.second ?: a
            vm.custom.value = Stamp.dayStart(utc(a)) to Stamp.dayEnd(utc(z))
            vm.period.value = PeriodFilter.CUSTOM
        }
        picker.show(childFragmentManager, "range")
    }

    private fun utc(ms: Long): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
        return Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Hozirgi filtrdagi hujjatlar (masalan PDF uchun). */
    fun currentState(): LedgerUiState = last

    fun viewModel(): LedgerViewModel = vm

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
