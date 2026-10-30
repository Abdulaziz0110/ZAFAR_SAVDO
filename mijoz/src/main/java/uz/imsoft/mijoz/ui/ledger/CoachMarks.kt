// NUSXA (tools/sync_ledger_kit.py) — o'zgartirmang. ui/ledger/ papkasi 2-ilovaga tools/sync_ledger_kit.py bilan ko'chiriladi — faqat shu yerda o'zgartiring.
package uz.imsoft.mijoz.ui.ledger

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import uz.imsoft.mijoz.R

/**
 * Ilovani ishlatayotganda o'rgatuvchi ko'rsatmalar: ekran birinchi ochilganda kerakli tugma atrofi yoritiladi va
 * qisqa izoh chiqadi ("Keyingi" / "Tushundim"). Har ekran bir marta; Sozlamalardan qayta yoqiladi yoki butunlay o'chiriladi.
 * Hech qanday ma'lumotga tegmaydi — faqat ko'rinish.
 */
object CoachMarks {
    class Step(@IdRes val target: Int, @StringRes val title: Int, @StringRes val text: Int)

    private const val PREFS = "coach_marks"
    private const val KEY_OFF = "off"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = !prefs(ctx).getBoolean(KEY_OFF, false)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_OFF, !on).apply()
    /** Hamma ekranlardagi ko'rsatmalar yana bir marta chiqsin. */
    fun reset(ctx: Context) = prefs(ctx).edit().clear().apply()
    fun seen(ctx: Context, key: String) = prefs(ctx).getBoolean("seen_$key", false)

    /** [key] ekrani uchun ko'rsatmalar hali ko'rilmagan bo'lsa (yoki [force]) ko'rsatadi. */
    fun show(f: Fragment, key: String, steps: List<Step>, force: Boolean = false) {
        val root = f.view ?: return
        val ctx = f.requireContext()
        if (!force && (!isEnabled(ctx) || seen(ctx, key))) return
        // Maket joylashib bo'lgach (o'lchamlar aniq) boshlaymiz.
        root.postDelayed({
            if (f.view == null || !f.isResumed) return@postDelayed
            val visible = steps.filter { s -> root.findViewById<View>(s.target)?.let { it.isShown && it.width > 0 } == true }
            if (visible.isEmpty()) return@postDelayed
            prefs(ctx).edit().putBoolean("seen_$key", true).apply()
            Overlay.attach(f, root, visible)
        }, 450)
    }

    @SuppressLint("ViewConstructor")
    private class Overlay(ctx: Context, private val root: View, private val steps: List<Step>) : FrameLayout(ctx) {
        private var index = 0
        private val hole = RectF()
        private val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(176, 0, 0, 0) }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f)
            color = MaterialColors.getColor(this@Overlay, com.google.android.material.R.attr.colorPrimary, Color.WHITE)
        }
        private val path = Path()
        private val card = MaterialCardView(ctx)
        private val tvStep = TextView(ctx)
        private val tvTitle = TextView(ctx)
        private val tvText = TextView(ctx)
        private val btnSkip = MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle)
        private val btnNext = MaterialButton(ctx)
        private val loc = IntArray(2)
        private val me = IntArray(2)
        var onClose: () -> Unit = {}

        init {
            setWillNotDraw(false)
            isClickable = true
            isFocusable = true
            card.radius = dp(20f)
            card.cardElevation = dp(6f)
            card.setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.WHITE))
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20), dpi(16), dpi(12), dpi(8)) }
            tvStep.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            tvStep.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLACK))
            tvTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            tvTitle.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
            tvText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            tvText.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY))
            tvText.setPadding(0, dpi(4), dpi(8), 0)
            box.addView(tvStep)
            box.addView(tvTitle)
            box.addView(tvText)
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
            btnSkip.setText(R.string.lg_tour_skip)
            row.addView(btnSkip)
            row.addView(btnNext)
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8) })
            card.addView(box)
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dpi(16); rightMargin = dpi(16) })
            btnSkip.setOnClickListener { onClose() }
            btnNext.setOnClickListener { if (index + 1 < steps.size) { index++; bind() } else onClose() }
            bind()
        }

        private fun dp(v: Float) = v * resources.displayMetrics.density
        private fun dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

        private fun bind() {
            val s = steps[index]
            tvStep.text = context.getString(R.string.lg_tour_step, index + 1, steps.size)
            tvStep.isVisible = steps.size > 1
            tvTitle.setText(s.title)
            tvText.setText(s.text)
            btnNext.setText(if (index + 1 < steps.size) R.string.lg_tour_next else R.string.lg_tour_done)
            btnSkip.isVisible = index + 1 < steps.size
            requestLayout()
            invalidate()
        }

        private fun target(): View? = root.findViewById(steps[index].target)

        private fun measureHole() {
            val t = target() ?: run { hole.setEmpty(); return }
            t.getLocationInWindow(loc)
            getLocationInWindow(me)
            val pad = dp(6f)
            hole.set(loc[0] - me[0] - pad, loc[1] - me[1] - pad, loc[0] - me[0] + t.width + pad, loc[1] - me[1] + t.height + pad)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            measureHole()
            // Karta — yoritilgan joyning pastida (joy bo'lsa), aks holda tepasida.
            val h = card.measuredHeight
            val gap = dpi(14)
            val top = if (hole.isEmpty) (height - h) / 2
            else if (hole.bottom + gap + h < height - dpi(24)) (hole.bottom + gap).toInt()
            else maxOf(dpi(24), (hole.top - gap - h).toInt())
            card.layout(card.left, top, card.right, top + h)
        }

        override fun onDraw(canvas: Canvas) {
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            if (!hole.isEmpty) path.addRoundRect(hole, dp(14f), dp(14f), Path.Direction.CW)
            canvas.drawPath(path, scrim)
            if (!hole.isEmpty) canvas.drawRoundRect(hole, dp(14f), dp(14f), ring)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Yoritilgan joyga tegilsa — keyingi qadam (asl tugma bosilmaydi: tasodifiy amal bo'lmasin).
            if (event.action == MotionEvent.ACTION_UP && hole.contains(event.x, event.y)) btnNext.performClick()
            return true
        }

        companion object {
            fun attach(f: Fragment, root: View, steps: List<Step>) {
                val decor = f.requireActivity().window.decorView as? ViewGroup ?: return
                val o = Overlay(f.requireContext(), root, steps)
                val observer = object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) { decor.removeView(o) }
                }
                o.onClose = {
                    decor.removeView(o)
                    runCatching { f.viewLifecycleOwner.lifecycle.removeObserver(observer) }
                }
                decor.addView(o, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                f.viewLifecycleOwner.lifecycle.addObserver(observer)
            }
        }
    }
}
