package com.mtlive.iptv.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mtlive.iptv.R
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.ContentType
import com.mtlive.iptv.data.SessionStore
import com.mtlive.iptv.databinding.LayoutTopbarBinding

object Tabs { const val HOME = 0; const val LIVE = 1; const val MOVIES = 2; const val SERIES = 3; const val SETTINGS = 4 }

fun Activity.fullscreen() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val c = WindowInsetsControllerCompat(window, window.decorView)
    c.hide(WindowInsetsCompat.Type.systemBars())
    c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

/** يتأكد من وجود جلسة؛ إذا ما موجودة يرجع لتسجيل الدخول */
fun Activity.requireSession(): Boolean {
    if (Cache.session == null) Cache.session = SessionStore.load(this)
    if (Cache.session == null) {
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        return false
    }
    return true
}

fun Activity.setupTopbar(tb: LayoutTopbarBinding, current: Int) {
    val tabs = listOf(tb.tabHome, tb.tabLive, tb.tabMovies, tb.tabSeries, tb.tabSettings)
    tabs.forEachIndexed { i, v ->
        v.isSelected = i == current
        v.setOnClickListener {
            if (i == current) return@setOnClickListener
            when (i) {
                Tabs.HOME -> {
                    startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    if (this !is HomeActivity) finish()
                }
                Tabs.SETTINGS -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    if (this !is HomeActivity) finish()
                }
                else -> {
                    val type = when (i) { Tabs.LIVE -> ContentType.LIVE; Tabs.MOVIES -> ContentType.MOVIE; else -> ContentType.SERIES }
                    startActivity(SectionActivity.intent(this, type))
                    if (this !is HomeActivity) finish()
                }
            }
        }
    }
    val exp = Cache.session?.expiryText()
    tb.tvExpiry.visibility = View.VISIBLE
    tb.tvExpiry.text = getString(R.string.expires, exp ?: getString(R.string.unlimited))
}
