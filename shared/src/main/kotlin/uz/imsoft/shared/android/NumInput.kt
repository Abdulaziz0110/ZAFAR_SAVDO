package uz.imsoft.shared.android

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.widget.EditText
import uz.imsoft.shared.NumText
import java.util.WeakHashMap

/**
 * v5.0: pul va miqdor maydonlari — yozayotganda minglik ajraladi ("12 000"), kursor o'z joyida qoladi.
 * Faqat kasrli raqam maydonlariga qo'yiladi (PLU, shtrix kod, telefon — tegilmaydi).
 *
 * Muhim: raqamli maydonning o'z filtri bo'shliqni o'chirib tashlaydi, shuning uchun bo'shliqqa ruxsat beruvchi
 * KeyListener qo'yiladi va klaviatura turi setRawInputType bilan raqamli qoldiriladi.
 */
object NumInput {
    private val attached = WeakHashMap<EditText, Boolean>()

    fun attach(e: EditText) {
        if (attached.put(e, true) == true) return
        val signed = e.inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0
        @Suppress("DEPRECATION")
        e.keyListener = DigitsKeyListener.getInstance(if (signed) "-0123456789., " else "0123456789., ")
        e.setRawInputType(
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
        )
        val start = e.text?.toString().orEmpty()
        if (start.isNotEmpty()) e.setText(NumText.group(start))
        e.addTextChangedListener(object : TextWatcher {
            private var busy = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (busy || s == null) return
                val old = s.toString()
                val formatted = NumText.group(old)
                if (formatted == old) return
                busy = true
                val keep = NumText.significantBefore(old, e.selectionStart)
                s.replace(0, s.length, formatted)
                e.setSelection(NumText.posAfter(formatted, keep).coerceIn(0, e.text?.length ?: 0))
                busy = false
            }
        })
    }

    /** Maydonga tayyor sonni qo'yish (ajratilgan ko'rinishda). */
    fun set(e: EditText, text: String) = e.setText(NumText.group(text))
}
