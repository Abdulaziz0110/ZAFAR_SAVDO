package com.ilxomjon.dur_novvot_dokon.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.repo.LocalReportRepository
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentDayendBinding
import com.ilxomjon.dur_novvot_dokon.ui.formatDate
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Kun yakuni: shu telefondagi bugungi buyurtma va to'lovlar (Lokal). Kassa bilan solishtirish va CSV uchun. */
class DayEndFragment : Fragment() {
    private var _binding: FragmentDayendBinding? = null
    private val binding get() = _binding!!
    private var dayStart = startOfToday()
    private var data: LocalReportRepository.Data? = null

    private val csvOrders = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val d = data
        if (uri != null && d != null) lifecycleScope.launch { done(runCatching { LocalReportUi.writeOrdersCsv(requireContext(), uri, d) }.isSuccess) }
    }
    private val csvPayments = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val d = data
        if (uri != null && d != null) lifecycleScope.launch { done(runCatching { LocalReportUi.writePaymentsCsv(requireContext(), uri, d) }.isSuccess) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDayendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { navigator().pop() }
        binding.webView.settings.javaScriptEnabled = false
        binding.webView.settings.allowFileAccess = false
        binding.webView.settings.blockNetworkLoads = true
        binding.webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.btnPrev.setOnClickListener { shift(-1) }
        binding.btnNext.setOnClickListener { shift(1) }
        binding.btnCsvOrders.setOnClickListener { csvOrders.launch("buyurtmalar_${stamp()}.csv") }
        binding.btnCsvPayments.setOnClickListener { csvPayments.launch("tolovlar_${stamp()}.csv") }
        load()
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(dayStart))

    private fun shift(days: Int) {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_MONTH, days) }
        if (c.timeInMillis > startOfToday()) return
        dayStart = c.timeInMillis
        load()
    }

    private fun load() {
        val end = Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        binding.tvDate.text = formatDate(Date(dayStart))
        binding.btnNext.isEnabled = dayStart < startOfToday()
        viewLifecycleOwner.lifecycleScope.launch {
            val d = ServiceLocator.localReports.load(dayStart, end, null)
            data = d
            if (_binding == null) return@launch
            val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val body = com.ilxomjon.dur_novvot_dokon.domain.LocalReport.html(d.summary, getString(R.string.dayend_title) + " · " + formatDate(Date(dayStart)), LocalReportUi.labels(requireContext()))
            binding.webView.loadDataWithBaseURL(null, ReportHtml.wrap(body, dark), "text/html", "UTF-8", null)
        }
    }

    private fun done(ok: Boolean) {
        _binding?.let { Snackbar.make(it.root, if (ok) R.string.csv_saved else R.string.csv_failed, Snackbar.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        (binding.webView.parent as? ViewGroup)?.removeView(binding.webView)
        binding.webView.destroy()
        super.onDestroyView()
        _binding = null
    }
}
