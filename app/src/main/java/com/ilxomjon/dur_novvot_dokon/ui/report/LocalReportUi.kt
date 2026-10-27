package com.ilxomjon.dur_novvot_dokon.ui.report

import android.content.Context
import android.net.Uri
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.repo.LocalReportRepository
import com.ilxomjon.dur_novvot_dokon.domain.LocalReport
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState
import com.ilxomjon.dur_novvot_dokon.ui.outbox.OutboxUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lokal hisobot uchun matnlar va CSV faylga yozish. */
object LocalReportUi {
    fun labels(ctx: Context) = LocalReport.Labels(
        localBadge = ctx.getString(R.string.report_local_badge),
        sales = ctx.getString(R.string.dayend_sales), returns = ctx.getString(R.string.dayend_returns),
        paidInOrders = ctx.getString(R.string.dayend_paid_in_orders), payments = ctx.getString(R.string.dayend_payments),
        cash = ctx.getString(R.string.payment_cash), transfer = ctx.getString(R.string.payment_transfer), click = ctx.getString(R.string.payment_click),
        unsent = ctx.getString(R.string.dayend_unsent), byStore = ctx.getString(R.string.dayend_by_store), store = ctx.getString(R.string.dayend_store),
        som = ctx.getString(R.string.unit_som),
    )

    private fun status(ctx: Context, states: Map<Long, OutboxState>, outboxId: Long, pending: Boolean): String {
        val s = states[outboxId] ?: return ctx.getString(if (pending) R.string.st_ready else R.string.st_sent)
        return OutboxUi.label(ctx, s)
    }

    suspend fun writeOrdersCsv(ctx: Context, uri: Uri, data: LocalReportRepository.Data) {
        val repo = ServiceLocator.localReports
        val text = LocalReport.ordersCsv(data.orders, { repo.receipt(it) }, { status(ctx, data.states, it.outboxId, it.pending) })
        write(ctx, uri, text)
    }

    suspend fun writePaymentsCsv(ctx: Context, uri: Uri, data: LocalReportRepository.Data) {
        val repo = ServiceLocator.localReports
        val text = LocalReport.paymentsCsv(data.payments, { repo.receipt(it) }, { status(ctx, data.states, it.outboxId, it.pending) })
        write(ctx, uri, text)
    }

    private suspend fun write(ctx: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Fayl ochilmadi")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
