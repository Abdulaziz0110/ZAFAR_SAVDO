package uz.imsoft.shared.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.google.android.material.color.MaterialColors

/** v4.0.1: rangli doiraviy (halqa) grafik — ulushlar; o'rtada jami. Ranglar [ChartColors]dan. */
class DonutChart(context: Context) : View(context) {
    var values: List<Double> = emptyList()
        set(v) { field = v; invalidate() }
    var center: String = ""
        set(v) { field = v; invalidate() }
    var centerSub: String = ""
        set(v) { field = v; invalidate() }

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 15f * sp; isFakeBoldText = true }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 11f * sp }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = minOf(w, (170 * dp).toInt())
        setMeasuredDimension(w, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = values.sum()
        val size = minOf(width, height).toFloat()
        val stroke = size * 0.16f
        arc.strokeWidth = stroke
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f - stroke / 2f - 2 * dp
        rect.set(cx - r, cy - r, cx + r, cy + r)
        if (total <= 0) {
            arc.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
            canvas.drawArc(rect, 0f, 360f, false, arc)
        } else {
            var start = -90f
            val gap = if (values.count { it > 0 } > 1) 1.5f else 0f
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val sweep = (v / total * 360.0).toFloat()
                arc.color = ChartColors.at(i)
                canvas.drawArc(rect, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, arc)
                start += sweep
            }
        }
        big.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        small.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
        canvas.drawText(center, cx, cy + (if (centerSub.isEmpty()) 5 * dp else 0f), big)
        if (centerSub.isNotEmpty()) canvas.drawText(centerSub, cx, cy + 16 * dp, small)
    }
}

/** Grafik ranglari: kunduzgi va tungi rejimda ham yaxshi ko'rinadigan to'yingan ranglar. */
object ChartColors {
    private val PALETTE = intArrayOf(
        0xFF26A69A.toInt(), 0xFFFFA726.toInt(), 0xFF5C6BC0.toInt(), 0xFFEC407A.toInt(),
        0xFF66BB6A.toInt(), 0xFF29B6F6.toInt(), 0xFFAB47BC.toInt(), 0xFFFF7043.toInt(),
    )
    /** "Qolganlar" bo'lagi uchun. */
    const val OTHER = 0xFF90A4AE.toInt()
    const val SALES = 0xFF26A69A.toInt()
    const val PAYMENTS = 0xFF29B6F6.toInt()
    const val RETURNS = 0xFFFF7043.toInt()
    const val PROFIT = 0xFF66BB6A.toInt()
    const val UP = 0xFF43A047.toInt()
    const val DOWN = 0xFFE53935.toInt()

    fun at(i: Int): Int = PALETTE[((i % PALETTE.size) + PALETTE.size) % PALETTE.size]

    fun alpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
