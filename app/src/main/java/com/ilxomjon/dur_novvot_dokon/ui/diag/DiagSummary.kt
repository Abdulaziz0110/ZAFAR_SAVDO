package com.ilxomjon.dur_novvot_dokon.ui.diag

import android.content.Context
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.data.repo.Diagnosis

/**
 * v5.2 (A8): diagnostika xulosasi — bosh sahifada ham, diagnostika oynasida ham BIR XIL ro'yxat.
 * Har qator: nima tekshirildi, holati va xato bo'lsa — AYNAN nima bo'lgani.
 */
object DiagSummary {
    /** [state]: 1 — yaxshi, 0 — xato, -1 — tekshirilmadi/kerak emas. */
    class Item(val label: String, val state: Int, val detail: String = "")

    fun items(ctx: Context, d: Diagnosis): List<Item> {
        val out = ArrayList<Item>(8)
        out += Item(
            ctx.getString(R.string.diag_internet) + if (d.net.wifi) " (Wi-Fi)" else "",
            if (d.net.online) 1 else 0,
            if (d.net.online) "" else ctx.getString(R.string.dg_no_internet),
        )
        if (d.remoteConfigured) {
            out += Item(
                ctx.getString(R.string.diag_vpn),
                if (d.net.vpn) 1 else 0,
                if (d.net.vpn) "" else ctx.getString(R.string.dg_no_vpn),
            )
        }
        out += Item(
            ctx.getString(R.string.diag_local_port),
            when (d.localPort) { true -> 1; false -> 0; null -> -1 },
            if (d.localPort == false) ctx.getString(R.string.dg_no_local) else "",
        )
        if (d.remoteConfigured) {
            out += Item(
                ctx.getString(R.string.diag_remote_port),
                when (d.remotePort) { true -> 1; false -> 0; null -> -1 },
                if (d.remotePort == false) ctx.getString(R.string.dg_no_remote) else "",
            )
        }
        out += Item(ctx.getString(R.string.diag_sql), if (d.sqlOk) 1 else 0, d.sqlError.orEmpty())
        val stuck = d.counts.needsCheck + d.counts.retry + d.counts.uncertain
        if (d.counts.unsent > 0 || stuck > 0) {
            out += Item(
                ctx.getString(R.string.dg_queue),
                if (stuck > 0) 0 else -1,
                ctx.getString(R.string.diag_queue, d.counts.unsent, d.counts.needsCheck, d.counts.retry + d.counts.uncertain),
            )
        }
        d.lastOutboxError?.let { out += Item(ctx.getString(R.string.dg_last_error), 0, it) }
        if (d.dbOk == false) out += Item(ctx.getString(R.string.dg_db), 0, ctx.getString(R.string.diag_db_bad))
        d.writeError?.let { out += Item(ctx.getString(R.string.dg_write), 0, it) }
        if (d.batteryRestricted) out += Item(ctx.getString(R.string.dg_battery), 0, ctx.getString(R.string.diag_battery))
        return out
    }

    fun allOk(items: List<Item>): Boolean = items.none { it.state == 0 }

    /** Bosh sahifadagi qisqa xulosa: hammasi joyida yoki birinchi xato. */
    fun headline(ctx: Context, items: List<Item>): String {
        val bad = items.firstOrNull { it.state == 0 } ?: return ctx.getString(R.string.dg_all_ok)
        return bad.label + (if (bad.detail.isNotBlank()) ": " + bad.detail else "")
    }
}
