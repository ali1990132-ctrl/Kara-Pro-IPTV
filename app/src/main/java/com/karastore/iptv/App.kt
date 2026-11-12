package com.karastore.iptv

import android.app.Application
import android.content.Intent
import com.karastore.iptv.ui.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class App : Application() {
    override fun onCreate() {
        super.onCreate()
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
