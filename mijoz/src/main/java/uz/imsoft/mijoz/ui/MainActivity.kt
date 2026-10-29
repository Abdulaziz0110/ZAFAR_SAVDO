package uz.imsoft.mijoz.ui

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

/** 2-ilova: Bosh sahifa · Narxlar · Hisobot · Printer (ixtiyoriy) · Sozlamalar. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val tabs = listOf(R.id.nav_home, R.id.nav_prices, R.id.nav_report, R.id.nav_printer, R.id.nav_settings)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(runCatching { wrapScale(newBase, Locator.prefs(newBase)) }.getOrDefault(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNav.setOnItemSelectedListener { showTab(it.itemId); true }
        updatePrinterTab()
        if (savedInstanceState == null) {
            val start = when (intent.getStringExtra(EXTRA_TAB)) {
                TAB_PRINTER -> if (Locator.prefs.printerSection) R.id.nav_printer else R.id.nav_home
                else -> if (Locator.prefs.link == null) R.id.nav_settings else R.id.nav_home
            }
            binding.bottomNav.selectedItemId = start
            showTab(start)
        }
        handleUsb(intent)
        // Printer servisi yoqilgan bo'lsa (masalan ilova yangilangandan keyin) — qayta ishga tushiriladi.
        if (Locator.prefs.printerSection && Locator.prefs.printerOn && !PrinterHub.state.value.running) {
            runCatching { PrintService.start(this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsb(intent)
        if (intent.getStringExtra(EXTRA_TAB) == TAB_PRINTER && Locator.prefs.printerSection) binding.bottomNav.selectedItemId = R.id.nav_printer
    }

    override fun onStart() {
        super.onStart()
        val p = Locator.prefs
        if (p.autoRefresh && p.link != null && System.currentTimeMillis() - p.fetchedAt > 5 * 60_000L) {
            lifecycleScope.launch { runCatching { Locator.data.refresh(askFresh = false) } }
        }
    }

    /** Printer ulanganda ilova ochilsa — servis darhol tekshiradi. */
    private fun handleUsb(i: Intent?) {
        if (i?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) PrinterHub.nudge()
    }

    fun updatePrinterTab() {
        binding.bottomNav.menu.findItem(R.id.nav_printer).isVisible = Locator.prefs.printerSection
    }

    fun openTab(id: Int) { binding.bottomNav.selectedItemId = id }

    private fun create(id: Int): Fragment = when (id) {
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
        const val TAB_PRINTER = "printer"
    }
}
