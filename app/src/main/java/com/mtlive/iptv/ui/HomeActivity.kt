package com.mtlive.iptv.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mtlive.iptv.R
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.ContentType
import com.mtlive.iptv.data.SessionStore
import com.mtlive.iptv.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private lateinit var b: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()

        b.tileLive.setOnClickListener { startActivity(SectionActivity.intent(this, ContentType.LIVE)) }
        b.tileMovies.setOnClickListener { startActivity(SectionActivity.intent(this, ContentType.MOVIE)) }
        b.tileSeries.setOnClickListener { startActivity(SectionActivity.intent(this, ContentType.SERIES)) }
        b.tileSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnReload.setOnClickListener { Cache.clear(); refreshCounts(true) }
        b.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.yes) { _, _ ->
                    SessionStore.clear(this)
                    Cache.session = null
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
        b.tileLive.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (Cache.session == null) return
        setupTopbar(b.topbar, Tabs.HOME)
        val s = Cache.session!!
        val cons = if (s.maxCons != null) "  •  " + getString(R.string.connections, s.activeCons ?: "0", s.maxCons) else ""
        b.tvAccount.text = getString(R.string.account, s.username) + cons
        refreshCounts(false)
    }

    private fun refreshCounts(force: Boolean) {
        val views = mapOf(ContentType.LIVE to b.countLive, ContentType.MOVIE to b.countMovies, ContentType.SERIES to b.countSeries)
        views.forEach { (type, tv) ->
            val cached = Cache.items(type)
            if (cached != null && !force) { tv.text = cached.size.toString(); return@forEach }
            tv.text = getString(R.string.loading)
            lifecycleScope.launch {
                tv.text = try { Cache.load(type, force).second.size.toString() } catch (e: Exception) { "—" }
            }
        }
    }
}
