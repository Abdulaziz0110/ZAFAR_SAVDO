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
 * Joylashuv do'kon 1C konfiguratsiyasidagi shablonlardan olingan (ulushlarda — har qanday o'lchamga moslashadi):
 *  - PLU ("ГодехЦенник"): nom (Arial qalin 16) tepada, narx (26) o'rtada, "KOD: 501" (20) pastda — hammasi markazda;
 *  - Shtrix ("ГодехШтрих"): nom (16), narx (20), pastda shtrix kod va raqamlari.
 * Shrift: Arial bilan bir xil o'lchamli Liberation Sans Bold ([font] — ilova o'rnatadi), bo'lmasa tizimning qalin shrifti.
 */
object LabelBitmap {
    /** Ilova assets'dan yuklaydi (Liberation Sans Bold — Arial o'lchamlari). */
    @Volatile var font: Typeface? = null

    private fun bold(): Typeface = font ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    /** [w]×[h] — nuqtalarda (58×40 mm, 203 dpi = 464×320). */
    fun render(d: LabelData, kind: LabelKind, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        when (kind) {
            LabelKind.PLU -> drawPlu(c, d, w.toFloat(), h.toFloat())
            LabelKind.BARCODE -> drawBarcode(c, d, w.toFloat(), h.toFloat())
        }
        return threshold(bmp)
    }

    private fun paint(size: Float, tf: Typeface = bold()) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = tf
    }

    /**
     * Matnni quti ichida markazda chizadi: [size] dan boshlab, [maxLines] qatorga va qutiga sig'guncha kichraytiradi
     * (1C'dagi "Переносить" + "По центру" kabi).
     */
    private fun boxText(c: Canvas, text: String, size: Float, left: Float, top: Float, right: Float, bottom: Float, maxLines: Int) {
        val bw = (right - left).toInt().coerceAtLeast(10)
        val bh = bottom - top
        var s = size
        var sl: StaticLayout
        while (true) {
            val p = paint(s)
            sl = StaticLayout.Builder.obtain(text, 0, text.length, p, bw).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false).setLineSpacing(0f, 0.95f).setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            val ellipsized = (0 until sl.lineCount).any { sl.getEllipsisCount(it) > 0 }
            val wide = (0 until sl.lineCount).any { sl.getLineWidth(it) > bw + 0.5f }
            if ((sl.height <= bh && !ellipsized && !wide) || s < 9f) break
            s *= 0.93f
        }
        c.save()
        c.translate(left, top + (bh - sl.height) / 2f)
        sl.draw(c)
        c.restore()
    }

    /**
     * "ГодехЦенник" (nom / narx / KOD, hammasi markazda). O'lchamlar do'kondagi 1C yorlig'idan o'lchangan:
     * nom markazi 26%, narx 60%, KOD 87% (balandlikdan); harf balandligi 9% / 14% / 9.7%.
     */
    private fun drawPlu(c: Canvas, d: LabelData, w: Float, h: Float) {
        boxText(c, d.name, h * 0.126f, w * 0.03f, h * 0.10f, w * 0.97f, h * 0.42f, 2)
        boxText(c, d.price, h * 0.195f, w * 0.03f, h * 0.47f, w * 0.97f, h * 0.73f, 1)
        boxText(c, "KOD:  " + d.plu, h * 0.136f, w * 0.03f, h * 0.78f, w * 0.97f, h * 0.96f, 1)
    }

    /**
     * "ГодехШтрих" (nom / narx / shtrix kod). Do'kondagi 1C yorlig'idan: nom markazi 22%, narx 50%,
     * chiziqlar 64%..86%, raqamlar ostida; harf balandligi 7.5% / 10%.
     */
    private fun drawBarcode(c: Canvas, d: LabelData, w: Float, h: Float) {
        boxText(c, d.name, h * 0.105f, w * 0.03f, h * 0.06f, w * 0.97f, h * 0.38f, 2)
        boxText(c, d.price, h * 0.14f, w * 0.03f, h * 0.41f, w * 0.97f, h * 0.59f, 1)
        val sym = BarcodeEncode.forShopCode(d.barcode) ?: return
        val top = h * 0.64f
        val bottom = h * 0.93f
        val digitsH = (bottom - top) * 0.25f
        val barsBottom = bottom - digitsH
        // Modul — butun nuqta (skaner aniq o'qisin); 1C'dagidek eni ~93%
        val total = sym.modules.size + 2 * sym.quiet
        val mw = maxOf(1, (w * 0.97f / total).toInt())
        val bw = sym.modules.size * mw
        val x0 = ((w - bw) / 2f).toInt().toFloat()
        val bar = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = false }
        val guards = guardModules(sym)
        var i = 0
        while (i < sym.modules.size) {
            if (sym.modules[i]) {
                var j = i
                while (j < sym.modules.size && sym.modules[j]) j++
                // EAN: chegara chiziqlari raqamlar orasiga uzunroq tushadi
                val long = (i until j).any { it in guards }
                c.drawRect(x0 + i * mw, top, x0 + j * mw, if (long) barsBottom + digitsH * 0.5f else barsBottom, bar)
                i = j
            } else i++
        }
        val pd = paint(digitsH * 0.95f, Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL))
        val base = bottom - digitsH * 0.08f
        when (sym.kind) {
            BarcodeEncode.Kind.EAN13 -> {
                val t = sym.text
                c.drawText(t.substring(0, 1), x0 - pd.measureText("0") - mw * 2, base, pd)
                spread(c, t.substring(1, 7), x0 + 3 * mw, x0 + 45 * mw, base, pd)
                spread(c, t.substring(7), x0 + 50 * mw, x0 + 92 * mw, base, pd)
            }
            BarcodeEncode.Kind.EAN8 -> {
                spread(c, sym.text.substring(0, 4), x0 + 3 * mw, x0 + 31 * mw, base, pd)
                spread(c, sym.text.substring(4), x0 + 36 * mw, x0 + 64 * mw, base, pd)
            }
            BarcodeEncode.Kind.CODE128 -> {
                while (pd.measureText(sym.text) > bw && pd.textSize > 8f) pd.textSize *= 0.94f
                c.drawText(sym.text, (w - pd.measureText(sym.text)) / 2f, base, pd)
            }
        }
    }

    /** EAN chegara (guard) modullari — boshida, o'rtada va oxirida. */
    private fun guardModules(sym: BarcodeEncode.Symbol): Set<Int> = when (sym.kind) {
        BarcodeEncode.Kind.EAN13 -> (0..2).toSet() + (45..49) + (92..94)
        BarcodeEncode.Kind.EAN8 -> (0..2).toSet() + (31..35) + (64..66)
        BarcodeEncode.Kind.CODE128 -> sym.modules.indices.toSet()
    }

    /** Raqamlarni [from]..[to] oralig'iga teng taqsimlab chizish (har biri o'z katagi markazida). */
    private fun spread(c: Canvas, s: String, from: Float, to: Float, base: Float, p: TextPaint) {
        val cell = (to - from) / s.length
        for ((k, ch) in s.withIndex()) {
            val t = ch.toString()
            c.drawText(t, from + cell * k + (cell - p.measureText(t)) / 2f, base, p)
        }
    }

    /** Qora-oq (1 bit) ko'rinishga: termoprinterda kulrang chiqmaydi. Chegara 170 — qalin shrift ingichkalashmasin. */
    private fun threshold(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            px[i] = if (lum < 170) Color.BLACK else Color.WHITE
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        return src
    }

    fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
