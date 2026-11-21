package com.ilxomjon.dur_novvot_dokon.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * v5.3.12: o'lchami o'zgarmaydigan maydonda rasmni ikki barmoq bilan kattalashtirish/kichiklashtirish, surish,
 * ikki marta bosib yaqinlashtirish. [focusBand] — rasmning berilgan qatorini (bo'yining ulushi) maydon o'rtasiga olib keladi.
 */
class ZoomImageView(ctx: Context) : AppCompatImageView(ctx) {
    private val m = Matrix()
    private var bmpW = 0
    private var bmpH = 0
    private var fit = 1f
    private val values = FloatArray(9)

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val cur = scale()
            val k = (cur * d.scaleFactor).coerceIn(fit, fit * 8f) / cur
            m.postScale(k, k, d.focusX, d.focusY)
            clamp()
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            m.postTranslate(-dx, -dy)
            clamp()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val cur = scale()
            val target = if (cur > fit * 1.5f) fit else fit * 2.5f
            m.postScale(target / cur, target / cur, e.x, e.y)
            clamp()
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    fun show(b: Bitmap?) {
        setImageBitmap(b)
        bmpW = b?.width ?: 0
        bmpH = b?.height ?: 0
        post { reset() }
    }

    /** Rasmni maydon eniga moslab, tepadan. */
    fun reset() {
        if (bmpW == 0 || width == 0) return
        fit = minOf(width.toFloat() / bmpW, height.toFloat() / bmpH)
        m.reset()
        m.postScale(fit, fit)
        m.postTranslate((width - bmpW * fit) / 2f, (height - bmpH * fit) / 2f)
        imageMatrix = m
    }

    /** Qatorni ([y0]..[y1] — rasm bo'yining ulushi) maydon o'rtasiga, eniga to'liq ko'rinadigan qilib. */
    fun focusBand(y0: Double, y1: Double) {
        if (bmpW == 0 || width == 0) { post { focusBand(y0, y1) }; return }
        fit = minOf(width.toFloat() / bmpW, height.toFloat() / bmpH)
        val band = ((y1 - y0).coerceAtLeast(0.02) * bmpH).toFloat()
        val s = minOf(width.toFloat() / bmpW, height * 0.45f / band).coerceIn(fit, fit * 8f)
        val cy = ((y0 + y1) / 2.0 * bmpH).toFloat()
        m.reset()
        m.postScale(s, s)
        m.postTranslate((width - bmpW * s) / 2f, height / 2f - cy * s)
        clamp()
    }

    private fun scale(): Float {
        m.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    /** Rasm maydondan chiqib ketmasin (kichik bo'lsa — o'rtada). */
    private fun clamp() {
        val r = RectF(0f, 0f, bmpW.toFloat(), bmpH.toFloat())
        m.mapRect(r)
        var dx = 0f
        var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2f - r.left
        else if (r.left > 0) dx = -r.left
        else if (r.right < width) dx = width - r.right
        if (r.height() <= height) dy = (height - r.height()) / 2f - r.top
        else if (r.top > 0) dy = -r.top
        else if (r.bottom < height) dy = height - r.bottom
        m.postTranslate(dx, dy)
        imageMatrix = m
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        reset()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // aylantiriladigan sahifa ichida: rasmni surayotganda sahifa siljimasin
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        scaler.onTouchEvent(e)
        gestures.onTouchEvent(e)
        return true
    }
}
