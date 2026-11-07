package com.ilxomjon.dur_novvot_dokon.ui

import android.content.Intent
import android.os.Bundle
import com.ilxomjon.dur_novvot_dokon.ui.widget.QuickWidget
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.ActivityMainBinding
import com.ilxomjon.dur_novvot_dokon.ui.history.HistoryFragment
import com.ilxomjon.dur_novvot_dokon.ui.home.HomeFragment
import com.ilxomjon.dur_novvot_dokon.ui.login.LoginFragment
import com.ilxomjon.dur_novvot_dokon.ui.payment.PaymentFragment
import com.ilxomjon.dur_novvot_dokon.ui.report.ReportFragment
import com.ilxomjon.dur_novvot_dokon.ui.sale.SaleFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ilxomjon.dur_novvot_dokon.ui.diag.DiagnosticsSheet
import kotlinx.coroutines.launch

/** Bitta Activity: pastki navigatsiya (5 bo'lim) + ustiga ochiladigan ekranlar. */
class MainActivity : AppCompatActivity(), Navigator {

    private lateinit var binding: ActivityMainBinding
    private var loggedIn = false

    // v2.4: PIN qulfi
    private var lockView: android.view.View? = null
    private var wrongPins = 0
    private var lockedUntil = 0L
    private val lockBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { moveTaskToBack(true) }
    }

    /**
     * Shrift/o'lcham uchun o'ralgan kontekst PrintManager'ga Activity'ni ko'rsatmaydi ("Can print only from an activity").
     * Chop etish xizmati asl kontekstdan olinadi (uning "tashqi" konteksti — shu Activity).
     */
    private var originalBase: android.content.Context? = null

    override fun getSystemService(name: String): Any? =
        if (name == android.content.Context.PRINT_SERVICE) originalBase?.getSystemService(name) ?: super.getSystemService(name)
        else super.getSystemService(name)

    override fun attachBaseContext(newBase: android.content.Context) {
        originalBase = newBase
        // Sozlamalar ▸ Qulayliklar ▸ Ekran o'lchami / Shrift
        val wrapped = runCatching {
            com.ilxomjon.dur_novvot_dokon.data.extras.Extras.wrap(newBase, com.ilxomjon.dur_novvot_dokon.data.AppPrefs(newBase))
        }.getOrDefault(newBase)
        super.attachBaseContext(wrapped)
    }

    private val tabIds = listOf(R.id.nav_home, R.id.nav_sale, R.id.nav_payment, R.id.nav_history, R.id.nav_last)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // v4.0: katta shriftda tugma yozuvlari sig'sin (barcha ekranlarda)
        uz.imsoft.shared.android.FitText.install(this)
        if (savedInstanceState == null) com.ilxomjon.dur_novvot_dokon.ui.ledger.SecretPrefs.countLaunch(this)
        // v3.0 Admin: agentlar ko'prigi yoqilgan bo'lsa ishlab tursin (ilova ochilganda — fon cheklovlari tufayli)
        if (savedInstanceState == null) com.ilxomjon.dur_novvot_dokon.data.relay.RelayService.ensureRunning(applicationContext)

        applyRoleToNav()
        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        supportFragmentManager.addOnBackStackChangedListener { updateChrome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
        onBackPressedDispatcher.addCallback(this, lockBack)
        if (savedInstanceState == null) backgroundAt = 0L

        binding.tvStatus.setOnClickListener { DiagnosticsSheet.show(supportFragmentManager) }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val open = androidx.core.view.ViewCompat.getRootWindowInsets(binding.root)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            if (open != imeOpen) { imeOpen = open; binding.root.post { updateChrome() } }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(ServiceLocator.network.state, ServiceLocator.outbox.counts, ServiceLocator.outbox.lastError) { n, c, e -> Triple(n, c, e) }
                    .collect { (n, c, e) -> renderStatus(n.online, c.unsent, c.needsCheck, e != null) }
            }
        }
        lifecycleScope.launch {
            val state = ServiceLocator.catalog.state.first { it.loaded }
            loggedIn = state.agent != null
            if (savedInstanceState == null) {
                if (loggedIn) startTabs() else showLogin()
                if (loggedIn) handleQuick(intent)
            } else {
                updateChrome()
            }
        }
        // v4.0: yangi versiya bormi (3 soatda bir) — bo'lsa "O'rnatish" oynasi
        if (savedInstanceState == null) lifecycleScope.launch { kotlinx.coroutines.delay(2_500); checkUpdate(false) }
        // v3.2: avtomatik yangilash (ish vaqtida har N daqiqada, ilova ochiq turganda) va kunlik zaxira
        if (savedInstanceState == null) com.ilxomjon.dur_novvot_dokon.data.AutoUpdate.apply(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                runCatching { com.ilxomjon.dur_novvot_dokon.data.AutoUpdate.dailyBackupIfDue() }
                var tick = 0
                while (true) {
                    // v4.0: qurilma holati (biriktirish/bloklash) — har daqiqada; Admin ham ro'yxatda ko'rinsin
                    if (tick++ % 2 == 0) checkDevice()
                    kotlinx.coroutines.delay(30_000)
                    val cat = ServiceLocator.catalog.state.value
                    val due = System.currentTimeMillis() - cat.lastSyncAt >= com.ilxomjon.dur_novvot_dokon.data.AutoUpdate.intervalMs()
                    if (due && cat.agent != null && !cat.syncing && ServiceLocator.network.state.value.online) {
                        runCatching { ServiceLocator.catalog.sync() }
                        if (com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin) com.ilxomjon.dur_novvot_dokon.data.share.ShareWorker.kick(applicationContext, 1)
                    }
                }
            }
        }
    }

    /** v4.0: ilova yangilanishi. [force] — Sozlamalardan "Tekshirish" bosilganda (topilmasa ham xabar beradi). */
    fun checkUpdate(force: Boolean) {
        val u = ServiceLocator.updater
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

    /**
     * v4.0: Admin bu telefonni bloklasa yoki boshqa agentga biriktirsa — darhol amal qiladi (ma'lumot ko'rsatilmaydi,
     * kutish ekrani chiqadi). Internet yo'q — hech narsa o'zgarmaydi (oxirgi holat bilan ishlayveradi).
     */
    private suspend fun checkDevice() {
        val d = ServiceLocator.device() ?: return
        val before = d.cached()
        val a = runCatching { d.sync() }.getOrElse { return }
        if (!ServiceLocator.deviceMode || !loggedIn) return
        val current = ServiceLocator.catalog.state.value.agent?.id.orEmpty()
        // Eski usulda kirgan agent (hali biriktirilmagan) — chiqarib yuborilmaydi; Admin biriktirgach shu qoida ishlaydi
        val kick = if (a == null) before != null else a.blocked || (a.agentId.isNotEmpty() && a.agentId != current)
        if (kick) {
            runCatching { ServiceLocator.auth.logout() }
            onLoggedOut()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (loggedIn) handleQuick(intent)
    }

    /** v3.2 vidjet: tez savdo (tanlangan do'kon bilan), skaner yoki to'lov. Savatcha to'la bo'lsa — do'kon almashtirilmaydi. */
    private fun handleQuick(i: Intent?) {
        i ?: return
        val storeId = i.getStringExtra(QuickWidget.EXTRA_STORE)
        val action = i.getStringExtra(QuickWidget.EXTRA_ACTION).orEmpty()
        if (storeId == null && action.isEmpty()) return
        i.removeExtra(QuickWidget.EXTRA_STORE)
        i.removeExtra(QuickWidget.EXTRA_ACTION)
        when (action) {
            QuickWidget.ACT_PAYMENT -> { openTab(R.id.nav_payment); return }
            QuickWidget.ACT_SCAN -> { openTab(R.id.nav_sale); push(com.ilxomjon.dur_novvot_dokon.ui.scan.ScanFragment()); return }
        }
        val draft = ServiceLocator.draft
        val st = storeId?.let { ServiceLocator.catalog.storeById(it) }
        if (st != null && draft.state.value.lines.isEmpty()) draft.setStore(st)
        openTab(R.id.nav_sale)
    }

    // v2.6: ulanadigan (Bluetooth/USB) shtrix kod skaneri klaviatura kabi yozadi. Savdo ekrani ochiq va hech qaysi
    // matn maydoni tanlanmagan bo'lsa ham kodni ushlab, savatchaga qo'shamiz.
    private val scanBuf = StringBuilder()
    private var scanAt = 0L

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.device?.isVirtual == false && currentFocus !is android.widget.EditText) {
            // Savdo ekrani (asosiy) yoki ochiq turgan "Narx yorliqlari" ekrani
            val target: ((String) -> Boolean)? = if (supportFragmentManager.backStackEntryCount == 0) {
                (supportFragmentManager.findFragmentByTag(tabTag(R.id.nav_sale)) as? SaleFragment)?.takeIf { it.isVisible }?.let { f -> { c: String -> f.onHardwareScan(c) } }
            } else {
                (supportFragmentManager.findFragmentById(R.id.container) as? com.ilxomjon.dur_novvot_dokon.ui.labels.LabelsFragment)?.takeIf { it.isVisible }?.let { f -> { c: String -> f.onHardwareScan(c) } }
            }
            if (target != null) {
                val now = System.currentTimeMillis()
                if (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER || event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    val code = scanBuf.toString()
                    scanBuf.setLength(0)
                    if (target(code)) return true
                } else {
                    val c = event.unicodeChar
                    if (c > 0 && !Character.isISOControl(c)) {
                        if (now - scanAt > 300) scanBuf.setLength(0)
                        scanBuf.append(c.toChar())
                        scanAt = now
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        com.ilxomjon.dur_novvot_dokon.ui.ledger.SecretPrefs.apply(this)
        if (ServiceLocator.appPrefs.keepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        val p = ServiceLocator.appPrefs
        if (p.pinHash.isNotEmpty() && lockView == null) {
            val away = System.currentTimeMillis() - backgroundAt
            if (backgroundAt == 0L || away >= maxOf(p.lockAfterMin * 60_000L, 5_000L)) showLock()
        }
        com.ilxomjon.dur_novvot_dokon.ui.widget.QuickWidget.refresh(applicationContext)
        // Mijoz ilovasi: ilova ochilganda 2-ilova so'rovi va buyurtmalari darhol tekshiriladi.
        if (ServiceLocator.shareStore.isConfigured) com.ilxomjon.dur_novvot_dokon.data.share.ShareWorker.kick(this, 1)
        // Avto-yangilash: ma'lumot eski bo'lsa (Qulayliklar ▸ Avto-yangilash)
        val h = p.autoSyncHours
        val cat = ServiceLocator.catalog.state.value
        if (h > 0 && cat.agent != null && !cat.syncing && ServiceLocator.network.state.value.online &&
            System.currentTimeMillis() - cat.lastSyncAt > h * 3_600_000L
        ) {
            ServiceLocator.appScope.launch { runCatching { ServiceLocator.catalog.sync() } }
        }
    }

    private fun showLock() {
        if (lockView != null) return
        val root = window.decorView as android.view.ViewGroup
        val v = layoutInflater.inflate(R.layout.view_pin_lock, root, false)
        root.addView(v)
        lockView = v
        lockBack.isEnabled = true
        val edt = v.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtPin)
        val til = v.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPin)
        val tryUnlock = {
            val now = System.currentTimeMillis()
            if (now < lockedUntil) {
                til.error = getString(R.string.pin_wait, ((lockedUntil - now) / 1000 + 1).toInt())
            } else if (com.ilxomjon.dur_novvot_dokon.data.extras.Extras.checkPin(ServiceLocator.appPrefs, edt.text?.toString().orEmpty())) {
                wrongPins = 0
                hideLock()
            } else {
                wrongPins++
                edt.setText("")
                if (wrongPins >= 5) { lockedUntil = now + 30_000L; wrongPins = 0 }
                til.error = getString(R.string.pin_wrong)
            }
        }
        v.findViewById<android.view.View>(R.id.btnUnlock).setOnClickListener { tryUnlock() }
        edt.setOnEditorActionListener { _, _, _ -> tryUnlock(); true }
        v.findViewById<android.view.View>(R.id.btnForgot).setOnClickListener { forgotPin() }
        edt.requestFocus()
    }

    private fun hideLock() {
        lockView?.let { (window.decorView as android.view.ViewGroup).removeView(it) }
        lockView = null
        lockBack.isEnabled = false
        val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    /** PIN unutilsa: agent paroli serverda tekshiriladi, to'g'ri bo'lsa PIN o'chiriladi. */
    private fun forgotPin() {
        val agent = ServiceLocator.catalog.state.value.agent
        if (agent == null) { com.ilxomjon.dur_novvot_dokon.data.extras.Extras.setPin(ServiceLocator.appPrefs, null); hideLock(); return }
        val til = com.google.android.material.textfield.TextInputLayout(this).apply { setPadding(48, 16, 48, 0) }
        val edt = com.google.android.material.textfield.TextInputEditText(til.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHint(R.string.password_label)
        }
        til.addView(edt)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(R.string.pin_forgot).setMessage(R.string.pin_forgot_msg).setView(til)
            .setPositiveButton(R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val ok = runCatching { ServiceLocator.api.login(agent.phone, edt.text?.toString().orEmpty()) != null }.getOrDefault(false)
                    if (ok) {
                        com.ilxomjon.dur_novvot_dokon.data.extras.Extras.setPin(ServiceLocator.appPrefs, null)
                        hideLock()
                    } else {
                        lockView?.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPin)?.error = getString(R.string.pin_forgot_fail)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    override fun onStop() {
        super.onStop()
        if (lockView == null) backgroundAt = System.currentTimeMillis()
        // Savatchadagi oxirgi o'zgarishlar bazaga yozilib bo'linsin (ilova fonga ketganda jarayon o'ldirilishi mumkin).
        ServiceLocator.appScope.launch { runCatching { ServiceLocator.draft.flush() } }
    }

    /** Doimiy holat belgisi: faqat muammo bo'lganda ko'rinadi. "Tasdiqlandi" degan so'z yo'q. */
    private fun renderStatus(online: Boolean, unsent: Int, needsCheck: Int, lastFailed: Boolean) {
        val text = when {
            !online -> if (unsent > 0) getString(R.string.status_offline_n, unsent) else getString(R.string.status_offline)
            needsCheck > 0 -> getString(R.string.status_needs_check, needsCheck)
            lastFailed && unsent > 0 -> getString(R.string.status_server_n, unsent)
            else -> null
        }
        binding.tvStatus.text = text
        binding.tvStatus.visibility = if (text != null && loggedIn) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun startTabs() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        showTab(R.id.nav_home)
        updateChrome()
    }

    private fun showLogin() {
        clearAll()
        // v4.0: kalitsiz (Admin biriktiradi) — kirish oynasi o'rniga kutish ekrani
        val f: Fragment = if (ServiceLocator.deviceMode) com.ilxomjon.dur_novvot_dokon.ui.login.DeviceWaitFragment() else LoginFragment()
        supportFragmentManager.beginTransaction().setReorderingAllowed(true)
            .replace(R.id.container, f, TAG_LOGIN).commit()
        updateChrome()
    }

    private fun createTab(id: Int): Fragment = when (id) {
        R.id.nav_home -> HomeFragment()
        R.id.nav_sale -> SaleFragment()
        R.id.nav_payment -> PaymentFragment()
        R.id.nav_history -> HistoryFragment()
        // v5.0: oxirgi bo'lim — Admin'da Boshqaruv, agentda Sozlamalar
        else -> if (com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin) {
            com.ilxomjon.dur_novvot_dokon.ui.admin.AdminHubFragment().asRootTab()
        } else {
            com.ilxomjon.dur_novvot_dokon.ui.settings.SettingsHubFragment().asRootTab()
        }
    }

    /** Oxirgi bo'limning nomi va ikonkasi rolga qarab. */
    private fun applyRoleToNav() {
        val admin = com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin
        binding.bottomNav.menu.findItem(R.id.nav_last)?.apply {
            setTitle(if (admin) R.string.nav_manage else R.string.nav_settings)
            setIcon(if (admin) R.drawable.ic_tune else R.drawable.ic_settings)
        }
    }

    private fun showTab(id: Int) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        fm.findFragmentByTag(TAG_LOGIN)?.let { tx.remove(it) }
        var target = fm.findFragmentByTag(tabTag(id))
        if (target == null) {
            target = createTab(id)
            tx.add(R.id.container, target, tabTag(id))
        }
        for (other in tabIds) {
            if (other == id) continue
            fm.findFragmentByTag(tabTag(other))?.let { tx.hide(it) }
        }
        tx.show(target).commitNowAllowingStateLoss()
        updateChrome()
    }

    private fun tabTag(id: Int) = "tab_$id"

    private fun clearAll() {
        val fm = supportFragmentManager
        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val tx = fm.beginTransaction()
        fm.fragments.forEach { tx.remove(it) }
        tx.commitNowAllowingStateLoss()
    }

    /** v3.1: klaviatura ochiq — pastki menyu yashiriladi (ro'yxatga joy). */
    private var imeOpen = false

    private fun updateChrome() {
        val showNav = loggedIn && supportFragmentManager.backStackEntryCount == 0 && !imeOpen
        binding.bottomNav.visibility = if (showNav) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun handleBack() {
        val fm = supportFragmentManager
        when {
            fm.backStackEntryCount > 0 -> fm.popBackStack()
            loggedIn && binding.bottomNav.selectedItemId != R.id.nav_home -> binding.bottomNav.selectedItemId = R.id.nav_home
            else -> {
                // Tasodifan chiqib ketmaslik uchun: 2 soniya ichida ikkinchi marta bosilsa chiqadi.
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) finish()
                else {
                    lastBackAt = now
                    android.widget.Toast.makeText(this, R.string.back_again_to_exit, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var lastBackAt = 0L

    // --- Navigator
    override fun openTab(itemId: Int) {
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        binding.bottomNav.selectedItemId = itemId
    }

    override fun push(fragment: Fragment) {
        supportFragmentManager.beginTransaction().setReorderingAllowed(true)
            .add(R.id.container, fragment).addToBackStack(null).commit()
    }

    override fun pop() {
        supportFragmentManager.popBackStack()
    }

    override fun onLoggedIn() {
        loggedIn = true
        clearAll()
        startTabs()
    }

    override fun onLoggedOut() {
        loggedIn = false
        showLogin()
    }

    private companion object {
        const val TAG_LOGIN = "login"
        /** Ilova oxirgi marta fonga ketgan vaqt (jarayon o'lsa 0 — PIN so'raladi). */
        @Volatile var backgroundAt = 0L
    }
}
