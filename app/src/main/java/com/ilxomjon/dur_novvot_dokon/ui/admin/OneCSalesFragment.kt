package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.datepicker.MaterialDatePicker
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import kotlinx.coroutines.delay
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.OneCDoc
import uz.imsoft.shared.onec.OneCSales
import uz.imsoft.shared.report.Stamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * v3.2 Admin: 1C savdolar ro'yxati (kompyuter yuboradi, standart — oxirgi 30 kun). Qidiruv: mijoz, tovar, hujjat raqami.
 * Boshqa davr — "Davr" tugmasi: kompyuterga so'rov ketadi, 1 daqiqa ichida keladi.
 */
class OneCSalesFragment : CodeFragment() {
    private lateinit var lp: Ui.ListPage
    private lateinit var tvInfo: TextView
    private lateinit var tvTotal: TextView
    private var query = ""
    private var kindIdx = 0
    private var shown: List<OneCDoc> = emptyList()

    private val kinds = listOf(emptySet(), setOf(OneCDoc.SALE, OneCDoc.SALE_MOBILE), setOf(OneCDoc.RETAIL), setOf(OneCDoc.CLIENT_RETURN))

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.oc_sales_title) { i -> shown.getOrNull(i)?.let { showDoc(it) } }
        val h = lp.header
        h.addView(Ui.search(ctx, getString(R.string.oc_search)) { query = it.trim(); render() }.first)
        h.addView(Ui.toggle(ctx, listOf(getString(R.string.oc_f_all), getString(R.string.oc_f_sales), getString(R.string.oc_f_retail), getString(R.string.oc_f_returns)), 0, 6) { kindIdx = it; render() })
        val b30 = Ui.button(ctx, getString(R.string.oc_last30), outlined = true, top = 6, icon = R.drawable.ic_calendar) { request(30) }
        val bRange = Ui.button(ctx, getString(R.string.oc_range), outlined = true, top = 6, icon = R.drawable.ic_calendar) { pickRange() }
        h.addView(Ui.buttonRow(ctx, b30, bRange, top = 0))
        tvInfo = Ui.hint(ctx, "", 4).also { h.addView(it) }
        tvTotal = Ui.text(ctx, "", com.google.android.material.R.style.TextAppearance_Material3_TitleSmall, 2).also { h.addView(it) }
        lp.empty.text = getString(R.string.oc_empty)
        return lp.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        launchOnStarted { ServiceLocator.onec.state.collect { render() } }
    }

    private fun render() {
        if (!::lp.isInitialized) return
        val st = ServiceLocator.onec.state.value
        val s: OneCSales? = st.sales
        if (s == null) {
            shown = emptyList()
            lp.adapter.submit(emptyList())
            lp.empty.visibility = View.VISIBLE
            tvInfo.text = if (st.requestedKind == "sales") getString(R.string.oc_requested) else getString(R.string.oc_none)
            tvTotal.text = ""
            return
        }
        shown = s.filter(query, kinds[kindIdx])
        lp.empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        lp.adapter.submit(shown.map { d ->
            Ui.Row(
                d.party.ifEmpty { kindName(d.kind) },
                getString(R.string.oc_row_sub, kindName(d.kind), d.number, Stamp.formatDateTime(d.stamp), d.lines.size),
                money2(if (d.kind == OneCDoc.CLIENT_RETURN) -d.som else d.som, if (d.kind == OneCDoc.CLIENT_RETURN) -d.usd else d.usd),
                icon = docIcon(d.kind), tone = if (d.kind == OneCDoc.CLIENT_RETURN) 2 else 0,
            )
        })
        tvInfo.text = buildString {
            append(getString(R.string.oc_period, Stamp.formatDate(s.from), Stamp.formatDate(s.to), formatDateTime(s.at)))
            if (st.requestedKind == "sales" && st.requestedAt > s.at) append('\n').append(getString(R.string.oc_requested))
            if (s.errors.isNotEmpty()) append('\n').append(getString(R.string.oc_errors, s.errors.joinToString("; ")))
        }
        val (ts, tu) = s.totals(shown)
        tvTotal.text = getString(R.string.oc_total, shown.size, money2(ts, tu))
    }

    private fun ymd(ms: Long, utc: Boolean = false): String = SimpleDateFormat("yyyyMMdd", Locale.US).apply { if (utc) timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    private fun request(days: Int) {
        val to = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -(days - 1)) }
        send(ymd(cal.timeInMillis), ymd(to))
    }

    private fun pickRange() {
        val p = MaterialDatePicker.Builder.dateRangePicker().setTitleText(R.string.oc_range).build()
        p.addOnPositiveButtonClickListener { sel ->
            val a = sel.first ?: return@addOnPositiveButtonClickListener
            val b = sel.second ?: a
            send(ymd(a, utc = true), ymd(b, utc = true))
        }
        p.show(childFragmentManager, "range")
    }

    private fun send(from: String, to: String) {
        if (!ServiceLocator.onec.ready) { snack(getString(R.string.oc_no_cloud)); return }
        launchUi {
            ServiceLocator.onec.requestData("sales", from, to)
            snack(getString(R.string.oc_request_sent))
            render()
            // Kompyuter javobini tezroq olish: 3 daqiqa davomida har 15 soniyada kirish qutisini tekshiradi
            repeat(12) {
                delay(15_000)
                runCatching { ServiceLocator.share.processInbox() }
                if ((ServiceLocator.onec.state.value.sales?.at ?: 0L) > ServiceLocator.onec.state.value.requestedAt) return@launchUi
            }
        }
    }
}
