// NUSXA (tools/sync_ledger_kit.py) — o'zgartirmang. ui/ledger/ papkasi 2-ilovaga tools/sync_ledger_kit.py bilan ko'chiriladi — faqat shu yerda o'zgartiring.
package uz.imsoft.mijoz.ui.ledger

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import uz.imsoft.mijoz.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uz.imsoft.shared.receipt.NumFmt
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerFilter
import uz.imsoft.shared.report.LedgerIndex
import uz.imsoft.shared.report.LedgerQuery
import uz.imsoft.shared.report.Stamp
import java.io.File
import java.util.Calendar
import kotlin.random.Random

/**
 * "Sirli menyu" (Sozlamalar ▸ eng pastdagi versiya yozuvini uzoq bosish). Ikki ilovada umumiy qism shu yerda;
 * ilovaga xos bandlar [SecretFragment.appItems] orqali qo'shiladi.
 * Qoida: bu yerdagi hech bir band serverga (1C / SQL) yozmaydi va maxfiy kalit yoki parollarni ko'rsatmaydi.
 */
class SecretItem(val emoji: String, val title: String, val sub: String, val action: () -> Unit)

/** Sirli sozlamalar (oddiy SharedPreferences — maxfiy narsa yo'q). */
object SecretPrefs {
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE)

    fun autoDark(ctx: Context) = p(ctx).getBoolean("auto_dark", false)
    fun setAutoDark(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("auto_dark", v).apply()
    fun darkFrom(ctx: Context) = p(ctx).getInt("dark_from", 19)
    fun darkTo(ctx: Context) = p(ctx).getInt("dark_to", 7)
    fun setDarkHours(ctx: Context, from: Int, to: Int) = p(ctx).edit().putInt("dark_from", from).putInt("dark_to", to).apply()
    fun secure(ctx: Context) = p(ctx).getBoolean("secure", false)
    fun setSecure(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("secure", v).apply()
    fun launches(ctx: Context) = p(ctx).getInt("launches", 0)
    fun firstLaunch(ctx: Context) = p(ctx).getLong("first", 0L)
    fun found(ctx: Context) = p(ctx).getStringSet("found", emptySet()).orEmpty()
    fun markFound(ctx: Context, key: String) {
        val s = found(ctx).toMutableSet()
        if (s.add(key)) p(ctx).edit().putStringSet("found", s).apply()
    }

    /** MainActivity.onCreate (yangi ochilishda). */
    fun countLaunch(ctx: Context) {
        val pr = p(ctx)
        val e = pr.edit().putInt("launches", pr.getInt("launches", 0) + 1)
        if (pr.getLong("first", 0L) == 0L) e.putLong("first", System.currentTimeMillis())
        e.apply()
    }

    /** MainActivity.onResume: ekran himoyasi va soatga qarab qorong'i rejim. */
    fun apply(a: Activity) {
        if (secure(a)) a.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else a.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (!autoDark(a)) return
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val f = darkFrom(a); val t = darkTo(a)
        val night = if (f <= t) h in f until t else h >= f || h < t
        val mode = if (night) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
    }
}

/** Ekran bo'ylab konfetti (2,5 soniya). */
@SuppressLint("ViewConstructor")
class ConfettiView(ctx: Context) : View(ctx) {
    private class P(var x: Float, var y: Float, val vx: Float, var vy: Float, val c: Int, val s: Float, var r: Float, val vr: Float)
    private val parts = ArrayList<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = System.currentTimeMillis()
    private val colors = intArrayOf(0xFF00897B.toInt(), 0xFFF2B233.toInt(), 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFF43A047.toInt())

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        parts.clear()
        val d = resources.displayMetrics.density
        repeat(140) {
            parts += P(Random.nextFloat() * w, -Random.nextFloat() * h * 0.5f, (Random.nextFloat() - 0.5f) * 4 * d, (2 + Random.nextFloat() * 5) * d,
                colors[Random.nextInt(colors.size)], (4 + Random.nextFloat() * 6) * d, Random.nextFloat() * 360, (Random.nextFloat() - 0.5f) * 20)
        }
    }

    override fun onDraw(c: Canvas) {
        for (p in parts) {
            p.x += p.vx; p.y += p.vy; p.vy += 0.08f * resources.displayMetrics.density; p.r += p.vr
            paint.color = p.c
            c.save(); c.rotate(p.r, p.x, p.y); c.drawRect(p.x - p.s, p.y - p.s / 2, p.x + p.s, p.y + p.s / 2, paint); c.restore()
        }
        if (System.currentTimeMillis() - start < 2600) postInvalidateOnAnimation() else (parent as? ViewGroup)?.removeView(this)
    }

    companion object {
        fun burst(a: Activity) {
            val decor = a.window.decorView as? ViewGroup ?: return
            decor.addView(ConfettiView(a), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
}

/** Sirli menyu ekrani. Ilova [appItems], [close], va kerak bo'lsa [usdRate]/[products] ni beradi. */
abstract class SecretFragment : Fragment() {
    /** 1C tarixi (qidiruv laboratoriyasi, CSV, top-10). null — bu bandlar ko'rinmaydi. */
    protected abstract val ledgerHost: LedgerHost?
    protected abstract fun appItems(): List<SecretItem>
    protected abstract fun close()
    /** Dollar kursi (so'm). 0 — noma'lum. */
    protected open fun usdRate(): Double = 0.0
    /** Tovar ruleti uchun: nom -> narx matni. */
    protected open fun products(): List<Pair<String, String>> = emptyList()
    /** Fon ishlari nomlari (WorkManager unique work). */
    protected open fun workNames(): List<String> = emptyList()
    /**
     * true — faqat foydalanuvchi sozlamalari va foydali bandlar (2-ilova): texnik (fon ishlari, qurilma, qidiruv
     * laboratoriyasi) va o'yin bandlari ko'rsatilmaydi, ilova bandlari birinchi turadi.
     */
    protected open val userOnly: Boolean = false
    /** Ekran sarlavhasi. */
    protected open fun screenTitle(): String = getString(R.string.lg_sx_title)
    /** Sarlavha ostidagi izoh ([n] — bandlar soni). */
    protected open fun screenHint(n: Int): String = getString(R.string.lg_sx_hint, n)

    private var taps = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(ctx, android.R.attr.colorBackground, Color.BLACK))
            isClickable = true
        }
        val tb = MaterialToolbar(ctx).apply {
            title = screenTitle()
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { close() }
        }
        root.addView(tb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        val scroll = ScrollView(ctx)
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(24)) }
        val items = if (userOnly) appItems() + commonItems() else commonItems() + appItems()
        val head = TextView(ctx).apply {
            text = "🔮"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
            setOnClickListener {
                taps++
                if (taps == 7) { taps = 0; SecretPrefs.markFound(ctx, "crystal"); ConfettiView.burst(requireActivity()); snack(getString(R.string.lg_sx_crystal)) }
            }
        }
        if (!userOnly) list.addView(head)
        list.addView(TextView(ctx).apply {
            text = screenHint(items.size)
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY))
            setPadding(dp(16), dp(4), dp(16), dp(12))
        })
        for (it in items) list.addView(card(it))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun card(i: SecretItem): View {
        val ctx = requireContext()
        val c = MaterialCardView(ctx).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceContainer, Color.DKGRAY))
            isClickable = true
            setOnClickListener { SecretPrefs.markFound(ctx, i.title); i.action() }
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        row.addView(TextView(ctx).apply { text = i.emoji; setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f) }, LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply {
            text = i.title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setTextColor(MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.WHITE))
        })
        col.addView(TextView(ctx).apply {
            text = i.sub
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY))
        })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row)
        c.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        return c
    }

    // ---------------- yordamchilar ----------------

    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    protected fun snack(t: String) { view?.let { Snackbar.make(it, t, Snackbar.LENGTH_LONG).show() } }

    protected fun info(title: String, text: String) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(text)
            .setPositiveButton(R.string.lg_close, null)
            .setNeutralButton(R.string.lg_sx_copy) { _, _ -> copy(text) }.show()
    }

    protected fun copy(text: String) {
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("secret", text))
        snack(getString(R.string.lg_sx_copied))
    }

    protected fun shareText(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null))
    }

    protected fun mask(s: String) = if (s.length <= 6) "•".repeat(s.length) else s.take(3) + "•".repeat(6) + s.takeLast(2) + " (" + s.length + ")"

    private fun s(id: Int, vararg a: Any) = getString(id, *a)

    // ---------------- umumiy bandlar ----------------

    private fun commonItems(): List<SecretItem> {
        val l = ArrayList<SecretItem>()
        if (ledgerHost != null) {
            if (!userOnly) l += SecretItem("🧪", s(R.string.lg_sx_lab), s(R.string.lg_sx_lab_sub)) { searchLab() }
            l += SecretItem("🏆", s(R.string.lg_sx_top), s(R.string.lg_sx_top_sub)) { topProducts() }
            l += SecretItem("📤", s(R.string.lg_sx_csv), s(R.string.lg_sx_csv_sub)) { exportCsv() }
        }
        l += SecretItem("💱", s(R.string.lg_sx_calc), s(R.string.lg_sx_calc_sub)) { calculator() }
        if (!userOnly && products().isNotEmpty()) l += SecretItem("🎰", s(R.string.lg_sx_roulette), s(R.string.lg_sx_roulette_sub)) { roulette() }
        if (!userOnly) l += SecretItem("📜", s(R.string.lg_sx_quote), s(R.string.lg_sx_quote_sub)) { quote() }
        l += SecretItem("🌙", s(R.string.lg_sx_autodark), autoDarkSub()) { autoDark() }
        l += SecretItem("🛡️", s(R.string.lg_sx_secure), s(if (SecretPrefs.secure(requireContext())) R.string.lg_sx_on else R.string.lg_sx_off) + " · " + s(R.string.lg_sx_secure_sub)) { secure() }
        l += SecretItem("🎓", s(R.string.lg_sx_tours), s(R.string.lg_sx_tours_sub)) { tours() }
        l += SecretItem("🔔", s(R.string.lg_sx_notif), s(R.string.lg_sx_notif_sub)) { testNotification() }
        if (!userOnly && workNames().isNotEmpty()) l += SecretItem("⚙️", s(R.string.lg_sx_work), s(R.string.lg_sx_work_sub)) { workStatus() }
        if (!userOnly) l += SecretItem("📱", s(R.string.lg_sx_device), s(R.string.lg_sx_device_sub)) { deviceInfo() }
        l += SecretItem("🧹", s(R.string.lg_sx_cache), s(R.string.lg_sx_cache_sub)) { clearCache() }
        if (!userOnly) l += SecretItem("🎉", s(R.string.lg_sx_party), s(R.string.lg_sx_party_sub, SecretPrefs.launches(requireContext()))) { party() }
        return l
    }

    private fun searchLab() {
        val ctx = requireContext()
        val til = TextInputLayout(ctx).apply { setPadding(dp(20), dp(8), dp(20), 0); hint = getString(R.string.lg_search_hint) }
        val edt = TextInputEditText(til.context).apply { setText("pista 40kg >1mln oktabr -bodom") }
        til.addView(edt)
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.lg_sx_lab).setView(til)
            .setPositiveButton(R.string.lg_sx_analyze) { _, _ ->
                val q = edt.text?.toString().orEmpty()
                val p = LedgerQuery.parse(q, LedgerUi.todayStamp())
                val lines = p.conds.map { describe(it) } + p.excludes.map { "🚫 " + it.raw }
                val hits = ledgerHost?.ledgerData?.value?.entries?.let { entries ->
                    runCatching { LedgerIndex(entries).search(q, LedgerUi.todayStamp(), LedgerFilter(), true).hits.size }.getOrNull()
                }
                info(getString(R.string.lg_sx_lab), "«$q»\n\n" + (if (lines.isEmpty()) "—" else lines.joinToString("\n")) +
                    (hits?.let { "\n\n" + getString(R.string.lg_found_n, it) } ?: ""))
            }
            .setNegativeButton(R.string.lg_cancel, null).show()
    }

    private fun describe(c: LedgerQuery.Cond): String = when (c) {
        is LedgerQuery.DateRange -> "📅 " + Stamp.formatDate(c.from) + " – " + Stamp.formatDate(c.to)
        is LedgerQuery.DayMonth -> "📅 %02d.%02d".format(c.day, c.month)
        is LedgerQuery.Month -> "📅 " + getString(R.string.lg_sx_month, c.month) + (c.year?.let { " $it" } ?: "")
        is LedgerQuery.Year -> "📅 ${c.year}"
        is LedgerQuery.TimeOfDay -> "🕒 %02d:%s".format(c.hour, c.minute?.let { "%02d".format(it) } ?: "**")
        is LedgerQuery.Kind -> "🏷️ " + c.kinds.joinToString(", ") { LedgerUi.kindTitle(requireContext(), it) }
        is LedgerQuery.Currency -> "💵 " + if (c.usd) "$" else getString(R.string.lg_unit_som)
        is LedgerQuery.Total -> "Σ " + range(c.min, c.max) + (if (c.usd == true) " $" else "")
        is LedgerQuery.Qty -> "⚖️ " + range(c.min, c.max)
        is LedgerQuery.Number -> "🔢 " + range(c.min, c.max) + " («${c.text}»)"
        is LedgerQuery.Text -> "🔤 " + c.raw
        is LedgerQuery.Either -> describe(c.a) + "  " + getString(R.string.lg_sx_or) + "  " + describe(c.b)
        is LedgerQuery.AnyOf -> c.list.joinToString("  " + getString(R.string.lg_sx_or) + "  ") { describe(it) }
        else -> c.javaClass.simpleName
    }

    private fun range(a: Double, b: Double) = when {
        a == b -> NumFmt.qty(a)
        b >= Double.MAX_VALUE / 2 || b.isInfinite() -> "≥ " + NumFmt.qty(a)
        a <= 0.0 -> "≤ " + NumFmt.qty(b)
        else -> NumFmt.qty(a) + " – " + NumFmt.qty(b)
    }

    private fun topProducts() {
        val d = ledgerHost?.ledgerData?.value ?: return
        val sales = d.entries.filter { it.kind == EntryKind.SALE && it.product.isNotBlank() }
        if (sales.isEmpty()) { snack(getString(R.string.lg_sx_no_ledger)); return }
        val top = sales.groupBy { it.product.trim() }.map { (n, l) -> Triple(n, l.sumOf { it.qty }, l.size) }.sortedByDescending { it.second }.take(10)
        val text = top.mapIndexed { i, t -> "${i + 1}. ${t.first} — ${NumFmt.qty(t.second)} (${t.third}×)" }.joinToString("\n")
        info(getString(R.string.lg_sx_top) + (if (d.clientName.isNotBlank()) " · " + d.clientName else ""), text)
    }

    private fun exportCsv() {
        val d = ledgerHost?.ledgerData?.value ?: return
        if (d.entries.isEmpty()) { snack(getString(R.string.lg_sx_no_ledger)); return }
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                val file = File(dir, "1C_tarixi_" + d.clientName.filter { it.isLetterOrDigit() }.take(30) + ".csv")
                fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
                fun m(c: Col, e: uz.imsoft.shared.report.LedgerEntry) = e.v(c).let { if (it.usd != 0.0) it.usd.toString() + "$" else if (it.som != 0.0) "%.0f".format(it.som) else "" }
                file.bufferedWriter(Charsets.UTF_8).use { w ->
                    w.write("\uFEFF")
                    w.write("Sana;Turi;Tovar;Miqdor;Narx;Summa;To'lov;Qaytarish;Qoldiq\n")
                    for (e in d.entries.sortedBy { it.stamp }) {
                        w.write(listOf(Stamp.formatDateTime(e.stamp), q(uz.imsoft.shared.report.LedgerOwner.plain(e.kindText)), q(e.product), NumFmt.qty(e.qty), m(Col.PRICE, e), m(Col.SUM, e), m(Col.PAYMENT, e), m(Col.RETURN, e), m(Col.BALANCE, e)).joinToString(";"))
                        w.write("\n")
                    }
                }
                file
            }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, null))
        }
    }

    private fun calculator() {
        val ctx = requireContext()
        val rate0 = usdRate()
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun field(h: String, v: String) = TextInputLayout(ctx).let { til ->
            til.hint = h
            val e = TextInputEditText(til.context).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(v) }
            til.addView(e); box.addView(til); e
        }
        val eRate = field(getString(R.string.lg_sx_rate), if (rate0 > 0) NumFmt.qty(rate0).replace(" ", "").replace("\u00A0", "") else "")
        val eUsd = field("$", "100")
        val out = TextView(ctx).apply { setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium); setPadding(0, dp(12), 0, 0) }
        box.addView(out)
        fun num(e: TextInputEditText) = e.text?.toString().orEmpty().replace(" ", "").replace("\u00A0", "").replace(',', '.').toDoubleOrNull() ?: 0.0
        val upd = {
            val r = num(eRate); val u = num(eUsd)
            out.text = if (r > 0) "= " + NumFmt.qty(Math.round(u * r).toDouble()) + " " + getString(R.string.lg_unit_som) + "\n1 000 000 " + getString(R.string.lg_unit_som) + " = " + "%.2f".format(1_000_000 / r) + " $" else "—"
        }
        val w = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = upd()
        }
        eRate.addTextChangedListener(w); eUsd.addTextChangedListener(w); upd()
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.lg_sx_calc).setView(box).setPositiveButton(R.string.lg_close, null).show()
    }

    private fun roulette() {
        val list = products()
        val p = list[Random.nextInt(list.size)]
        val text = getString(R.string.lg_sx_roulette_text, p.first, p.second)
        MaterialAlertDialogBuilder(requireContext()).setTitle("🎰 " + getString(R.string.lg_sx_roulette)).setMessage(text)
            .setPositiveButton(R.string.lg_sx_again) { _, _ -> roulette() }
            .setNeutralButton(R.string.lg_sx_share) { _, _ -> shareText(text) }
            .setNegativeButton(R.string.lg_close, null).show()
    }

    private fun quote() {
        val all = getString(R.string.lg_sx_quotes).split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val q = all[day % all.size]
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lg_sx_quote).setMessage("«$q»")
            .setPositiveButton(R.string.lg_close, null)
            .setNeutralButton(R.string.lg_sx_another) { _, _ ->
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lg_sx_quote).setMessage("«" + all[Random.nextInt(all.size)] + "»").setPositiveButton(R.string.lg_close, null).show()
            }.show()
    }

    private fun autoDarkSub(): String {
        val c = requireContext()
        return if (SecretPrefs.autoDark(c)) getString(R.string.lg_sx_autodark_on, SecretPrefs.darkFrom(c), SecretPrefs.darkTo(c)) else getString(R.string.lg_sx_autodark_sub)
    }

    private fun autoDark() {
        val c = requireContext()
        val opts = arrayOf(getString(R.string.lg_sx_off), "19:00 – 07:00", "20:00 – 06:00", "18:00 – 08:00", "22:00 – 06:00")
        val hours = arrayOf(0 to 0, 19 to 7, 20 to 6, 18 to 8, 22 to 6)
        MaterialAlertDialogBuilder(c).setTitle(R.string.lg_sx_autodark).setItems(opts) { _, i ->
            if (i == 0) {
                SecretPrefs.setAutoDark(c, false)
                snack(getString(R.string.lg_sx_autodark_off))
            } else {
                SecretPrefs.setDarkHours(c, hours[i].first, hours[i].second)
                SecretPrefs.setAutoDark(c, true)
                SecretPrefs.apply(requireActivity())
            }
        }.show()
    }

    private fun secure() {
        val c = requireContext()
        val v = !SecretPrefs.secure(c)
        SecretPrefs.setSecure(c, v)
        SecretPrefs.apply(requireActivity())
        snack(getString(if (v) R.string.lg_sx_secure_on else R.string.lg_sx_secure_off))
    }

    private fun tours() {
        val c = requireContext()
        val opts = arrayOf(getString(R.string.lg_sx_tours_again), getString(if (CoachMarks.isEnabled(c)) R.string.lg_sx_tours_disable else R.string.lg_sx_tours_enable))
        MaterialAlertDialogBuilder(c).setTitle(R.string.lg_sx_tours).setItems(opts) { _, i ->
            if (i == 0) { CoachMarks.reset(c); CoachMarks.setEnabled(c, true); snack(getString(R.string.lg_sx_tours_reset)) }
            else { CoachMarks.setEnabled(c, !CoachMarks.isEnabled(c)); snack(getString(if (CoachMarks.isEnabled(c)) R.string.lg_sx_on else R.string.lg_sx_off)) }
        }.show()
    }

    private fun testNotification() {
        val c = requireContext()
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) { snack(getString(R.string.lg_sx_notif_off)); return }
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, getString(R.string.lg_sx_notif), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = NotificationCompat.Builder(c, CH).setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("🔮 " + getString(R.string.lg_sx_title)).setContentText(getString(R.string.lg_sx_notif_text)).setAutoCancel(true).build()
        try {
            nm.notify(7707, n)
            snack(getString(R.string.lg_sx_notif_sent))
        } catch (e: SecurityException) {
            snack(getString(R.string.lg_sx_notif_off))
        }
    }

    private fun workStatus() {
        val c = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val wm = WorkManager.getInstance(c)
            val lines = workNames().map { name ->
                val infos = withTimeoutOrNull(3000) { runCatching { wm.getWorkInfosForUniqueWorkFlow(name).first() }.getOrNull() }.orEmpty()
                name + ": " + (if (infos.isEmpty()) getString(R.string.lg_sx_work_none) else infos.joinToString(", ") { it.state.name })
            }
            info(getString(R.string.lg_sx_work), lines.joinToString("\n"))
        }
    }

    private fun deviceInfo() {
        val c = requireContext()
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val dm = resources.displayMetrics
        val ver = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty()
        val days = SecretPrefs.firstLaunch(c).takeIf { it > 0 }?.let { (System.currentTimeMillis() - it) / 86_400_000L } ?: 0
        val text = listOf(
            getString(R.string.lg_sx_d_app, ver, c.packageName),
            getString(R.string.lg_sx_d_phone, Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            getString(R.string.lg_sx_d_ram, mi.availMem / 1_048_576, mi.totalMem / 1_048_576),
            getString(R.string.lg_sx_d_screen, dm.widthPixels, dm.heightPixels, dm.densityDpi, resources.configuration.fontScale),
            getString(R.string.lg_sx_d_battery, getString(if (pm.isIgnoringBatteryOptimizations(c.packageName)) R.string.lg_sx_d_bat_free else R.string.lg_sx_d_bat_opt)),
            getString(R.string.lg_sx_d_storage, dirSize(c.filesDir) / 1024, dirSize(c.cacheDir) / 1024),
            getString(R.string.lg_sx_d_usage, SecretPrefs.launches(c), days),
        ).joinToString("\n")
        info(getString(R.string.lg_sx_device), text)
    }

    private fun dirSize(f: File?): Long = if (f == null || !f.exists()) 0L else if (f.isFile) f.length() else f.listFiles().orEmpty().sumOf { dirSize(it) }

    private fun clearCache() {
        val c = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val freed = withContext(Dispatchers.IO) {
                val before = dirSize(c.cacheDir)
                c.cacheDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
                before - dirSize(c.cacheDir)
            }
            snack(getString(R.string.lg_sx_cache_done, freed / 1024))
        }
    }

    private fun party() {
        ConfettiView.burst(requireActivity())
        val c = requireContext()
        snack(getString(R.string.lg_sx_found, SecretPrefs.found(c).size))
    }

    private companion object { const val CH = "secret_test" }
}
