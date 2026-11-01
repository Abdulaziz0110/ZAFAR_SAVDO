package uz.imsoft.shared.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * v4.0: oddiy ustunli grafik (kun/hafta/oy/yil). Kutubxonasiz — Canvas. Ustun bosilsa uning qiymati tepada yoziladi.
 * Ranglar ilova mavzusidan (kunduzgi/tungi rejimda ham to'g'ri).
 */
class BarChart(context: Context) : View(context) {
    var bars: List<Pair<String, Double>> = emptyList()
        set(v) { field = v; selected = v.lastIndex; invalidate() }
    var format: (Double) -> String = { "%.0f".format(it) }
    var onSelect: ((Int) -> Unit)? = null
    private var selected = -1

    private val dp = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f * sp; textAlign = Paint.Align.CENTER }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * sp; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val grid = Paint().apply { strokeWidth = dp }
    private val rect = RectF()

    init {
        minimumHeight = (180 * dp).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, resolveSize((180 * dp).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = bars.size
        if (n == 0) return
        val primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
        val soft = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer)
        val onVar = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val on = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        text.color = onVar
        value.color = on
        grid.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
        val top = 26 * dp
        val bottom = height - 20 * dp
        val w = width.toFloat()
        val max = bars.maxOf { it.second }.coerceAtLeast(1.0)
        canvas.drawLine(0f, bottom, w, bottom, grid)
        val slot = w / n
        val bw = (slot * 0.62f).coerceAtMost(36 * dp)
        bars.forEachIndexed { i, (label, v) ->
            val cx = slot * i + slot / 2
            val h = ((v / max) * (bottom - top)).toFloat().coerceAtLeast(if (v > 0) 2 * dp else 0f)
            bar.color = if (i == selected) primary else soft
            rect.set(cx - bw / 2, bottom - h, cx + bw / 2, bottom)
            canvas.drawRoundRect(rect, 4 * dp, 4 * dp, bar)
            // Yorliqlar ko'p bo'lsa — har ikkinchisi
            if (n <= 12 || i % 2 == (n - 1) % 2) canvas.drawText(label, cx, height - 5 * dp, text)
        }
        if (selected in bars.indices) {
            val cx = (slot * selected + slot / 2).coerceIn(40 * dp, w - 40 * dp)
            canvas.drawText(format(bars[selected].second), cx, 16 * dp, value)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bars.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val i = (event.x / (width.toFloat() / bars.size)).toInt().coerceIn(0, bars.lastIndex)
            if (i != selected) { selected = i; invalidate(); onSelect?.invoke(i) }
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return event.action == MotionEvent.ACTION_UP
    }
}
