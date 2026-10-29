package uz.imsoft.mijoz.ui

import android.content.Context
import android.content.res.Configuration
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.data.MijozPrefs
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.report.Money2
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun View.show(v: Boolean) { visibility = if (v) View.VISIBLE else View.GONE }

fun EditText.onTextChanged(block: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun afterTextChanged(s: Editable?) = block(s?.toString().orEmpty())
    })
}

fun Fragment.launchOnStarted(block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED, block) }
}

/** Pul ko'rinishi: so'm — guruhlangan, kerak bo'lsa 2 kasr; dollar — doim 2 kasr. */
object Fmt {
    private val sym = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' ' }
    fun som(v: Double): String = DecimalFormat("#,##0.##", sym).format(v)
    fun usd(v: Double): String = DecimalFormat("#,##0.00", sym).format(v)
    fun qty(v: Double): String = DecimalFormat("#,##0.###", sym).format(v)
    fun dateTime(ms: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(ms))
    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))
}

fun Context.money(v: Double, usd: Boolean): String =
    if (usd) Fmt.usd(v) + " $" else Fmt.som(v) + " " + getString(R.string.unit_som)

/** "1 250 000 so'm + 12.50 $" (nol qismlar ko'rsatilmaydi; ikkalasi nol bo'lsa "0 so'm"). */
fun Context.money2(m: Money2): String {
    val parts = ArrayList<String>()
    if (m.som != 0.0) parts += money(m.som, false)
    if (m.usd != 0.0) parts += money(m.usd, true)
    return if (parts.isEmpty()) money(0.0, false) else parts.joinToString(" + ")
}

/** Tarmoq/bulut xatosini tushunarli matnga. */
fun Context.describe(e: Throwable?): String = when {
    e == null -> ""
    e is CloudDb.CloudException && e.denied -> getString(R.string.err_denied)
    e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException -> getString(R.string.err_net)
    e is uz.imsoft.shared.report.SnapshotCodec.TooNew -> getString(R.string.err_too_new)
    else -> getString(R.string.err_generic, (e.message ?: e.javaClass.simpleName).take(120))
}

/** Ekran o'lchami va shrift (1-ilovadagi kabi). */
fun wrapScale(base: Context, prefs: MijozPrefs): Context {
    val scale = prefs.uiScale.coerceIn(0.75f, 1.3f)
    val font = prefs.fontScale.coerceIn(0.8f, 1.4f)
    if (scale == 1.0f && font == 1.0f) return base
    val cfg = Configuration(base.resources.configuration)
    if (scale != 1.0f) {
        val dpi = cfg.densityDpi.takeIf { it > 0 } ?: base.resources.displayMetrics.densityDpi
        val newDpi = (dpi * scale).toInt().coerceAtLeast(120)
        val k = dpi.toFloat() / newDpi
        cfg.densityDpi = newDpi
        if (cfg.screenWidthDp > 0) cfg.screenWidthDp = (cfg.screenWidthDp * k).toInt()
        if (cfg.screenHeightDp > 0) cfg.screenHeightDp = (cfg.screenHeightDp * k).toInt()
        if (cfg.smallestScreenWidthDp > 0) cfg.smallestScreenWidthDp = (cfg.smallestScreenWidthDp * k).toInt()
    }
    cfg.fontScale = cfg.fontScale * font
    return base.createConfigurationContext(cfg)
}
