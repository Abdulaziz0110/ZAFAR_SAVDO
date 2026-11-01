package com.ilxomjon.dur_novvot_dokon.ui.ledger

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.ilxomjon.dur_novvot_dokon.R
import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.receipt.Receipt
import uz.imsoft.shared.receipt.ReceiptBuilder
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerDoc
import uz.imsoft.shared.report.LedgerEntry
import uz.imsoft.shared.report.Money2
import uz.imsoft.shared.report.Stamp
import java.util.Calendar

/** 1C tarixi ko'rinishi uchun umumiy yordamchilar (ikki ilovada bir xil). */
object LedgerUi {
    fun som(ctx: Context): String = ctx.getString(R.string.lg_unit_som)
    fun money(ctx: Context, m: Money2): String = NumFmt.money2(m, som(ctx))

    fun kindTitle(ctx: Context, k: EntryKind): String = ctx.getString(
        when (k) {
            EntryKind.SALE -> R.string.lg_kind_sale
            EntryKind.RETURN -> R.string.lg_kind_return
            EntryKind.PAYMENT -> R.string.lg_kind_payment
            EntryKind.PAYMENT_RETURN -> R.string.lg_kind_payment_return
            EntryKind.DISCOUNT -> R.string.lg_kind_discount
            EntryKind.OTHER -> R.string.lg_kind_other
        },
    )

    fun icon(k: EntryKind): Int = when (k) {
        EntryKind.SALE -> R.drawable.ic_cart
        EntryKind.RETURN -> R.drawable.ic_return
        EntryKind.PAYMENT, EntryKind.PAYMENT_RETURN -> R.drawable.ic_payment
        else -> R.drawable.ic_info
    }

    /** Summa rangi: savdo — oddiy, to'lov — yashil, qaytarish — sariq-jigarrang. */
    fun amountColor(ctx: Context, k: EntryKind): Int = when (k) {
        EntryKind.PAYMENT -> ContextCompat.getColor(ctx, R.color.brand_success)
        EntryKind.RETURN, EntryKind.DISCOUNT -> MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorTertiary, 0)
        else -> MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0)
    }

    /** Ro'yxatdagi sarlavha: "Savdo · 4 xil tovar", "To'lov · Click". */
    fun docTitle(ctx: Context, d: LedgerDoc): String = when {
        // 1-ilovada qilingan, 1C hisobotida hali yo'q savdo — tasdiqlangan deb ko'rsatilmaydi
        uz.imsoft.shared.report.AppSale.isApp(d.kindText) -> kindTitle(ctx, d.kind) + " · " + ctx.getString(R.string.lg_from_app)
        d.isGoods && d.goods.isNotEmpty() -> kindTitle(ctx, d.kind) + " · " + ctx.getString(R.string.lg_items_n, d.goods.size)
        d.comment.contains("click", ignoreCase = true) -> kindTitle(ctx, d.kind) + " · Click"
        else -> kindTitle(ctx, d.kind)
    }

    fun time(st: Long) = "%02d:%02d".format(Stamp.hour(st), Stamp.minute(st))

    fun weekday(ctx: Context, st: Long): String {
        val c = Calendar.getInstance().apply { clear(); set(Stamp.year(st), Stamp.month(st) - 1, Stamp.dayOfMonth(st)) }
        val names = intArrayOf(R.string.lg_wd_1, R.string.lg_wd_2, R.string.lg_wd_3, R.string.lg_wd_4, R.string.lg_wd_5, R.string.lg_wd_6, R.string.lg_wd_7)
        val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // dushanba = 0
        return ctx.getString(names[dow])
    }

    fun todayStamp(): Long {
        val c = Calendar.getInstance()
        return Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun dayLabel(ctx: Context, day: Long): String {
        val today = todayStamp()
        val base = Stamp.formatDate(day) + ", " + weekday(ctx, day)
        return when (Stamp.dayStart(day)) {
            today -> ctx.getString(R.string.lg_today) + " · " + base
            Stamp.addDays(today, -1) -> ctx.getString(R.string.lg_yesterday) + " · " + base
            else -> base
        }
    }

    /** "40 × 182 000" */
    fun lineDetail(e: LedgerEntry): String {
        val p = NumFmt.price(e.v(Col.PRICE))
        return when {
            e.qty != 0.0 && p.isNotEmpty() -> NumFmt.qty(e.qty) + " × " + p
            e.qty != 0.0 -> NumFmt.qty(e.qty)
            else -> ""
        }
    }

    fun highlight(ctx: Context, text: String, ranges: List<IntRange>?): CharSequence {
        if (ranges.isNullOrEmpty()) return text
        val color = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, 0)
        val s = SpannableString(text)
        for (r in ranges) {
            val a = r.first.coerceIn(0, text.length)
            val b = (r.last + 1).coerceIn(a, text.length)
            if (b <= a) continue
            s.setSpan(StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(color), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    fun receipt(ctx: Context, d: LedgerDoc, header: String, client: String, footer: String = "", staff: String = ""): Receipt = ReceiptBuilder.fromDoc(
        d, header, client, labels(ctx), footerLines(footer), staff,
    )

    fun footerLines(footer: String): List<String> = footer.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(6)

    /** Chek yozuvlari (ikki ilovada bir xil). */
    fun labels(ctx: Context): ReceiptBuilder.Labels = ReceiptBuilder.Labels(
            som(ctx), { k -> ctx.getString(R.string.lg_receipt_title, kindTitle(ctx, k)) }, ctx.getString(R.string.lg_client),
            ctx.getString(R.string.lg_total), ctx.getString(R.string.lg_discount), ctx.getString(R.string.lg_payable),
            ctx.getString(R.string.lg_rc_debt), ctx.getString(R.string.lg_items_qty), ctx.getString(R.string.lg_receipt_note),
            sum = ctx.getString(R.string.lg_rc_sum), staff = ctx.getString(R.string.lg_rc_staff), number = ctx.getString(R.string.lg_rc_number),
            time = ctx.getString(R.string.lg_rc_time), returnBanner = ctx.getString(R.string.lg_rc_return),
        )
}
