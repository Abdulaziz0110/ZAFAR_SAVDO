package com.ilxomjon.dur_novvot_dokon.ui.history

import androidx.fragment.app.Fragment
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.SentOrder
import com.ilxomjon.dur_novvot_dokon.domain.SentPayment
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxEntry
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxKind
import com.ilxomjon.dur_novvot_dokon.ui.ledger.ReceiptActions
import com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost
import uz.imsoft.shared.receipt.Chek
import uz.imsoft.shared.receipt.Receipt
import uz.imsoft.shared.report.AppSale
import uz.imsoft.shared.report.Stamp
import java.util.Calendar

/**
 * v5.2 (A3): tarixdagi savdo/to'lovning cheki — 1C "Chek1" maketining AYNAN nusxasi, boshqa format yo'q.
 * 1C tarixidagi hujjat ham, telefonda qilingan savdo ham bir xil ko'rinishda chiqadi.
 */
object OrderReceipt {
    private fun stampOf(ms: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return Stamp.of(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
    }

    private fun wrap(chek: Chek, stamp: Long): Receipt =
        Receipt("", "", "", Stamp.formatDateTime(stamp), emptyList(), emptyList(), emptyList(), chek = chek)

    /** Chekdagi "Xodim" va telefonlar — do'kon sozlamasidan (1C maketidagi maydonlar). */
    private fun agent() = ServiceLocator.shareStore.receiptStaff
    private fun phone() = Chek.phoneOf(ServiceLocator.shareStore.receiptFooter.split('\n').map { it.trim() }.filter { it.isNotEmpty() })

    fun of(o: SentOrder): Receipt {
        val sale = AppSale(
            id = "o${o.id}", at = o.sentAt, stamp = stampOf(o.sentAt), isReturn = o.mode == DealMode.RETURN,
            usd = o.currency == Currency.USD, lines = o.lines.map { AppSale.Line(it.name, it.qty, it.price) },
            discount = o.discount, code = if (o.outboxId > 0) OutboxEntry.receiptOf(OutboxKind.ORDER, o.outboxId) else "",
            paid = o.paid, cash = o.cash, card = o.card,
        )
        return wrap(Chek.ofSale(sale, sale.code, o.storeName, agent(), phone()), sale.stamp)
    }

    fun of(p: SentPayment): Receipt {
        val code = if (p.outboxId > 0) OutboxEntry.receiptOf(OutboxKind.PAYMENT, p.outboxId) else ""
        val sale = AppSale(
            id = "p${p.id}", at = p.sentAt, stamp = stampOf(p.sentAt), isReturn = false, usd = false,
            lines = emptyList(), discount = 0.0, code = code, paid = p.total, cash = p.cash, card = p.transfer + p.click,
            payment = true, note = p.note,
        )
        return wrap(Chek.ofSale(sale, code, p.storeName, agent(), phone()), sale.stamp)
    }

    fun show(f: Fragment, r: Receipt, stamp: Long) = ReceiptActions.preview(f, r, DokonLedgerHost, Stamp.formatDate(stamp))
}
