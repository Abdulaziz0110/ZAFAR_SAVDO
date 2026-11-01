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

    /** Silliq chiziqlar uchun 3 baravar katta chiziladi, keyin printer nuqtalariga kichraytiriladi (v3.1). */
    private const val SS = 3

    /** [w]×[h] — nuqtalarda (58×40 mm, 203 dpi = 464×320). */
    fun render(d: LabelData, kind: LabelKind, w: Int, h: Int): Bitmap {
        val big = Bitmap.createBitmap(w * SS, h * SS, Bitmap.Config.ARGB_8888)
        val c = Canvas(big)
        c.drawColor(Color.WHITE)
        when (kind) {
            LabelKind.PLU -> drawPlu(c, d, (w * SS).toFloat(), (h * SS).toFloat())
            LabelKind.BARCODE -> drawBarcode(c, d, (w * SS).toFloat(), (h * SS).toFloat(), w)
        }
        return downsample(big, w, h)
    }

    /** Qalin harflar: Arial Bold + ozgina chiziq (Windows drayveri chiqargandek to'q). */
    private fun paint(size: Float, tf: Typeface = bold(), heavy: Boolean = true) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = tf
        if (heavy) {
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = size * 0.035f
            strokeJoin = Paint.Join.ROUND
        }
    }

    /** SS×SS blok o'rtachasi -> qora/oq (chegara 150: qalinroq, uzilmaydi). */
    private fun downsample(big: Bitmap, w: Int, h: Int): Bitmap {
        val bw = big.width
        val src = IntArray(bw * big.height)
        big.getPixels(src, 0, bw, 0, 0, bw, big.height)
        big.recycle()
        val out = IntArray(w * h)
        val n = SS * SS
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            for (dy in 0 until SS) {
                val row = (y * SS + dy) * bw + x * SS
                for (dx in 0 until SS) sum += src[row + dx] and 0xFF
            }
            out[y * w + x] = if (sum / n < 150) Color.BLACK else Color.WHITE
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Kalibrlash yorlig'i: qog'oz chegarasi (ramka), markaz chiziqlari va o'lcham. */
    fun calibration(w: Int, h: Int, text: String): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val line = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = false }
        c.drawRect(1.5f, 1.5f, w - 1.5f, h - 1.5f, line)
        line.strokeWidth = 1f
        c.drawLine(w / 2f, 0f, w / 2f, h.toFloat(), line)
        c.drawLine(0f, h / 2f, w.toFloat(), h / 2f, line)
        val p = paint(h * 0.16f, heavy = false).apply { isAntiAlias = false }
        val tw = p.measureText(text)
        c.drawRect(w / 2f - tw / 2 - 4, h / 2f - p.textSize * 0.9f, w / 2f + tw / 2 + 4, h / 2f + p.textSize * 0.3f, Paint().apply { color = Color.WHITE })
        c.drawText(text, (w - tw) / 2f, h / 2f + p.textSize * 0.2f, p)
        return bmp
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
            if ((sl.height <= bh && !ellipsized && !wide) || s < 9f * SS) break
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
    private fun drawBarcode(c: Canvas, d: LabelData, w: Float, h: Float, finalW: Int) {
        boxText(c, d.name, h * 0.105f, w * 0.03f, h * 0.06f, w * 0.97f, h * 0.38f, 2)
        boxText(c, d.price, h * 0.14f, w * 0.03f, h * 0.41f, w * 0.97f, h * 0.59f, 1)
        val sym = BarcodeEncode.forShopCode(d.barcode) ?: return
        val top = h * 0.64f
        val bottom = h * 0.93f
        val digitsH = (bottom - top) * 0.25f
        val barsBottom = bottom - digitsH
        // Modul — butun nuqta (skaner aniq o'qisin); 1C'dagidek eni ~93%
        val total = sym.modules.size + 2 * sym.quiet
        // Modul eni printer nuqtasida butun son (skaner aniq o'qisin), keyin katta chizish masshtabiga
        val mw = maxOf(1, (finalW * 0.97f / total).toInt()) * SS
        val bw = sym.modules.size * mw
        val x0 = (((w - bw) / 2f).toInt() / SS * SS).toFloat()
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
        val pd = paint(digitsH * 0.95f, bold(), heavy = false)
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

    fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
