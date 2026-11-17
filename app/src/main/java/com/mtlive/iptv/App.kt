package com.mtlive.iptv

import android.app.Application
import com.mtlive.iptv.data.SecurityGuard

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        tampered = !SecurityGuard.verify(this)
    }

    companion object {
        /** صار true لو التطبيق متعدّل/معاد توقيعه */
        @JvmStatic
        var tampered: Boolean = false
            private set
    }
}
