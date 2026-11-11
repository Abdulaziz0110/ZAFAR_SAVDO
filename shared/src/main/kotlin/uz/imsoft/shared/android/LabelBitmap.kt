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
 *
 * v5.0.2: o'lchamlar 1C shakllaridan AYNAN olindi (PLU_Narx.mxl va Shtrix_chek.mxl shriftlari):
 *  - PLU yorlig'i:    nom Arial 16 qalin, narx Arial 26 qalin, "KOD: ..." Arial 20 qalin;
 *  - Shtrix yorlig'i: nom Arial 16 qalin, narx Arial 20 qalin, pastda shtrix kod.
 * Punkt (pt) printer nuqtasiga [dpi] bo'yicha o'tkaziladi (203 dpi da 16pt = 45 nuqta), shuning uchun
 * yorliq o'lchami o'zgarsa ham harflar 1C'dagidek chiqadi; faqat sig'masa hammasi birday kichrayadi.
 * Harflar sun'iy qalinlashtirilmaydi (oldin shunday edi — harflar bo'rtib ketardi), Arial Bold o'zining qalinligida.
 * Shrift: Arial bilan bir xil o'lchamli Liberation Sans Bold ([font] — ilova o'rnatadi), bo'lmasa tizimning qalin shrifti.
 */
object LabelBitmap {
    /** Ilova assets'dan yuklaydi (Liberation Sans Bold — Arial o'lchamlari). */
    @Volatile var font: Typeface? = null

    private fun bold(): Typeface = font ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    /** Silliq chiziqlar uchun 3 baravar katta chiziladi, keyin printer nuqtalariga kichraytiriladi (v3.1). */
    private const val SS = 3

    /** [w]×[h] — nuqtalarda (58×40 mm, 203 dpi = 464×320), [dpi] — printer zichligi (pt -> nuqta uchun). */
    fun render(d: LabelData, kind: LabelKind, w: Int, h: Int, dpi: Int = 203): Bitmap {
        val big = Bitmap.createBitmap(w * SS, h * SS, Bitmap.Config.ARGB_8888)
        val c = Canvas(big)
        c.drawColor(Color.WHITE)
        when (kind) {
            LabelKind.PLU -> drawPlu(c, d, (w * SS).toFloat(), (h * SS).toFloat(), dpi)
            LabelKind.BARCODE -> drawBarcode(c, d, (w * SS).toFloat(), (h * SS).toFloat(), w, dpi)
            LabelKind.BIO -> drawBio(c, d, (w * SS).toFloat(), (h * SS).toFloat(), dpi)
        }
        return downsample(big, w, h)
    }

    /** 1C punkti printer nuqtasiga: 16pt, 203 dpi -> 45 nuqta (chizish SS barobar kattaroq). */
    private fun pt(points: Float, dpi: Int): Float = points * dpi / 72f * SS

    /** Arial Bold — 1C'dagidek, qo'shimcha qalinlashtirishsiz (aks holda harflar bo'rtib ketadi). */
    private fun paint(size: Float, tf: Typeface = bold()) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = tf
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
        val p = paint(h * 0.16f).apply { isAntiAlias = false }
        val tw = p.measureText(text)
        c.drawRect(w / 2f - tw / 2 - 4, h / 2f - p.textSize * 0.9f, w / 2f + tw / 2 + 4, h / 2f + p.textSize * 0.3f, Paint().apply { color = Color.WHITE })
        c.drawText(text, (w - tw) / 2f, h / 2f + p.textSize * 0.2f, p)
        return bmp
    }

    /**
     * Bitta matn qatori (markazda). 1C'dagi o'lcham [size] dan boshlanadi; matn qutiga sig'masa FAQAT shu qator
     * kichrayadi (1C "Переносить" kabi, lekin hech narsa yo'qolmaydi).
     */
    private fun lineOf(text: String, size: Float, boxW: Int, maxLines: Int): StaticLayout {
        var s = size
        while (true) {
            val p = paint(s)
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, p, boxW).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false).setLineSpacing(0f, 0.95f).setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            val cut = (0 until sl.lineCount).any { sl.getEllipsisCount(it) > 0 || sl.getLineWidth(it) > boxW + 0.5f }
            if (!cut || s <= size * 0.55f) return sl
            s *= 0.94f
        }
    }

    /** Ustma-ust qatorlarni vertikal markazda chizadi. */
    private fun stack(c: Canvas, left: Float, h: Float, gap: Float, rows: List<StaticLayout>) {
        val total = rows.sumOf { it.height } + gap * (rows.size - 1)
        var y = (h - total) / 2f
        for (sl in rows) {
            c.save()
            c.translate(left, y)
            sl.draw(c)
            c.restore()
            y += sl.height + gap
        }
    }

    /**
     * PLU narx yorlig'i — 1C "PLU Narx" shakli: nom Arial 16 qalin, narx Arial 26 qalin, "KOD: ..." Arial 20 qalin,
     * hammasi markazda. Uchalasi birga sig'masa — nisbati saqlangan holda birday kichrayadi.
     */
    private fun drawPlu(c: Canvas, d: LabelData, w: Float, h: Float, dpi: Int) {
        val left = w * 0.03f
        val bw = (w * 0.94f).toInt().coerceAtLeast(10)
        val gap = h * 0.025f
        val code = "KOD:  " + d.plu
        var k = 1f
        var rows = listOf(lineOf(d.name, pt(16f, dpi), bw, 2), lineOf(d.price, pt(26f, dpi), bw, 1), lineOf(code, pt(20f, dpi), bw, 1))
        while (rows.sumOf { it.height } + 2 * gap > h * 0.96f && k > 0.5f) {
            k *= 0.94f
            rows = listOf(lineOf(d.name, pt(16f, dpi) * k, bw, 2), lineOf(d.price, pt(26f, dpi) * k, bw, 1), lineOf(code, pt(20f, dpi) * k, bw, 1))
        }
        stack(c, left, h, gap, rows)
    }

    /**
     * v5.2 (A7): mahsulot ma'lumoti yorlig'i — nom Arial 11 qalin, matn Arial 10.
     * 58x40 etiketkaga Arial 10 bilan ~278 belgi sig'adi; uzunroq bo'lsa shrift sal kichrayadi.
     */
    private fun drawBio(c: Canvas, d: LabelData, w: Float, h: Float, dpi: Int) {
        val left = w * 0.03f
        val bw = (w * 0.94f).toInt().coerceAtLeast(10)
        val gap = h * 0.02f
        var k = 1f
        fun rows() = listOf(
            lineOf(d.name, pt(11f, dpi) * k, bw, 2),
            lineOf(d.bio, pt(10f, dpi) * k, bw, 20),
        )
        var list = rows()
        while (list.sumOf { it.height } + gap > h * 0.97f && k > 0.55f) {
            k *= 0.95f
            list = rows()
        }
        stack(c, left, h, gap, list)
    }

    /**
     * Shtrixli yorliq — 1C "Shtrix chek" shakli: nom Arial 16 qalin, narx Arial 20 qalin, pastda shtrix kod va raqamlari.
     */
    private fun drawBarcode(c: Canvas, d: LabelData, w: Float, h: Float, finalW: Int, dpi: Int) {
        val left = w * 0.03f
        val bw = (w * 0.94f).toInt().coerceAtLeast(10)
        val gap = h * 0.02f
        val top = h * 0.60f
        val bottom = h * 0.95f
        var k = 1f
        var rows = listOf(lineOf(d.name, pt(16f, dpi), bw, 2), lineOf(d.price, pt(20f, dpi), bw, 1))
        while (rows.sumOf { it.height } + gap > top - h * 0.04f && k > 0.5f) {
            k *= 0.94f
            rows = listOf(lineOf(d.name, pt(16f, dpi) * k, bw, 2), lineOf(d.price, pt(20f, dpi) * k, bw, 1))
        }
        stack(c, left, top, gap, rows)
        val sym = BarcodeEncode.forShopCode(d.barcode) ?: return
        val digitsH = (bottom - top) * 0.25f
        val barsBottom = bottom - digitsH
        // Modul — butun nuqta (skaner aniq o'qisin); 1C'dagidek eni ~93%
        val total = sym.modules.size + 2 * sym.quiet
        val mw = maxOf(1, (finalW * 0.97f / total).toInt()) * SS
        val barsW = sym.modules.size * mw
        val x0 = (((w - barsW) / 2f).toInt() / SS * SS).toFloat()
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
        val pd = paint(digitsH * 0.95f)
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
                while (pd.measureText(sym.text) > barsW && pd.textSize > 8f) pd.textSize *= 0.94f
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
