package uz.imsoft.mijoz.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.FragmentReportBinding
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.mijoz.ui.ledger.LedgerFragment
import uz.imsoft.mijoz.ui.ledger.LedgerHost
import uz.imsoft.mijoz.ui.ledger.ReceiptActions
import uz.imsoft.mijoz.ui.ledgerhost.MijozLedgerHost
import uz.imsoft.shared.report.ClientSnapshot

/** "Tarix" — 1C tarixi (1-ilovadagi bilan bir xil ro'yxat, qidiruv, chek), qayta buyurtma va buyurtmalarim. */
class ReportFragment : Fragment(), LedgerHost by MijozLedgerHost {
    private var _b: FragmentReportBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentReportBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (childFragmentManager.findFragmentByTag(TAG) == null) {
            childFragmentManager.beginTransaction().replace(R.id.ledgerContainer, LedgerFragment(), TAG).commitNow()
        }
        b.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_pdf -> { printList(); true }
                R.id.action_orders -> { MyOrdersSheet.show(childFragmentManager); true }
                else -> false
            }
        }
        launchOnStarted {
            Locator.data.state.collect { s ->
                val bb = _b ?: return@collect
                bb.toolbar.menu.findItem(R.id.action_orders)?.isVisible = s.snapshot?.features?.contains(ClientSnapshot.FEATURE_ORDERS) == true || Locator.prefs.myOrders.isNotEmpty()
            }
        }
    }

    /** Joriy ro'yxat (qidiruv va filtr bilan) PDF / chop etish. */
    private fun printList() {
        val lf = childFragmentManager.findFragmentByTag(TAG) as? LedgerFragment ?: return
        val st = lf.currentState()
        val snap = Locator.data.state.value.snapshot ?: return
        val ctx = requireContext()
        val extra = ArrayList<String>()
        snap.reportBalance?.let { extra += ctx.getString(R.string.home_report_balance, uz.imsoft.shared.report.Stamp.formatDate(it.first), uz.imsoft.mijoz.ui.ledger.LedgerUi.money(ctx, it.second)) }
        snap.debt?.let { d -> extra += ctx.getString(R.string.pdf_debt, uz.imsoft.mijoz.ui.ledger.LedgerUi.money(ctx, uz.imsoft.shared.report.Money2(d.som, d.usd)), if (d.at > 0) uz.imsoft.mijoz.ui.Fmt.dateTime(d.at) else "—") }
        val html = uz.imsoft.mijoz.ui.ledger.LedgerPdf.html(ctx, snap.clientName, st, extra)
        ReceiptActions.printHtml(requireContext(), html, "Tarix_" + snap.clientName.filter { it.isLetterOrDigit() }.take(20))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private companion object { const val TAG = "ledger" }
}
