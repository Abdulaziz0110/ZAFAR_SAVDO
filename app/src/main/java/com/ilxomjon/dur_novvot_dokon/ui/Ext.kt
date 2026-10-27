package com.ilxomjon.dur_novvot_dokon.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerError
import com.ilxomjon.dur_novvot_dokon.data.remote.ServerException
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun View.showInvisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.INVISIBLE
}

/** Matn o'zgarganda chaqiriladi. [fromUser] - faqat foydalanuvchi yozganda (dasturiy setText'da emas). */
fun EditText.onTextChanged(block: (text: String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = block(s?.toString().orEmpty())
    })
}

/** Dasturiy ravishda matn qo'yadi (TextWatcher'larni qayta ishga tushirmasdan). */
fun EditText.setTextSilently(text: String, watcher: (() -> Unit)? = null) {
    if (this.text?.toString() == text) return
    setText(text)
    setSelection(text.length)
}

inline fun <reified VM : ViewModel> Fragment.viewModel(
    owner: ViewModelStoreOwner = this,
    crossinline make: () -> VM,
): VM {
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = make() as T
    }
    return ViewModelProvider(owner, factory)[VM::class.java]
}

/** Fragment ko'rinishi ko'rinadigan paytda oqimni kuzatadi. */
fun Fragment.launchOnStarted(block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}

fun Fragment.navigator(): Navigator = requireActivity() as Navigator

/** Xatoni foydalanuvchiga tushunarli o'zbekcha matnga aylantiradi. */
fun Context.describeError(t: Throwable): String {
    if (t is ServerException) {
        val base = getString(
            when (t.kind) {
                ServerError.NOT_CONFIGURED -> R.string.err_not_configured
                ServerError.CONNECT -> R.string.err_connect
                ServerError.TIMEOUT -> R.string.err_timeout
                ServerError.QUERY, ServerError.REJECTED -> R.string.err_query
            },
        )
        val detail = t.message?.takeIf { it.isNotBlank() && it != t.kind.name }?.take(160)
        return if (detail != null && t.kind != ServerError.NOT_CONFIGURED) base + "\n" + getString(R.string.err_details, detail) else base
    }
    return getString(R.string.err_unknown)
}

fun Context.money(v: Double, currency: Currency): String =
    Money.format(v, currency) + " " + getString(if (currency == Currency.SOM) R.string.unit_som else R.string.unit_usd)

fun formatDateTime(millis: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(millis))
fun formatDate(d: Date): String = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(d)
