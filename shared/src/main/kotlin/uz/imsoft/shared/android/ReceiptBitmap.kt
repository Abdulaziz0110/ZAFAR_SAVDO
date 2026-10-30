package uz.imsoft.shared.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import uz.imsoft.shared.receipt.EscPosRaster
import uz.imsoft.shared.receipt.Receipt
import java.io.File
import java.io.FileOutputStream

/**
 * Chekni rasmga chizish (oq fon, qora matn) — ulashish (PNG) va chek printeri (rastr) uchun.
 * Ko'rinish do'konning qog'oz chekiga o'xshatilgan: katta do'kon nomi, Xodim/Chek №/Vaqt, Mijoz, ramkali qatorlar,
 * katta "Summa", qarz qoldig'i va telefonlar. Rasm bo'lgani uchun kirill/lotin printer kod sahifasiga bog'liq emas.
 */
object ReceiptBitmap {
    /** [width] — nuqtalarda: 80 mm = 576, 58 mm = 384. */
    fun render(r: Receipt, width: Int = 576): Bitmap {
        val k = width / 576f
        val pad = 10 * k
        val inner = (width - 2 * pad).toInt()
        fun paint(size: Float, bold: Boolean = false, serif: Boolean = false, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size * k
            typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            if (bold && serif) isFakeBoldText = true
            letterSpacing = spacing
        }
        val pHeader = paint(46f, bold = true, serif = true)
        val pInfo = paint(24f)
        val pClient = paint(30f, bold = true)
        val pBanner = paint(32f, bold = true, spacing = 0.25f)
        val pName = paint(27f)
        val pFormula = paint(27f)
        val pTotal = paint(27f)
        val pSum = paint(38f, bold = true)
        val pFooter = paint(30f, bold = true, spacing = 0.06f)
        val pNote = paint(19f)
        val line = Paint().apply { color = Color.BLACK; strokeWidth = 2.5f * k; style = Paint.Style.STROKE }

        // Avval balandlikni hisoblash uchun: har bir "op" o'z balandligi va chizish funksiyasi bilan.
        class Op(val h: Float, val draw: (Canvas, Float) -> Unit)
        val ops = ArrayList<Op>()
        fun layout(text: String, p: TextPaint, w: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, p, w.coerceAtLeast(10)).setAlignment(align).setIncludePad(false).build()
        fun gap(h: Float) { ops += Op(h * k) { _, _ -> } }
        fun block(text: String, p: TextPaint, align: Layout.Alignment, after: Float = 4f, x0: Float = pad, w: Int = inner) {
            if (text.isEmpty()) return
            val sl = layout(text, p, w, align)
            ops += Op(sl.height + after * k) { c, y -> c.save(); c.translate(x0, y); sl.draw(c); c.restore() }
        }
        /** Chapda yorliq, o'ngda qiymat (bir qatorda; chap uzun bo'lsa o'raladi). */
        fun row(left: String, right: String, p: TextPaint, pr: TextPaint = p, after: Float = 6f, indent: Float = 0f) {
            val rw = pr.measureText(right)
            val lw = (inner - indent * k - rw - 14 * k).toInt().coerceAtLeast((inner * 0.35f).toInt())
            val sl = layout(left, p, lw)
            val h = maxOf(sl.height.toFloat(), pr.fontSpacing)
            ops += Op(h + after * k) { c, y ->
                c.save(); c.translate(pad + indent * k, y + (h - sl.height) / 2); sl.draw(c); c.restore()
                c.drawText(right, width - pad - rw, y + (h - pr.fontSpacing) / 2 - pr.ascent(), pr)
            }
        }
        fun rule(after: Float = 6f) { ops += Op((4 + after) * k) { c, y -> c.drawLine(pad, y + 2 * k, width - pad, y + 2 * k, line) } }

        gap(6f)
        block(r.header, pHeader, Layout.Alignment.ALIGN_CENTER, 14f)
        if (r.info.isEmpty()) {
            block(r.title, pClient, Layout.Alignment.ALIGN_CENTER, 2f)
            block(r.dateText, pInfo, Layout.Alignment.ALIGN_CENTER, 8f)
        } else {
            // Yorliqlar o'ng tomonga tekislangan ustun (qog'oz chekdagidek): "   Xodim:  Dokon"
            val labelW = r.info.maxOf { pInfo.measureText(it.first + ":") } + 0f
            val col = maxOf(pad + 120 * k, width * 0.36f - labelW / 2)
            for ((idx, kv) in r.info.withIndex()) {
                val lab = kv.first + ":"
                val lw = pInfo.measureText(lab)
                val date = if (idx == r.info.lastIndex) r.date else ""
                ops += Op(pInfo.fontSpacing + 4 * k) { c, y ->
                    val base = y - pInfo.ascent()
                    c.drawText(lab, col + labelW - lw, base, pInfo)
                    c.drawText(kv.second, col + labelW + 12 * k, base, pInfo)
                    if (date.isNotEmpty()) c.drawText(date, width - pad - pInfo.measureText(date), base, pInfo)
                }
            }
            gap(6f)
        }
        if (r.clientLabel.isNotEmpty()) {
            val lab = r.clientLabel + ":"
            val lw = pClient.measureText(lab)
            val sl = layout(r.clientName, pClient, (inner - lw - 40 * k).toInt())
            ops += Op(sl.height + 8 * k) { c, y ->
                c.drawText(lab, pad, y - pClient.ascent(), pClient)
                c.save(); c.translate(pad + lw + 40 * k, y); sl.draw(c); c.restore()
            }
        } else block(r.client, pInfo, Layout.Alignment.ALIGN_CENTER, 8f)
        for (t in r.before) row(t.label + ":", t.value, pInfo, pTotal, 6f)
        if (r.banner.isNotEmpty()) {
            gap(4f); rule(8f)
            block(r.banner, pBanner, Layout.Alignment.ALIGN_CENTER, 8f)
            rule(4f)
        }
        gap(8f)
        // Ramkali qatorlar: "1 ) Nomi" va o'ngda "40 x 117 000 = 4 680 000"
        val cellPad = 8 * k
        for ((i, x) in r.lines.withIndex()) {
            val nl = layout("${i + 1} ) ${x.name}", pName, (inner - 2 * cellPad).toInt())
            val fl = layout(x.formula, pFormula, (inner - 2 * cellPad).toInt(), Layout.Alignment.ALIGN_OPPOSITE)
            val h = cellPad + nl.height + 2 * k + fl.height + cellPad * 0.6f
            ops += Op(h) { c, y ->
                c.drawRect(pad, y, width - pad, y + h, line)
                c.save(); c.translate(pad + cellPad, y + cellPad * 0.6f); nl.draw(c); c.restore()
                c.save(); c.translate(pad + cellPad, y + cellPad * 0.6f + nl.height + 2 * k); fl.draw(c); c.restore()
            }
        }
        gap(16f)
        for (t in r.totals) {
            if (t.bold) row(t.label + ":", t.value, pSum, pSum, 10f, indent = 50f)
            else row(t.label + ":", t.value, pTotal, pTotal, 6f, indent = 50f)
        }
        for (t in r.after) row(t.label + ":", t.value, pTotal, pTotal, 6f, indent = 50f)
        if (r.footer.isNotEmpty()) gap(8f)
        for (f in r.footer) block(f, pFooter, Layout.Alignment.ALIGN_NORMAL, 6f, pad + 40 * k, (inner - 40 * k).toInt())
        for (n in r.notes) { gap(10f); block(n, pNote, Layout.Alignment.ALIGN_CENTER) }
        gap(18f)

        val height = ops.sumOf { it.h.toDouble() }.toInt().coerceAtLeast(10)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        var y = 0f
        for (op in ops) { op.draw(c, y); y += op.h }
        return bmp
    }

    fun toEscPos(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val black = BooleanArray(w * h) { i ->
            val c = px[i]
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            lum < 140
        }
        return EscPosRaster.encode(w, h, black)
    }

    fun savePng(bmp: Bitmap, file: File): File {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
