package com.ilxomjon.dur_novvot_dokon.ui.report

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentReportBinding
import com.ilxomjon.dur_novvot_dokon.ui.common.StorePickerSheet
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.formatDate
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.show
import com.ilxomjon.dur_novvot_dokon.ui.showInvisible
import com.ilxomjon.dur_novvot_dokon.ui.viewModel
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class ReportFragment : Fragment() {
    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: ReportViewModel
    private var shownHtml: String? = null

    private val csvOrders = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val d = vm.state.value.local
        if (uri != null && d != null) lifecycleScope.launch { csvDone(runCatching { LocalReportUi.writeOrdersCsv(requireContext(), uri, d) }.isSuccess) }
    }
    private val csvPayments = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val d = vm.state.value.local
        if (uri != null && d != null) lifecycleScope.launch { csvDone(runCatching { LocalReportUi.writePaymentsCsv(requireContext(), uri, d) }.isSuccess) }
    }

    private fun csvDone(ok: Boolean) {
        _binding?.let { com.google.android.material.snackbar.Snackbar.make(it.root, if (ok) R.string.csv_saved else R.string.csv_failed, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { ReportViewModel(ServiceLocator.catalog, ServiceLocator.reports, ServiceLocator.localReports) }

        // Server HTML'i: JavaScript, fayl va tarmoq kirishi o'chirilgan.
        binding.webView.settings.javaScriptEnabled = false
        binding.webView.settings.allowFileAccess = false
        binding.webView.settings.allowContentAccess = false
        binding.webView.settings.builtInZoomControls = true
        binding.webView.settings.displayZoomControls = false

        childFragmentManager.setFragmentResultListener(REQ_STORE, viewLifecycleOwner) { _, bundle ->
            vm.selectStore(bundle.getString(StorePickerSheet.KEY_ID).orEmpty())
        }
        binding.rowStore.setOnClickListener { StorePickerSheet.show(childFragmentManager, REQ_STORE) }
        binding.rowStore.setOnLongClickListener { vm.clearStore(); true }
        binding.btnPeriod.setOnClickListener { pickPeriod() }
        binding.btnShow.setOnClickListener {
            if (vm.state.value.loading) vm.cancel() else vm.load(LocalReportUi.labels(requireContext()), getString(R.string.report_title))
        }
        binding.toggleSource.addOnButtonCheckedListener { _, id, checked ->
            if (checked) vm.setSource(if (id == R.id.btnSourceLocal) ReportSource.LOCAL else ReportSource.SERVER)
        }
        binding.webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_print -> printReport()
                R.id.action_csv_orders -> csvOrders.launch("buyurtmalar_${System.currentTimeMillis() / 1000}.csv")
                R.id.action_csv_payments -> csvPayments.launch("tolovlar_${System.currentTimeMillis() / 1000}.csv")
            }
            true
        }

        launchOnStarted { vm.state.collect { render(it) } }
    }

    /** Hisobotni PDF qilib saqlash yoki chop etish (tizim oynasi ochiladi). */
    private fun printReport() {
        // Qog'ozga doim yorug' (oq fon, qora matn) — qorong'i rejimda ham
        val html = vm.state.value.html ?: return
        com.ilxomjon.dur_novvot_dokon.ui.ledger.ReceiptActions.printHtml(requireActivity(), ReportHtml.wrap(html, dark = false), "Hisobot")
    }

    private fun pickPeriod() {
        val s = vm.state.value
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.report_pick_period)
            .setSelection(androidx.core.util.Pair(localToUtc(s.fromMillis), localToUtc(s.toMillis)))
            .build()
        picker.addOnPositiveButtonClickListener { sel ->
            val a = sel.first
            val b = sel.second
            if (a != null && b != null) vm.setPeriod(utcToLocal(a), utcToLocal(b))
        }
        picker.show(childFragmentManager, "period")
    }

    private fun render(s: ReportState) {
        val local = s.source == ReportSource.LOCAL
        binding.tvStoreName.text = s.store?.name ?: getString(if (local) R.string.report_all_stores else R.string.sale_select_store)
        binding.btnPeriod.text = formatDate(Date(s.fromMillis)) + " – " + formatDate(Date(s.toMillis))
        binding.progress.showInvisible(s.loading)
        binding.btnShow.setText(if (s.loading && !local) R.string.report_cancel else R.string.report_show)

        val message = when {
            s.noStore -> getString(R.string.report_error_store)
            s.error != null -> getString(R.string.report_failed) + "\n" + requireContext().describeError(s.error)
            s.loading -> getString(R.string.report_loading)
            s.html != null && s.html.isBlank() -> getString(R.string.report_empty)
            else -> null
        }
        binding.tvMessage.text = message
        binding.tvMessage.show(message != null)
        val showWeb = !s.loading && s.error == null && !s.html.isNullOrBlank()
        binding.webView.show(showWeb)
        binding.tvSource.show(showWeb)
        binding.tvSource.text = if (local) getString(R.string.report_local_badge) else getString(R.string.report_server_badge, formatDateTime(s.loadedAt))
        binding.toolbar.menu.findItem(R.id.action_print)?.isVisible = showWeb
        binding.toolbar.menu.findItem(R.id.action_csv_orders)?.isVisible = showWeb && local && s.local != null
        binding.toolbar.menu.findItem(R.id.action_csv_payments)?.isVisible = showWeb && local && s.local != null
        if (showWeb && s.html != shownHtml) {
            shownHtml = s.html
            val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            binding.webView.loadDataWithBaseURL(null, ReportHtml.wrap(s.html.orEmpty(), dark), "text/html", "UTF-8", null)
        }
    }

    override fun onDestroyView() {
        (binding.webView.parent as? ViewGroup)?.removeView(binding.webView)
        binding.webView.destroy()
        super.onDestroyView()
        _binding = null
        shownHtml = null
    }

    private fun localToUtc(local: Long): Long {
        val l = Calendar.getInstance().apply { timeInMillis = local }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun utcToLocal(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply {
            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private companion object {
        const val REQ_STORE = "report_store"
    }
}
