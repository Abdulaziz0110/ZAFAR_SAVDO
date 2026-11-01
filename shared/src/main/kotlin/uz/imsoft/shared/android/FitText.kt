package uz.imsoft.shared.android

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * v4.0: katta shriftli telefonda tugma yozuvi chegaradan chiqmasin. Tanlov guruhlaridagi (narx turi, valyuta, sotuv/qaytarish,
 * sozlamalardagi 1/2/3 soat va h.k.) har tugma matni sig'maguncha kichraytiriladi (eng kami 9sp) — "Do…", "O'c…" bo'lmaydi.
 * Hamma ekranga avtomatik: [install] Activity'da bir marta chaqiriladi.
 */
object FitText {
    private const val MIN_SP = 9f

    fun install(activity: FragmentActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) = attach(v)
        }, true)
        activity.findViewById<View>(android.R.id.content)?.let { attach(it) }
    }

    /** [root] ichidagi barcha tanlov guruhlarini kuzatadi (o'lcham o'zgarsa qayta moslaydi). */
    fun attach(root: View) {
        walk(root) { g ->
            if (!seen.containsKey(g)) {
                seen[g] = true
                g.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) (v as ViewGroup).post { fitGroup(v) } }
                g.post { fitGroup(g) }
            }
        }
    }

    private fun walk(v: View, f: (ViewGroup) -> Unit) {
        if (v is MaterialButtonToggleGroup) f(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
    }

    private fun fitGroup(g: ViewGroup) {
        for (i in 0 until g.childCount) (g.getChildAt(i) as? MaterialButton)?.let { fit(it) }
    }

    /** Bitta tugma: asl o'lchamdan boshlab, matn eniga sig'maguncha kichraytiradi. */
    fun fit(b: TextView) {
        val w = b.width - b.compoundPaddingLeft - b.compoundPaddingRight
        if (w <= 0 || b.text.isNullOrEmpty()) return
        val dm = b.resources.displayMetrics
        val orig = sizes[b] ?: b.textSize.also { sizes[b] = it }
        val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_SP, dm)
        val words = b.text.toString().split(' ', '\n').filter { it.isNotEmpty() }
        val paint = android.text.TextPaint(b.paint)
        var size = orig
        while (size > minPx) {
            paint.textSize = size
            // Eng uzun so'z bir qatorga sig'sin (2 qatorga bo'linishi mumkin, so'z bo'linmasin)
            if (words.all { paint.measureText(it) <= w }) break
            size -= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 0.5f, dm)
        }
        if (kotlin.math.abs(b.textSize - size) > 0.5f) b.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.coerceAtLeast(minPx))
    }

    // View.setTag(kalit) resurs id talab qiladi — shuning uchun zaif xaritalar (ekran yopilsa o'zi tozalanadi)
    private val seen = java.util.WeakHashMap<View, Boolean>()
    private val sizes = java.util.WeakHashMap<TextView, Float>()
}
