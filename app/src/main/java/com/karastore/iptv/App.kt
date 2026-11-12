package com.karastore.iptv

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import com.karastore.iptv.ui.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class App : Application() {

    /** هل الجهاز تلفزيون/بوكس أندرويد؟ */
    private fun isTv(): Boolean {
        val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature("android.hardware.type.television")
    }

    override fun onCreate() {
        super.onCreate()

        // على التلفزيون: اعرض بالوضع الأفقي بدل الطولي (كل الشاشات)
        if (isTv()) {
            registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) {
                    try { a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE } catch (_: Throwable) {}
                }
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            })
        }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val trace = "${e.javaClass.name}: ${e.message}\n\n$sw"
                getSharedPreferences("crash", MODE_PRIVATE).edit().putString("last", trace).apply()
                val i = Intent(this, CrashActivity::class.java)
                    .putExtra("trace", trace)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(i)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, e)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }
}
