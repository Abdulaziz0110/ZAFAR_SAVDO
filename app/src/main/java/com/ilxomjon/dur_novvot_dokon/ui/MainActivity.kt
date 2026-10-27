package com.ilxomjon.dur_novvot_dokon.ui

import android.os.Bundle
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Bitta Activity: pastki navigatsiya (5 bo'lim) + ustiga ochiladigan ekranlar. */
class MainActivity : AppCompatActivity(), Navigator {

    private lateinit var binding: ActivityMainBinding
    private var loggedIn = false

    private val tabIds = listOf(R.id.nav_home, R.id.nav_sale, R.id.nav_payment, R.id.nav_history, R.id.nav_report)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        supportFragmentManager.addOnBackStackChangedListener { updateChrome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        lifecycleScope.launch {
            val state = ServiceLocator.catalog.state.first { it.loaded }
            loggedIn = state.agent != null
            if (savedInstanceState == null) {
                if (loggedIn) startTabs() else showLogin()
            } else {
                updateChrome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ServiceLocator.appPrefs.keepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startTabs() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        showTab(R.id.nav_home)
        updateChrome()
    }

    private fun showLogin() {
        clearAll()
        supportFragmentManager.beginTransaction().setReorderingAllowed(true)
            .replace(R.id.container, LoginFragment(), TAG_LOGIN).commit()
        updateChrome()
    }

    private fun createTab(id: Int): Fragment = when (id) {
        R.id.nav_home -> HomeFragment()
        R.id.nav_sale -> SaleFragment()
        R.id.nav_payment -> PaymentFragment()
        R.id.nav_history -> HistoryFragment()
        else -> ReportFragment()
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

    private fun updateChrome() {
        val showNav = loggedIn && supportFragmentManager.backStackEntryCount == 0
        binding.bottomNav.visibility = if (showNav) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun handleBack() {
        val fm = supportFragmentManager
        when {
            fm.backStackEntryCount > 0 -> fm.popBackStack()
            loggedIn && binding.bottomNav.selectedItemId != R.id.nav_home -> binding.bottomNav.selectedItemId = R.id.nav_home
            else -> finish()
        }
    }

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
    }
}
