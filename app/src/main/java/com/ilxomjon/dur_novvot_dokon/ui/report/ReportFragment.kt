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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = viewModel { ReportViewModel(ServiceLocator.catalog, ServiceLocator.reports) }

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
        binding.btnPeriod.setOnClickListener { pickPeriod() }
        binding.btnShow.setOnClickListener { vm.load() }
        binding.webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_print) printReport()
            true
        }

        launchOnStarted { vm.state.collect { render(it) } }
    }

    /** Hisobotni PDF qilib saqlash yoki chop etish (tizim oynasi ochiladi). */
    private fun printReport() {
        val pm = requireContext().getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
        pm.print(getString(R.string.report_title), binding.webView.createPrintDocumentAdapter(getString(R.string.report_title)), null)
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
        binding.tvStoreName.text = s.store?.name ?: getString(R.string.sale_select_store)
        binding.btnPeriod.text = formatDate(Date(s.fromMillis)) + " – " + formatDate(Date(s.toMillis))
        binding.progress.showInvisible(s.loading)
        binding.btnShow.isEnabled = !s.loading

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
        binding.toolbar.menu.findItem(R.id.action_print)?.isVisible = showWeb
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
