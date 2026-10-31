package com.ilxomjon.dur_novvot_dokon

import android.app.Application
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        com.ilxomjon.dur_novvot_dokon.core.AppRole.init(this)
        ServiceLocator.init(this)
        // Chek/yorliq shriftlari (Liberation Sans) — fonda yuklanadi
        Thread({ uz.imsoft.shared.android.PrintFonts.init(this) }, "print-fonts").start()
    }
}
