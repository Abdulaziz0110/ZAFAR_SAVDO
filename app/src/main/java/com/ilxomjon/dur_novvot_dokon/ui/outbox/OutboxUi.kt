package com.ilxomjon.dur_novvot_dokon.ui.outbox

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.domain.outbox.CheckReason
import com.ilxomjon.dur_novvot_dokon.domain.outbox.OutboxState

/**
 * Holat matnlari. QOIDA: server tasdiqlamagan holat hech qachon "tasdiqlandi" deyilmaydi;
 * "Serverga yozildi" — faqat savollar jadvaliga yozilgan (ID bor), "qayta ishlandi" EMAS.
 */
object OutboxUi {
    fun label(ctx: Context, s: OutboxState): String = ctx.getString(
        when (s) {
            OutboxState.READY -> R.string.st_ready
            OutboxState.SENDING -> R.string.st_sending
            OutboxState.RETRY_WAIT -> R.string.st_retry
            OutboxState.UNCERTAIN -> R.string.st_uncertain
            OutboxState.SENT -> R.string.st_sent
            OutboxState.NEEDS_CHECK -> R.string.st_needs_check
            OutboxState.SENT_MANUAL -> R.string.st_sent_manual
        },
    )

    fun reason(ctx: Context, code: String?): String? = when (code) {
        CheckReason.PROBE_UNRELIABLE -> ctx.getString(R.string.reason_probe_unreliable)
        CheckReason.RESTORED -> ctx.getString(R.string.reason_restored)
        CheckReason.REJECTED -> ctx.getString(R.string.reason_rejected)
        CheckReason.PROBE_FAILED -> ctx.getString(R.string.reason_probe_failed)
        else -> null
    }

    fun needsAttention(s: OutboxState) = s == OutboxState.NEEDS_CHECK
    fun canAct(s: OutboxState) = s == OutboxState.NEEDS_CHECK || s == OutboxState.UNCERTAIN || s == OutboxState.RETRY_WAIT
}
