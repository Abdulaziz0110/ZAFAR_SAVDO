package uz.imsoft.shared.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import uz.imsoft.shared.barcode.BarcodeEncode
import uz.imsoft.shared.label.LabelData
import uz.imsoft.shared.label.LabelKind
import java.io.ByteArrayOutputStream

/**
 * Narx yorlig'ini rasm qilib chizish (oq fon, qora) — printer o'lchamida, nuqtama-nuqta.
 * Ko'rinish 1C shakllaridan olingan:
 *  - PLU:   tovar nomi (qalin) / "KOD:  501" va katta narx "60 000";
 *  - Shtrix: tovar nomi / katta narx / shtrix kod (EAN-13, EAN-8 yoki Code 128) va raqamlari.
 * Shtrix kod modullari butun nuqtalarda chiziladi (skaner aniq o'qishi uchun), matn qora-oqqa keltiriladi.
 */
object LabelBitmap {
    /** [w]×[h] — nuqtalarda (58×40 mm, 203 dpi = 464×320). */
    fun render(d: LabelData, kind: LabelKind, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val m = maxOf(6f, minOf(w, h) * 0.045f)
        when (kind) {
            LabelKind.PLU -> drawPlu(c, d, w.toFloat(), h.toFloat(), m)
            LabelKind.BARCODE -> drawBarcode(c, d, w.toFloat(), h.toFloat(), m)
        }
        return threshold(bmp)
    }

    private fun paint(size: Float, bold: Boolean = true) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun layout(text: String, p: TextPaint, w: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, w.coerceAtLeast(10)).setAlignment(align)
            .setIncludePad(false).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()

    /** Matn [maxW]×[maxH] ga 2 qatordan oshmay sig'adigan eng katta o'lcham. */
    private fun fitName(text: String, start: Float, maxW: Int, maxH: Float, align: Layout.Alignment): StaticLayout {
        var size = start
        while (true) {
            val sl = layout(text, paint(size), maxW, align)
            val fits = sl.height <= maxH && (0 until sl.lineCount).none { sl.getEllipsisCount(it) > 0 }
            if (fits || size < 10f) return sl
            size *= 0.92f
        }
    }

    /** Bir qatorli matn [maxW] ga sig'guncha kichraytiriladi. */
    private fun fitLine(text: String, start: Float, maxW: Float): TextPaint {
        val p = paint(start)
        while (p.measureText(text) > maxW && p.textSize > 8f) p.textSize *= 0.94f
        return p
    }

    private fun drawPlu(c: Canvas, d: LabelData, w: Float, h: Float, m: Float) {
        val inner = (w - 2 * m).toInt()
        val name = fitName(d.name, h * 0.17f, inner, h * 0.42f, Layout.Alignment.ALIGN_NORMAL)
        c.save(); c.translate(m, m); name.draw(c); c.restore()
        // Pastki qator: chapda KOD, o'ngda narx (eng katta)
        val kod = "KOD:  " + d.plu
        val pk = fitLine(kod, h * 0.17f, w * 0.42f)
        val pp = fitLine(d.price, h * 0.32f, w - 2 * m - pk.measureText(kod) - m)
        val base = h - m - 2f
        c.drawText(kod, m, base, pk)
        c.drawText(d.price, w - m - pp.measureText(d.price), base, pp)
    }

    private fun drawBarcode(c: Canvas, d: LabelData, w: Float, h: Float, m: Float) {
        val inner = (w - 2 * m).toInt()
        val sym = BarcodeEncode.forShopCode(d.barcode)
        // Yuqori qism: nom va narx; pastki ~45% — shtrix kod
        val name = fitName(d.name, h * 0.13f, inner, h * 0.27f, Layout.Alignment.ALIGN_CENTER)
        c.save(); c.translate(m, m * 0.7f); name.draw(c); c.restore()
        val top = m * 0.7f + name.height
        val pp = fitLine(d.price, h * 0.21f, inner.toFloat())
        val priceBase = top + pp.textSize * 0.95f
        c.drawText(d.price, (w - pp.measureText(d.price)) / 2, priceBase, pp)
        if (sym == null) return
        val digitsSize = h * 0.085f
        val pd = paint(digitsSize, bold = false)
        val barsTop = priceBase + h * 0.04f
        val barsBottom = h - m * 0.6f - digitsSize * 1.05f
        val total = sym.modules.size + 2 * sym.quiet
        val mw = maxOf(1, ((w - m) / total).toInt())
        val bw = sym.modules.size * mw
        val x0 = ((w - bw) / 2f).toInt().toFloat()
        val bar = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = false }
        var i = 0
        while (i < sym.modules.size) {
            if (sym.modules[i]) {
                var j = i
                while (j < sym.modules.size && sym.modules[j]) j++
                c.drawRect(x0 + i * mw, barsTop, x0 + j * mw, maxOf(barsTop + 8f, barsBottom), bar)
                i = j
            } else i++
        }
        val human = when (sym.kind) {
            BarcodeEncode.Kind.EAN13 -> sym.text[0] + "  " + sym.text.substring(1, 7) + "  " + sym.text.substring(7)
            BarcodeEncode.Kind.EAN8 -> sym.text.substring(0, 4) + "  " + sym.text.substring(4)
            BarcodeEncode.Kind.CODE128 -> sym.text
        }
        while (pd.measureText(human) > inner && pd.textSize > 8f) pd.textSize *= 0.94f
        c.drawText(human, (w - pd.measureText(human)) / 2, h - m * 0.6f, pd)
    }

    /** Qora-oq (1 bit) ko'rinishga: termoprinterda kulrang chiqmaydi. */
    private fun threshold(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            px[i] = if (lum < 150) Color.BLACK else Color.WHITE
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        return src
    }

    fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
