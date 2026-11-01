package uz.imsoft.shared.android

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.imsoft.shared.CloudDb
import uz.imsoft.shared.DeviceAssignment
import uz.imsoft.shared.DeviceClient
import uz.imsoft.shared.DeviceCodes
import uz.imsoft.shared.Json
import java.io.File

/**
 * v4.0: shu telefonning qurilma sifatidagi holati. Id telefonda bir marta yaratiladi (ilova yangilansa saqlanadi).
 * Admin'ning oxirgi biriktirishi telefonda saqlanadi — internet bo'lmasa ham ilova ishlayveradi.
 */
class DeviceSession(private val context: Context, val dev: DeviceCodes.Dev, private val role: String, private val version: String, private val versionCode: Long) {
    private val prefs = context.getSharedPreferences("device_session", Context.MODE_PRIVATE)

    val id: String
        get() = prefs.getString("id", null)?.takeIf { CloudDb.isKey(it) } ?: CloudDb.newKey().also { prefs.edit().putString("id", it).apply() }

    private val client get() = DeviceClient(dev, id)

    /** Oxirgi ma'lum biriktirish (internetsiz ham). */
    fun cached(): DeviceAssignment? = prefs.getString("x", null)?.let { runCatching { DeviceAssignment.parse(Json.parse(it)) }.getOrNull() }

    /** Ro'yxatdan o'tadi (har chaqiruvda "oxirgi ko'rilgan" yangilanadi) va biriktirishni o'qiydi. Internet yo'q — xato otiladi. */
    suspend fun sync(): DeviceAssignment? = withContext(Dispatchers.IO) {
        val c = client
        c.register(Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL, role, version, versionCode, Build.VERSION.SDK_INT)
        val a = c.assignment()
        if (a == null) prefs.edit().remove("x").apply()
        else prefs.edit().putString("x", Json.write(linkedMapOf("s" to a.status, "a" to a.agent, "l" to a.link, "n" to a.label, "at" to a.at))).apply()
        sendCrash(c)
        a
    }

    private fun sendCrash(c: DeviceClient) {
        val f = CrashLog.file(context)
        if (!f.isFile) return
        runCatching { c.reportCrash(f.readText()) }.onSuccess { f.delete() }
    }
}

/** v4.0: ilova yiqilsa — xato matni faylga; keyingi ochilishda Admin'ga yuboriladi (skrinshot shart emas). */
object CrashLog {
    fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context, appLabel: String, version: String) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val head = "$appLabel $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL} · ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US).format(java.util.Date())}\n"
                file(app).writeText(head + sw.toString().take(7000))
            }
            prev?.uncaughtException(t, e)
        }
    }
}
