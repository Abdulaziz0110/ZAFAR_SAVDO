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

/** Chekni rasmga chizish (oq fon, qora matn) — ulashish (PNG) va chek printeri (rastr) uchun. */
object ReceiptBitmap {
    /** [width] — nuqtalarda: 80 mm = 576, 58 mm = 384. */
    fun render(r: Receipt, width: Int = 576): Bitmap {
        val k = width / 576f
        val pad = (16 * k)
        val inner = (width - 2 * pad).toInt()
        fun paint(size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size * k
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val pHeader = paint(34f, true)
        val pTitle = paint(28f, true)
        val pMeta = paint(24f)
        val pName = paint(26f, true)
        val pText = paint(25f)
        val pTotal = paint(26f)
        val pTotalB = paint(32f, true)
        val pNote = paint(20f)

        // Avval balandlikni hisoblash uchun ikki marta chiziladi: o'lchash va chizish.
        class Op(val h: Float, val draw: (Canvas, Float) -> Unit)
        val ops = ArrayList<Op>()
        fun block(text: String, p: TextPaint, align: Layout.Alignment, gapAfter: Float = 4f) {
            if (text.isEmpty()) return
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, p, inner).setAlignment(align).setIncludePad(false).build()
            ops += Op(sl.height + gapAfter * k) { c, y -> c.save(); c.translate(pad, y); sl.draw(c); c.restore() }
        }
        fun row(left: String, right: String, p: TextPaint, gapAfter: Float = 6f) {
            val rw = p.measureText(right)
            val lw = (inner - rw - 12 * k).toInt().coerceAtLeast((inner * 0.4f).toInt())
            val sl = StaticLayout.Builder.obtain(left, 0, left.length, p, lw).setIncludePad(false).build()
            val h = maxOf(sl.height.toFloat(), p.fontSpacing)
            ops += Op(h + gapAfter * k) { c, y ->
                c.save(); c.translate(pad, y); sl.draw(c); c.restore()
                c.drawText(right, width - pad - rw, y - p.ascent(), p)
            }
        }
        fun sep() {
            ops += Op(18 * k) { c, y ->
                val sp = Paint().apply { color = Color.BLACK; strokeWidth = 2 * k }
                var x = pad
                while (x < width - pad) { c.drawLine(x, y + 8 * k, minOf(x + 10 * k, width - pad), y + 8 * k, sp); x += 18 * k }
            }
        }
        ops += Op(8 * k) { _, _ -> }
        block(r.header, pHeader, Layout.Alignment.ALIGN_CENTER, 6f)
        block(r.title, pTitle, Layout.Alignment.ALIGN_CENTER, 6f)
        block(r.client, pMeta, Layout.Alignment.ALIGN_CENTER, 2f)
        block(r.dateText, pMeta, Layout.Alignment.ALIGN_CENTER, 6f)
        sep()
        for ((i, x) in r.lines.withIndex()) {
            block("${i + 1}. ${x.name}", pName, Layout.Alignment.ALIGN_NORMAL, 2f)
            row(x.detail, x.amount, pText, 10f)
        }
        sep()
        for (t in r.totals) row(t.label, t.value, if (t.bold) pTotalB else pTotal, 6f)
        for (n in r.notes) { ops += Op(10 * k) { _, _ -> }; block(n, pNote, Layout.Alignment.ALIGN_CENTER) }
        ops += Op(16 * k) { _, _ -> }

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
