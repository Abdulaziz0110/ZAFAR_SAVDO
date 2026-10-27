package com.ilxomjon.dur_novvot_dokon.ui

import androidx.fragment.app.Fragment

/** MainActivity bajaradigan oddiy navigatsiya. */
interface Navigator {
    fun openTab(itemId: Int)
    fun push(fragment: Fragment)
    fun pop()
    fun onLoggedIn()
    fun onLoggedOut()
}
