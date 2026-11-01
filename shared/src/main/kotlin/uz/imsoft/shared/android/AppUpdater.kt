package uz.imsoft.shared.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.imsoft.shared.JObj
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * v4.0: ilova o'zi yangilanadi. Build tugagach APK'lar va version.json Cloudflare'dagi yashirin papkaga qo'yiladi
 * ([uz.imsoft.shared.DeviceCodes.updateDir]). Ilova ochilganda (3 soatda ko'pi bilan bir) tekshiradi: yangisi bo'lsa —
 * "O'rnatish" oynasi; bosilsa yuklab, Android o'rnatish oynasini ochadi. Imzo bir xil bo'lgani uchun ma'lumot saqlanadi.
 *
 * version.json: {"agent": {"vc": 25, "v": "4.0", "apk": "agent.apk", "size": 123}, "admin": {...}, "mijoz": {...}}
 */
class AppUpdater(private val context: Context, private val baseUrl: String, private val key: String, private val versionCode: Long) {
    data class Info(val versionCode: Long, val name: String, val apk: String, val size: Long)

    private val prefs = context.getSharedPreferences("app_updater", Context.MODE_PRIVATE)
    val enabled: Boolean get() = baseUrl.startsWith("https://")

    private fun url(name: String) = baseUrl.trimEnd('/') + "/" + name

    /** Yangi versiya bo'lsa — uning ma'lumoti. [force] bo'lmasa 3 soatda ko'pi bilan bir marta tarmoqqa chiqadi. */
    suspend fun check(force: Boolean = false): Info? {
        if (!enabled) return null
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("checked", 0L) < 3 * 3_600_000L) return cachedNewer()
        val text = withContext(Dispatchers.IO) {
            val c = URL(url("version.json") + "?t=" + now / 60_000).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.useCaches = false
            try {
                if (c.responseCode != 200) return@withContext null
                c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            } finally {
                c.disconnect()
            }
        } ?: return null
        prefs.edit().putLong("checked", now).putString("json", text).apply()
        return parse(text)
    }

    private fun cachedNewer(): Info? = prefs.getString("json", null)?.let { parse(it) }

    @Suppress("UNCHECKED_CAST")
    private fun parse(text: String): Info? = runCatching {
        val o = JObj.parse(text).obj(key) ?: return null
        val info = Info(o.long("vc"), o.str("v"), o.str("apk"), o.long("size"))
        info.takeIf { it.versionCode > versionCode && it.apk.isNotEmpty() }
    }.getOrNull()

    /** "Keyinroq" bosilgan versiya — bugun qayta so'ralmaydi. */
    fun snoozed(info: Info): Boolean = prefs.getLong("snooze_vc", 0L) == info.versionCode && System.currentTimeMillis() - prefs.getLong("snooze_at", 0L) < 12 * 3_600_000L
    fun snooze(info: Info) { prefs.edit().putLong("snooze_vc", info.versionCode).putLong("snooze_at", System.currentTimeMillis()).apply() }

    suspend fun download(info: Info, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "update-$key.apk")
        val tmp = File(dir, "update-$key.apk.part")
        val c = URL(url(info.apk) + "?v=" + info.versionCode).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            var got = 0L
            var last = -1
            c.inputStream.use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        got += n
                        val p = if (total > 0) (got * 100 / total).toInt() else 0
                        if (p != last) { last = p; withContext(Dispatchers.Main) { progress(p) } }
                    }
                }
            }
            check(total <= 0 || got == total) { "short" }
            if (out.exists()) out.delete()
            check(tmp.renameTo(out)) { "rename" }
            out
        } finally {
            c.disconnect()
            tmp.delete()
        }
    }

    /** Android o'rnatish oynasini ochadi. Birinchi marta "Noma'lum ilovalar" ruxsati so'raladi. @return ochildimi. */
    fun install(activity: Activity, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
            return false
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".files", file)
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }

    /**
     * Oynani ko'rsatadi: "Yangi versiya X" — O'rnatish / Keyinroq. Matnlar chaqiruvchidan (strings.xml).
     * [texts]: sarlavha (%1$s — versiya), matn, o'rnatish, keyinroq, yuklanmoqda, xato (%1$s), ruxsat haqida.
     */
    fun prompt(activity: Activity, info: Info, texts: Texts, launch: (suspend () -> Unit) -> Unit) {
        val d = MaterialAlertDialogBuilder(activity).setTitle(texts.title.format(info.name)).setMessage(texts.body)
            .setPositiveButton(texts.install, null).setNegativeButton(texts.later) { _, _ -> snooze(info) }.setCancelable(false).show()
        var ready: File? = null
        var tvState: TextView? = null
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            // Yuklab bo'lingan (ruxsat berib qaytgan) — qayta yuklamaydi
            ready?.let { f -> if (!install(activity, f)) tvState?.text = texts.permission; return@setOnClickListener }
            val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; val p = Ui.dp(activity, 24); setPadding(p, p / 2, p, 0) }
            val tv = TextView(activity).apply { text = texts.loading }
            val bar = LinearProgressIndicator(activity).apply { max = 100; isIndeterminate = false }
            tvState = tv
            col.addView(tv)
            col.addView(bar)
            d.setView(col)
            d.setMessage(null)
            d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            launch {
                try {
                    val f = download(info) { p -> bar.setProgressCompat(p, true); tv.text = texts.loading + " " + p + "%" }
                    ready = f
                    if (!install(activity, f)) tv.text = texts.permission
                    d.getButton(AlertDialog.BUTTON_POSITIVE).apply { isEnabled = true; setText(texts.install) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    tv.text = texts.error.format(e.message ?: e.javaClass.simpleName)
                    d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }
        }
    }

    class Texts(val title: String, val body: String, val install: String, val later: String, val loading: String, val error: String, val permission: String)
}
