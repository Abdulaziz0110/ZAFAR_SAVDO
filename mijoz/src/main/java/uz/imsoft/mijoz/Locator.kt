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
        Locator.init(this)
    }
}
