package uz.imsoft.mijoz.ui.extra

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.printer.UsbPrinter
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.ledgerhost.MijozLedgerHost
import uz.imsoft.mijoz.ui.money
import uz.imsoft.shared.android.ReceiptBitmap
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.receipt.Receipt

/** v3.2: kod bilan yig'iladigan Mijoz ekranlari uchun asos. */
abstract class MFragment : Fragment() {
    protected val ctx: Context get() = requireContext()

    abstract fun build(inflater: LayoutInflater): View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = build(inflater)

    protected fun back() { parentFragmentManager.popBackStack() }

    protected fun push(f: Fragment) { (activity as? MainActivity)?.push(f) }

    protected fun page(title: Int): Ui.Page = Ui.page(requireContext(), getString(title), R.drawable.ic_arrow_back) { back() }

    protected fun listPage(title: Int, onRow: (Int) -> Unit): Ui.ListPage =
        Ui.listPage(requireContext(), getString(title), R.drawable.ic_arrow_back, { back() }, onRow)

    protected fun snack(text: CharSequence) {
        val v = view ?: return
        Snackbar.make(v, text, Snackbar.LENGTH_LONG).show()
    }

    protected fun launchUi(block: suspend CoroutineScope.() -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) snack(getString(R.string.x_error, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    protected fun som(v: Double): String = ctx.money(v, false)

    protected fun fmtQty(q: Double): String = if (q == Math.floor(q)) q.toLong().toString() else "%.3f".format(java.util.Locale.US, q).trimEnd('0').trimEnd('.')

    /** Chekni chiqarish: USB printer ulangan bo'lsa — printerga, aks holda rasm qilib ulashish. */
    protected suspend fun printReceipt(r0: Receipt) {
        val footer = MijozLedgerHost.receiptFooter.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val r = if (r0.footer.isEmpty() && footer.isNotEmpty()) r0.copy(footer = footer) else r0
        val style = MijozLedgerHost.receiptStyle
        val bmp = withContext(Dispatchers.Default) { ReceiptBitmap.render(r, style, MijozLedgerHost.receiptLogo) }
        if (Locator.prefs.printerSection) {
            val bytes = withContext(Dispatchers.Default) { ReceiptBitmap.toEscPos(bmp, style.dark) }
            withContext(Dispatchers.IO) { UsbPrinter.send(ctx.applicationContext, bytes) }
            snack(getString(R.string.lgm_printed))
        } else {
            uz.imsoft.mijoz.ui.ledger.ReceiptActions.shareImage(ctx, r, bmp, MijozLedgerHost)
        }
    }

    /** Kassa cheki matnlari. */
    protected fun posLabels() = uz.imsoft.shared.pos.Pos.Labels(
        getString(R.string.pos_receipt_title), getString(R.string.pos_client), getString(R.string.pos_sum), getString(R.string.pos_discount),
        getString(R.string.pos_total), getString(R.string.pos_paid), getString(R.string.pos_debt), getString(R.string.pos_number), getString(R.string.pos_time),
    )
}
