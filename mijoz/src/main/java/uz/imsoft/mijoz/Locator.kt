package uz.imsoft.mijoz

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.data.MijozPrefs
import uz.imsoft.mijoz.data.SnapshotRepository

/** Oddiy qo'lda bog'lash (2-ilova kichik — Hilt shart emas). */
object Locator {
    private lateinit var app: Application
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs: MijozPrefs by lazy { MijozPrefs(app) }
    val login: uz.imsoft.mijoz.data.LoginRepository by lazy { uz.imsoft.mijoz.data.LoginRepository(app, prefs) }
    val cart: uz.imsoft.mijoz.data.OrderCart by lazy { uz.imsoft.mijoz.data.OrderCart(app) }
    val data: SnapshotRepository by lazy { SnapshotRepository(app, prefs) { force -> login.relogin(force) } }
    /** v3.2: o'z kassasi (faqat telefonda). */
    val pos: uz.imsoft.mijoz.data.PosDb by lazy { uz.imsoft.mijoz.data.PosDb(app) }
    /** v3.2: Admin'ga to'lov va chek tuzatish so'rovlari. */
    val requests: uz.imsoft.mijoz.data.ClientRequests by lazy { uz.imsoft.mijoz.data.ClientRequests(prefs) }
    /** v3.9: o'z savdosi savatchasi (telefonda saqlanadi). */
    val sale: uz.imsoft.mijoz.data.MCart by lazy { uz.imsoft.mijoz.data.MCart(app) }

    /** v4.0: build'ga qo'yilgan qurilmalar kodi (kalitsiz ishlash). Yo'q bo'lsa — eskicha (do'kon kodi + telefon + parol). */
    val devCode: uz.imsoft.shared.DeviceCodes.Dev? by lazy { uz.imsoft.shared.DeviceCodes.decodeDev(BuildConfig.DEV_CODE) }
    val deviceMode: Boolean get() = devCode != null
    /** v4.0: ilova o'zi yangilanadi. */
    val updater by lazy { uz.imsoft.shared.android.AppUpdater(app, BuildConfig.UPDATE_URL, "mijoz", BuildConfig.VERSION_CODE.toLong()) }
    val device: uz.imsoft.shared.android.DeviceSession? by lazy {
        devCode?.let { uz.imsoft.shared.android.DeviceSession(app, it, uz.imsoft.shared.DeviceCodes.ROLE_MIJOZ, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()) }
    }

    /**
     * v4.0: Admin bergan biriktirishni qo'llaydi. @return true — ulangan (ma'lumot ko'rsatiladi), false — kutish/bloklangan.
     * Ulanish o'zgarsa eski ma'lumot o'chiriladi va yangisi darhol olinadi.
     */
    suspend fun applyDevice(a: uz.imsoft.shared.DeviceAssignment?): Boolean {
        val code = a?.takeIf { !it.blocked }?.link?.let { uz.imsoft.shared.LinkCode.decode(it) }
        if (code == null) {
            if (prefs.link != null) data.unlink()
            return false
        }
        if (prefs.link != code) {
            prefs.setLink(code)
            // v4.0: filial printeri — chek navbati bor bo'lsa printer bo'limi o'zi yoqiladi (printer ulanishi bilan cheklar chiqadi)
            if (code.queueKey.isNotEmpty() && !prefs.devPrinterSet) {
                prefs.printerSection = true
                prefs.printerOn = true
                prefs.autostart = true
                prefs.devPrinterSet = true
            }
            data.onLinked()
            uz.imsoft.mijoz.data.RefreshWorker.schedule(app, prefs.autoMinutes)
            scope.launch { runCatching { data.refresh(askFresh = true) } }
        }
        prefs.memberName = a?.label.orEmpty()
        return true
    }

    fun init(a: Application) {
        if (::app.isInitialized) return
        app = a
        applyUi()
        Thread({ uz.imsoft.shared.android.PrintFonts.init(a) }, "print-fonts").start()
        scope.launch { runCatching { data.loadLocal() } }
        // Fonda avtomatik yangilash (Sozlamalar ▸ Ma'lumot) + v3.2 ish vaqti budilnigi
        uz.imsoft.mijoz.data.MijozAuto.apply(a)
        scope.launch { runCatching { pos.load() } }
    }

    /** BroadcastReceiver uchun (jarayon endi boshlangan bo'lsa ham). */
    fun prefs(ctx: Context): MijozPrefs {
        if (!::app.isInitialized) init(ctx.applicationContext as Application)
        return prefs
    }

    fun applyUi() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (prefs.language == "cyr") "uz-Cyrl" else "uz-Latn"))
        AppCompatDelegate.setDefaultNightMode(
            when (prefs.theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // v4.0: yiqilsa — xato matni Admin'ga ("Qurilmalar") keyingi ochilishda o'zi boradi
        uz.imsoft.shared.android.CrashLog.install(this, "Mijoz", BuildConfig.VERSION_NAME)
        Locator.init(this)
    }
}
