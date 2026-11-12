package com.karastore.iptv.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.karastore.iptv.R
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.ActivityMainBinding
import com.karastore.iptv.ui.login.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Repo.session == null) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish(); return
        }

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // الشريط السفلي (هاتف) أو القائمة الجانبية (تلفزيون) — كلاهما NavigationBarView
        val nav = b.bottomNav as com.google.android.material.navigation.NavigationBarView

        // تحديث تلقائي للمكتبة حسب الإعداد
        if (com.karastore.iptv.data.Settings.shouldAutoRefresh(this)) {
            com.karastore.iptv.data.ContentCache.clearAll()
            com.karastore.iptv.data.Settings.markRefreshed(this)
        }

        nav.setOnItemSelectedListener { item ->
            val f: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_live -> ContentFragment.newInstance(ContentType.LIVE)
                R.id.nav_movies -> ContentFragment.newInstance(ContentType.MOVIE)
                R.id.nav_series -> ContentFragment.newInstance(ContentType.SERIES)
                else -> MoreFragment()
            }
            show(f)
            true
        }

        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_home
        }
    }

    private fun show(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f)
            .commit()
    }

    fun goToTab(itemId: Int) {
        (b.bottomNav as com.google.android.material.navigation.NavigationBarView).selectedItemId = itemId
    }
}
