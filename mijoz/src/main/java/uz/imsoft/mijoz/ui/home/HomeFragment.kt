package uz.imsoft.mijoz.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.data.DataState
import uz.imsoft.mijoz.data.RefreshResult
import uz.imsoft.mijoz.databinding.FragmentHomeBinding
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.describe
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.mijoz.ui.money
import uz.imsoft.mijoz.ui.money2
import uz.imsoft.mijoz.ui.settings.LinkDialog
import uz.imsoft.mijoz.ui.show
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.Stamp
import java.util.Calendar

class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnLink.setOnClickListener { LinkDialog.show(this) }
        b.btnRefresh.setOnClickListener { refresh() }
        b.btnPrices.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.nav_prices) }
        b.btnReport.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.nav_report) }
        launchOnStarted { Locator.data.state.collect { render(it) } }
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val msg = try {
                when (Locator.data.refresh(askFresh = true)) {
                    RefreshResult.UPDATED -> getString(R.string.home_updated_at, Fmt.dateTime(Locator.data.state.value.publishedAt))
                    RefreshResult.SAME -> getString(R.string.home_same_at, Fmt.dateTime(Locator.data.state.value.publishedAt))
                    RefreshResult.NO_DATA -> getString(R.string.home_no_data)
                    RefreshResult.NOT_LINKED -> getString(R.string.home_link_title)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                requireContext().describe(e)
            }
            _b?.let { Snackbar.make(it.root, msg, Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun render(s: DataState) {
        val bb = _b ?: return
        val ctx = requireContext()
        bb.progress.show(s.loading)
        bb.btnRefresh.isEnabled = !s.loading && s.linked
        bb.cardLink.show(s.loaded && !s.linked)
        val snap = s.snapshot
        bb.toolbar.title = snap?.clientName?.ifEmpty { null } ?: Locator.prefs.link?.clientName?.ifEmpty { null } ?: getString(R.string.app_name)
        // Qarz: faqat server (1-ilova) ma'lumoti; vaqti bilan.
        // Qarz: server qiymati (1-ilova olgan, vaqti bilan); bo'lmasa — 1C hisobotidagi rasmiy yakuniy qoldiq.
        val debt = snap?.debt
        val rb = snap?.reportBalance
        fun amount(som: Double, usd: Double): String {
            val parts = ArrayList<String>()
            if (som != 0.0 || usd == 0.0) parts += ctx.money(som, false)
            if (usd != 0.0) parts += ctx.money(usd, true)
            return parts.joinToString("\n")
        }
        when {
            debt != null -> {
                bb.tvDebt.text = amount(debt.som, debt.usd)
                val lines = arrayListOf(getString(R.string.home_debt_at, if (debt.at > 0) Fmt.dateTime(debt.at) else "—"))
                if (rb != null) lines += getString(R.string.home_report_balance, Stamp.formatDate(rb.first), ctx.money2(rb.second))
                bb.tvDebtAt.text = lines.joinToString("\n")
            }
            rb != null -> {
                bb.tvDebt.text = amount(rb.second.som, rb.second.usd)
                bb.tvDebtAt.text = getString(R.string.home_debt_report_at, Stamp.formatDate(rb.first))
            }
            else -> {
                bb.tvDebt.text = "—"
                bb.tvDebtAt.text = getString(if (snap == null) R.string.home_no_data_short else R.string.home_debt_unknown)
            }
        }
        bb.tvSync.text = when {
            !s.linked -> getString(R.string.home_not_linked)
            snap == null -> getString(R.string.home_no_data)
            else -> getString(R.string.home_sync, Fmt.dateTime(s.publishedAt.takeIf { it > 0 } ?: snap.createdAt), if (s.fetchedAt > 0) Fmt.dateTime(s.fetchedAt) else "—")
        }
        // "Narx/qarzni yangilash so'raldi" — 1-ilova server ko'ringanda bajaradi (Excel ma'lumoti baribir darhol keladi).
        bb.tvRequest.show(s.requestPending && s.snapshot != null)
        if (s.requestPending) bb.tvRequest.text = getString(R.string.home_request_pending, Fmt.time(s.requestAt))
        bb.tvError.show(s.error != null)
        bb.tvError.text = ctx.describe(s.error)
        if (snap == null) {
            bb.tvMonth.text = "—"; bb.tvLast.text = "—"; bb.tvCoverage.text = ""
            return
        }
        val c = Calendar.getInstance()
        val today = Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        val (mf, mt) = ClientStats.Period.thisMonth(today)
        val t = ClientStats.totals(snap.ledger, mf, mt)
        bb.tvMonth.text = listOf(
            getString(R.string.sum_bought, ctx.money2(t.purchased), t.purchaseCount),
            getString(R.string.sum_paid, ctx.money2(t.paid), t.paymentCount),
            getString(R.string.sum_returned, ctx.money2(t.returned)),
        ).joinToString("\n")
        val lastBuy = snap.ledger.lastOrNull { !ClientStats.purchaseOf(it).isZero }
        val lastPay = snap.ledger.lastOrNull { !ClientStats.paymentOf(it).isZero }
        bb.tvLast.text = listOf(
            getString(R.string.last_buy, lastBuy?.let { Stamp.formatDateTime(it.stamp) + " · " + it.product + " · " + ctx.money2(ClientStats.purchaseOf(it)) } ?: "—"),
            getString(R.string.last_pay, lastPay?.let { Stamp.formatDateTime(it.stamp) + " · " + ctx.money2(ClientStats.paymentOf(it)) } ?: "—"),
        ).joinToString("\n")
        bb.tvCoverage.text = if (snap.coverage.isEmpty()) getString(R.string.home_no_reports)
        else getString(
            R.string.home_coverage2, snap.coverage.joinToString(", ") { Stamp.formatDate(it.from) + "–" + Stamp.formatDate(it.to) },
            if (snap.lastImportAt > 0) Fmt.dateTime(snap.lastImportAt) else "—",
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
