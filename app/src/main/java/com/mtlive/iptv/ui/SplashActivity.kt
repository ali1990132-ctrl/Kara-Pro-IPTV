package com.mtlive.iptv.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.SessionStore
import com.mtlive.iptv.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()

        b.imgSplash.alpha = 0f
        b.imgSplash.scaleX = 0.8f
        b.imgSplash.scaleY = 0.8f
        b.imgSplash.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).start()

        lifecycleScope.launch {
            val saved = SessionStore.load(this@SplashActivity)
            var next: Class<*> = LoginActivity::class.java
            if (saved != null) {
                Cache.session = saved
                next = HomeActivity::class.java
                // تحديث معلومات الاشتراك (تاريخ الانتهاء) بصمت
                try {
                    val info = saved.api().login()
                    val s = saved.copy(expDate = info.expDate, activeCons = info.activeCons, maxCons = info.maxConnections)
                    Cache.session = s
                    SessionStore.save(this@SplashActivity, s)
                } catch (e: Exception) {
                    if ((e.message ?: "").contains("الاشتراك منتهي") || (e.message ?: "").contains("غير صحيحة")) {
                        SessionStore.clear(this@SplashActivity)
                        Cache.session = null
                        next = LoginActivity::class.java
                    }
                }
            }
            delay(1600)
            startActivity(Intent(this@SplashActivity, next))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}
