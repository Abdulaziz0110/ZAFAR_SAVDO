package uz.imsoft.mijoz.ui

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.ActivityMainBinding
import uz.imsoft.mijoz.printer.PrintService
import uz.imsoft.mijoz.printer.PrinterHub
import uz.imsoft.mijoz.ui.home.HomeFragment
import uz.imsoft.mijoz.ui.prices.PricesFragment
import uz.imsoft.mijoz.ui.printer.PrinterFragment
import uz.imsoft.mijoz.ui.report.ReportFragment
import uz.imsoft.mijoz.ui.settings.SettingsFragment

/**
 * 2-ilova: Bosh sahifa · Savdo (v3.9) · Narxlar · Tarix · Sozlamalar. Pastki menyuda 5 tadan ko'p bo'lmaydi: chek printeri
 * bo'limi yoqilgan telefonda "Sozlamalar" o'rnida "Printer" turadi (Sozlamalar — bosh sahifadagi tishli g'ildirakdan).
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val tabs = listOf(R.id.nav_home, R.id.nav_sale, R.id.nav_prices, R.id.nav_report, R.id.nav_printer, R.id.nav_settings)

    /**
     * Shrift/o'lcham uchun o'ralgan kontekst PrintManager'ga Activity'ni ko'rsatmaydi ("Can print only from an activity").
     * Chop etish xizmati asl kontekstdan olinadi (uning "tashqi" konteksti — shu Activity).
     */
    private var originalBase: android.content.Context? = null

    override fun getSystemService(name: String): Any? =
        if (name == android.content.Context.PRINT_SERVICE) originalBase?.getSystemService(name) ?: super.getSystemService(name)
        else super.getSystemService(name)

    override fun attachBaseContext(newBase: Context) {
        originalBase = newBase
        super.attachBaseContext(runCatching { wrapScale(newBase, Locator.prefs(newBase)) }.getOrDefault(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // v4.0: katta shriftda tugma yozuvlari sig'sin (barcha ekranlarda)
        uz.imsoft.shared.android.FitText.install(this)
        binding.bottomNav.setOnItemSelectedListener { showTab(it.itemId); true }
        // Orqaga: avval bosh sahifaga, keyin 2 soniya ichida ikki marta bosilsa chiqadi.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) { supportFragmentManager.popBackStack(); return }
                if (loginShown()) {
                    val now0 = System.currentTimeMillis()
                    if (now0 - lastBackAt < 2000L) finish()
                    else { lastBackAt = now0; android.widget.Toast.makeText(this@MainActivity, R.string.back_again_to_exit, android.widget.Toast.LENGTH_SHORT).show() }
                    return
                }
                if (binding.bottomNav.selectedItemId != R.id.nav_home && Locator.prefs.link != null) {
                    binding.bottomNav.selectedItemId = R.id.nav_home
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) finish()
                else {
                    lastBackAt = now
                    android.widget.Toast.makeText(this@MainActivity, R.string.back_again_to_exit, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        })
        updatePrinterTab()
        if (savedInstanceState == null) {
            val start = when (intent.getStringExtra(EXTRA_TAB)) {
                TAB_PRINTER -> if (Locator.prefs.printerSection) R.id.nav_printer else R.id.nav_home
                else -> if (Locator.prefs.link == null) (if (Locator.prefs.printerSection) R.id.nav_home else R.id.nav_settings) else when (Locator.prefs.startTab) {
                    "prices" -> R.id.nav_prices
                    "report" -> R.id.nav_report
                    else -> R.id.nav_home
                }
            }
            binding.bottomNav.selectedItemId = start
            showTab(start)
        }
        handleUsb(intent)
        if (savedInstanceState == null) lifecycleScope.launch { kotlinx.coroutines.delay(2_500); checkUpdate(false) }
        if (savedInstanceState == null) openFromWidget(intent)
        if (savedInstanceState == null) uz.imsoft.mijoz.ui.ledger.SecretPrefs.countLaunch(this)
        if (Locator.deviceMode) { if (Locator.prefs.link == null) showLogin() }
        else if (Locator.prefs.link == null && !Locator.prefs.skipLogin && intent.getStringExtra(EXTRA_TAB) != TAB_PRINTER) showLogin()
        // Kirish bekor qilinsa (1-ilovada telefon o'chirildi) — kirish oynasi.
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                Locator.data.state.collect { if (it.revoked && !loginShown()) showLogin() }
            }
        }
        // Printer servisi yoqilgan bo'lsa (masalan ilova yangilangandan keyin) — qayta ishga tushiriladi.
        if (Locator.prefs.printerSection && Locator.prefs.printerOn && !PrinterHub.state.value.running) {
            runCatching { PrintService.start(this) }
        }
        // v3.2: ish vaqtida har N daqiqada yangilash (ilova ochiq turganda) va kunlik zaxira
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                runCatching { uz.imsoft.mijoz.data.MijozAuto.backupIfDue(applicationContext) }
                var tick = 0
                while (true) {
                    // v4.0: Admin biriktirishi/bloklashi — har daqiqada (internet yo'q bo'lsa oxirgi holat bilan ishlayveradi)
                    if (tick++ % 2 == 0) checkDevice()
                    kotlinx.coroutines.delay(30_000L)
                    val pr = Locator.prefs
                    if (pr.link != null && System.currentTimeMillis() - pr.fetchedAt >= uz.imsoft.mijoz.data.MijozAuto.liveIntervalMs()) {
                        runCatching { Locator.data.refresh(askFresh = false) }
                        uz.imsoft.mijoz.ui.extra.MijozWidget.refresh(applicationContext)
                    }
                }
            }
        }
    }

    /** v4.0: ilova yangilanishi. [force] — Sozlamalardan "Tekshirish" bosilganda. */
    fun checkUpdate(force: Boolean) {
        val u = Locator.updater
        if (!u.enabled) { if (force) android.widget.Toast.makeText(this, R.string.up_off, android.widget.Toast.LENGTH_LONG).show(); return }
        lifecycleScope.launch {
            val info = runCatching { u.check(force) }.getOrNull()
            if (info == null) { if (force) android.widget.Toast.makeText(this@MainActivity, R.string.up_none, android.widget.Toast.LENGTH_SHORT).show(); return@launch }
            if (!force && u.snoozed(info)) return@launch
            val t = uz.imsoft.shared.android.AppUpdater.Texts(
                getString(R.string.up_title), getString(R.string.up_body), getString(R.string.up_install), getString(R.string.up_later),
                getString(R.string.up_loading), getString(R.string.up_error), getString(R.string.up_permission),
            )
            u.prompt(this@MainActivity, info, t) { block -> lifecycleScope.launch { block() } }
        }
    }

    private suspend fun checkDevice() {
        val d = Locator.device ?: return
        val before = d.cached()
        val a = runCatching { d.sync() }.getOrElse { return }
        if (a == null && before == null && Locator.prefs.link != null) return // eski usulda ulangan, hali biriktirilmagan
        val ok = Locator.applyDevice(a)
        if (!ok && !loginShown()) showLogin()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsb(intent)
        if (intent.getStringExtra(EXTRA_TAB) == TAB_PRINTER && Locator.prefs.printerSection) binding.bottomNav.selectedItemId = R.id.nav_printer
        openFromWidget(intent)
    }

    override fun onResume() {
        super.onResume()
        uz.imsoft.mijoz.ui.ledger.SecretPrefs.apply(this)
        applyKeepScreenOn()
    }

    /** Qo'shimcha sozlamalar ▸ "Ekran o'chmasin". */
    fun applyKeepScreenOn() {
        if (Locator.prefs.keepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        uz.imsoft.mijoz.ui.extra.MijozWidget.refresh(applicationContext)
        val p = Locator.prefs
        // v3.1: ochilganda — eng so'nggi ma'lumot va Admin'dan qarzni serverdan yangilash so'raladi; javob ~1 daqiqada keladi
        if (p.autoRefresh && p.link != null && System.currentTimeMillis() - p.fetchedAt > 2 * 60_000L) {
            lifecycleScope.launch {
                runCatching { Locator.data.refresh(askFresh = true) }
                kotlinx.coroutines.delay(45_000L)
                runCatching { Locator.data.refresh(askFresh = false) }
            }
        }
    }

    /** v3.2 vidjet: Narxlar yoki Kassa. */
    private fun openFromWidget(i: Intent?) {
        when (i?.getStringExtra(EXTRA_TAB)) {
            TAB_PRICES -> if (Locator.prefs.link != null) binding.bottomNav.selectedItemId = R.id.nav_prices
            TAB_POS -> push(uz.imsoft.mijoz.ui.extra.PosFragment())
            else -> return
        }
        i.removeExtra(EXTRA_TAB)
    }

    /** Printer ulanganda ilova ochilsa — servis darhol tekshiradi. */
    private fun handleUsb(i: Intent?) {
        if (i?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) PrinterHub.nudge()
    }

    /**
     * v1.14: BottomNavigationView 5 tadan ortiq bandni qabul qilmaydi (6 ta bo'lsa ilova ochilmay yiqiladi). Shuning uchun
     * 5-o'rin almashtiriladi: printer bo'limi yoqilgan bo'lsa "Printer", aks holda "Sozlamalar".
     */
    fun updatePrinterTab() {
        val printer = Locator.prefs.printerSection
        val menu = binding.bottomNav.menu
        val want = if (printer) R.id.nav_printer else R.id.nav_settings
        if (menu.findItem(want) != null) return
        val drop = if (printer) R.id.nav_settings else R.id.nav_printer
        val wasSelected = binding.bottomNav.selectedItemId == drop
        menu.removeItem(drop)
        menu.add(android.view.Menu.NONE, want, android.view.Menu.NONE, if (printer) R.string.nav_printer else R.string.nav_settings)
            .setIcon(if (printer) R.drawable.ic_print else R.drawable.ic_settings)
        if (wasSelected) binding.bottomNav.selectedItemId = R.id.nav_home
    }

    /** v3.9: Sozlamalar — pastki menyuda bo'lsa o'sha, bo'lmasa (printer bo'limi yoqilgan) ustidan ochiladi. */
    fun openSettings() {
        if (binding.bottomNav.menu.findItem(R.id.nav_settings) != null) openTab(R.id.nav_settings) else push(SettingsFragment())
    }

    fun openTab(id: Int) { binding.bottomNav.selectedItemId = id }

    /** Pastki menyu ustidan ochiladigan ekran (masalan sirli menyu). "Orqaga" — yopadi. */
    fun push(f: Fragment) {
        supportFragmentManager.beginTransaction().setReorderingAllowed(true).add(R.id.container, f).addToBackStack(null).commit()
    }

    private fun loginShown() = supportFragmentManager.findFragmentByTag(TAG_LOGIN) != null

    /** Kirish oynasi (telefon + parol). Pastki menyu yashiriladi. */
    fun showLogin() {
        if (loginShown()) return
        binding.bottomNav.visibility = android.view.View.GONE
        supportFragmentManager.beginTransaction().setReorderingAllowed(true)
            .add(R.id.container, if (Locator.deviceMode) uz.imsoft.mijoz.ui.login.DeviceWaitFragment() else uz.imsoft.mijoz.ui.login.LoginFragment(), TAG_LOGIN).commitAllowingStateLoss()
    }

    fun closeLogin() {
        supportFragmentManager.findFragmentByTag(TAG_LOGIN)?.let { supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss() }
        binding.bottomNav.visibility = android.view.View.VISIBLE
        binding.bottomNav.selectedItemId = R.id.nav_home
        (supportFragmentManager.findFragmentByTag("tab_" + R.id.nav_home) as? HomeFragment)?.let { uz.imsoft.mijoz.ui.tour.Tour.show(it, uz.imsoft.mijoz.ui.tour.Tour.HOME) }
    }

    private var lastBackAt = 0L

    private fun create(id: Int): Fragment = when (id) {
        R.id.nav_sale -> uz.imsoft.mijoz.ui.sale.MSaleFragment()
        R.id.nav_prices -> PricesFragment()
        R.id.nav_report -> ReportFragment()
        R.id.nav_printer -> PrinterFragment()
        R.id.nav_settings -> SettingsFragment()
        else -> HomeFragment()
    }

    private fun showTab(id: Int) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        var target = fm.findFragmentByTag("tab_$id")
        if (target == null) {
            target = create(id)
            tx.add(R.id.container, target, "tab_$id")
        }
        for (other in tabs) if (other != id) fm.findFragmentByTag("tab_$other")?.let { tx.hide(it) }
        tx.show(target).commitNowAllowingStateLoss()
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_PRICES = "prices"
        const val TAB_POS = "pos"
        const val TAB_PRINTER = "printer"
        private const val TAG_LOGIN = "login"
    }
}
