package com.ilxomjon.dur_novvot_dokon

import android.app.Application
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
