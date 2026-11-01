package com.ilxomjon.dur_novvot_dokon

import android.app.Application
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        com.ilxomjon.dur_novvot_dokon.core.AppRole.init(this)
        // v4.0: yiqilsa — xato matni saqlanadi va keyingi ochilishda Admin'ga ("Qurilmalar") o'zi boradi
        uz.imsoft.shared.android.CrashLog.install(this, if (com.ilxomjon.dur_novvot_dokon.core.AppRole.isAdmin) "Admin" else "Agent", BuildConfig.VERSION_NAME)
        ServiceLocator.init(this)
        // Chek/yorliq shriftlari (Liberation Sans) — fonda yuklanadi
        Thread({ uz.imsoft.shared.android.PrintFonts.init(this) }, "print-fonts").start()
    }
}
