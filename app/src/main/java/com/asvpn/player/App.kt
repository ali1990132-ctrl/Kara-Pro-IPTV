package com.asvpn.player

import android.app.Application
import com.asvpn.player.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
