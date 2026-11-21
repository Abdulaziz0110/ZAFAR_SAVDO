package com.ilxomjon.dur_novvot_dokon.ui.labels

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.labels.LabelPrinterClient
import com.ilxomjon.dur_novvot_dokon.ui.describeError

/** Chop etish xatosini tushunarli matnga aylantirish (ulashilgan printer va kompyuter dasturi uchun). */
internal fun Context.labelErrorText(e: Exception): String {
    val s = ServiceLocator.labels.state.value
    val reason = (e as? LabelPrinterClient.PrintError)?.reason
    return when {
        reason == "no_printer" -> getString(if (s.lpd) R.string.lb_err_no_host_lpd else R.string.lb_err_no_route)
        reason == "lpd_queue" -> getString(R.string.lb_err_lpd_queue, s.queue, s.host)
        reason != null && reason.startsWith("lpd:") -> getString(R.string.lb_err_lpd, reason.removePrefix("lpd:"))
        reason != null && reason.startsWith("pc:") -> getString(R.string.lb_err_pc, reason.removePrefix("pc:"))
        // pc 39: agent — Admin telefoni / kompyuter orqali (LPD emas)
        reason != null && reason.startsWith("relay:") ->
            getString(R.string.lb_err_relay, describeError(IllegalStateException(reason.removePrefix("relay:"))).let { d ->
                if (d == getString(R.string.err_unknown)) reason.removePrefix("relay:") else d
            })
        reason == "bad_key" -> getString(R.string.lb_err_key)
        reason != null && reason.startsWith("printer") -> getString(R.string.lb_err_printer, reason.substringAfter(':', "").trim().ifEmpty { reason })
        s.lpd -> getString(R.string.lb_err_lpd_net, s.host, detailOf(e))
        else -> getString(R.string.lb_err_net, reason ?: detailOf(e))
    }
}

/** pc 39: «Kutilmagan xatolik» o'rniga aniq sabab (masalan "SocketTimeoutException: connect timed out"). */
private fun Context.detailOf(e: Exception): String {
    val d = describeError(e)
    if (d != getString(R.string.err_unknown)) return d
    return e.javaClass.simpleName + (e.message?.takeIf { it.isNotBlank() }?.let { ": " + it.take(160) } ?: "")
}
