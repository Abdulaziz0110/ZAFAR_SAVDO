package uz.imsoft.shared.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import uz.imsoft.shared.receipt.Dither
import uz.imsoft.shared.receipt.EscPosRaster
import uz.imsoft.shared.receipt.Receipt
import uz.imsoft.shared.receipt.ReceiptStyle
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Chekni rasmga chizish (oq fon, qora matn) — ulashish (PNG) va chek printeri (rastr) uchun.
 * Ko'rinish [ReceiptStyle] bo'yicha: logo, do'kon nomi, sarlavha osti, Xodim/Chek №/Vaqt, Mijoz, ramkali qatorlar,
 * katta "Summa", qarz qoldig'i, telefonlar va rahmat xabari. Rasm bo'lgani uchun kirill/lotin printer kod sahifasiga bog'liq emas.
 */
object ReceiptBitmap {
    /** Eski chaqiruvlar uchun: faqat eni berilgan standart ko'rinish. */
    fun render(r: Receipt, width: Int): Bitmap =
        render(r, ReceiptStyle.STANDARD.copy(paper = if (width <= 400) 58 else 80), null)

    fun render(r0: Receipt, style: ReceiptStyle = ReceiptStyle.STANDARD, logo: Bitmap? = null): Bitmap {
        val st = style.normalized()
        val r = st.apply(r0)
        val width = st.width
        val k = width / 576f
        val ts = st.text / 100f
        val pad = 10 * k
        val inner = (width - 2 * pad).toInt()
        fun face(bold: Boolean, serif: Boolean): Typeface = when {
            serif -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            st.arial && bold -> PrintFonts.bold ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            st.arial -> PrintFonts.regular ?: Typeface.SANS_SERIF
            else -> Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun paint(size: Float, bold: Boolean = false, serif: Boolean = false, spacing: Float = 0f, scale: Float = ts) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                val b = bold || st.bold
                color = Color.BLACK
                textSize = size * k * scale
                typeface = face(b, serif)
                if (b && serif) isFakeBoldText = true
                letterSpacing = spacing
            }
        val pHeader = paint(46f, bold = true, serif = st.headerSerif, scale = st.header / 100f)
        val pSub = paint(22f)
        val pInfo = paint(24f)
        val pClient = paint(30f, bold = true)
        val pBanner = paint(32f, bold = true, spacing = 0.25f)
        val pName = paint(27f)
        val pFormula = paint(27f)
        val pTotal = paint(27f)
        val pSum = paint(38f, bold = true)
        val pFooter = paint(30f, bold = true, spacing = 0.06f)
        val pThanks = paint(28f, bold = true)
        val pNote = paint(19f)
        val line = Paint().apply { color = Color.BLACK; strokeWidth = 2.5f * k; this.style = Paint.Style.STROKE }

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
        if (st.logo && logo != null && logo.width > 0 && logo.height > 0) {
            val lb = logoFor(logo, width * st.logoWidth / 100, st.dark)
            ops += Op(lb.height + 10 * k) { c, y -> c.drawBitmap(lb, ((width - lb.width) / 2).toFloat(), y, null) }
        }
        block(r.header, pHeader, Layout.Alignment.ALIGN_CENTER, if (r.subheader.isEmpty()) 14f else 4f)
        for ((i, s) in r.subheader.withIndex()) block(s, pSub, Layout.Alignment.ALIGN_CENTER, if (i == r.subheader.lastIndex) 12f else 2f)
        if (r0.info.isEmpty()) {
            // Eski tuzilish (Xodim/Vaqt yo'q): sarlavha va sana markazda
            block(r.title, pClient, Layout.Alignment.ALIGN_CENTER, 2f)
            if (st.date || st.time) block(r.dateText, pInfo, Layout.Alignment.ALIGN_CENTER, 8f)
        } else if (r.info.isEmpty()) {
            // Xodim va vaqt yashirilgan — faqat sana (o'ngda), u ham yashirilgan bo'lsa hech narsa
            if (r.date.isNotEmpty()) block(r.date, pInfo, Layout.Alignment.ALIGN_OPPOSITE, 8f)
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
        // Qatorlar: "1 ) Nomi" va o'ngda "40 x 117 000 = 4 680 000" (ramkada yoki ramkasiz)
        val cellPad = 8 * k
        if (!st.frame && r.lines.isNotEmpty()) rule(4f)
        for ((i, x) in r.lines.withIndex()) {
            val name = if (st.lineNo) "${i + 1} ) ${x.name}" else x.name
            val nl = layout(name, pName, (inner - 2 * cellPad).toInt())
            val fl = layout(x.formula, pFormula, (inner - 2 * cellPad).toInt(), Layout.Alignment.ALIGN_OPPOSITE)
            val h = cellPad + nl.height + 2 * k + fl.height + cellPad * 0.6f
            val framed = st.frame
            ops += Op(h) { c, y ->
                if (framed) c.drawRect(pad, y, width - pad, y + h, line)
                c.save(); c.translate(pad + cellPad, y + cellPad * 0.6f); nl.draw(c); c.restore()
                c.save(); c.translate(pad + cellPad, y + cellPad * 0.6f + nl.height + 2 * k); fl.draw(c); c.restore()
            }
        }
        if (!st.frame && r.lines.isNotEmpty()) { gap(4f); rule(0f) }
        gap(16f)
        for (t in r.totals) {
            if (t.bold) row(t.label + ":", t.value, pSum, pSum, 10f, indent = 50f)
            else row(t.label + ":", t.value, pTotal, pTotal, 6f, indent = 50f)
        }
        for (t in r.after) row(t.label + ":", t.value, pTotal, pTotal, 6f, indent = 50f)
        if (r.footer.isNotEmpty()) gap(8f)
        for (f in r.footer) block(f, pFooter, Layout.Alignment.ALIGN_NORMAL, 6f, pad + 40 * k, (inner - 40 * k).toInt())
        if (r.thanks.isNotEmpty()) { gap(10f); block(r.thanks, pThanks, Layout.Alignment.ALIGN_CENTER, 4f) }
        for (n in r.notes) { gap(10f); block(n, pNote, Layout.Alignment.ALIGN_CENTER) }
        gap(18f)
        // 203 dpi: 1 mm = 8 nuqta
        if (st.feed > 0) ops += Op(st.feed * 8f) { _, _ -> }

        val height = ops.sumOf { it.h.toDouble() }.toInt().coerceAtLeast(10)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        var y = 0f
        for (op in ops) { op.draw(c, y); y += op.h }
        return bmp
    }

    /** Logoni kerakli eniga keltirib, nuqtali (dither) qora-oq qiladi — termoprinterda kulrang ham ko'rinadi. */
    private fun logoFor(src: Bitmap, targetW: Int, dark: Int): Bitmap {
        val w = targetW.coerceAtLeast(16)
        val h = (src.height.toLong() * w / src.width).toInt().coerceIn(1, w * 2)
        val s = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        val lum = IntArray(w * h) { i ->
            val c = px[i]
            val a = Color.alpha(c)
            val l = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            // shaffof joy — oq
            (l * a + 255 * (255 - a)) / 255
        }
        // Chegara qoralik sozlamasiga mos (140 standart -> 128)
        val black = Dither.floyd(w, h, lum, (dark - 12).coerceIn(80, 220))
        for (i in px.indices) px[i] = if (black[i]) Color.BLACK else Color.WHITE
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Foydalanuvchi tanlagan rasmni saqlash uchun: kulrang, oq fon, 576×576 dan katta emas, PNG [MAX_LOGO_BYTES] dan
     * kichik (2-ilovalarga ham boradi). O'qib bo'lmasa — null.
     */
    fun normalizeLogo(bytes: ByteArray): ByteArray? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 576) sample *= 2
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        var scale = minOf(1f, 576f / maxOf(b.width, b.height))
        repeat(6) {
            val w = (b.width * scale).toInt().coerceAtLeast(16)
            val h = (b.height * scale).toInt().coerceAtLeast(1)
            val s = Bitmap.createScaledBitmap(b, w, h, true)
            val px = IntArray(w * h)
            s.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) {
                val c = px[i]
                val a = Color.alpha(c)
                val l = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                val g = (l * a + 255 * (255 - a)) / 255
                px[i] = Color.rgb(g, g, g)
            }
            val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            flat.setPixels(px, 0, w, 0, 0, w, h)
            val out = ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            if (out.size <= MAX_LOGO_BYTES) return out
            scale *= 0.75f
        }
        return null
    }

    const val MAX_LOGO_BYTES = 60_000

    fun decodeLogo(bytes: ByteArray?): Bitmap? =
        if (bytes == null || bytes.isEmpty()) null else runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    fun toEscPos(bmp: Bitmap, dark: Int = 140): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val black = BooleanArray(w * h) { i ->
            val c = px[i]
            val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            lum < dark
        }
        return EscPosRaster.encode(w, h, black)
    }

    fun savePng(bmp: Bitmap, file: File): File {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
