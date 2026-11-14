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
        b.tileSeries.setOnClickListener { startActivity(SectionActivity.intent(this, ContentType.SERIES)) }
        b.tileMovies.setOnClickListener { startActivity(SectionActivity.intent(this, ContentType.MOVIE)) }
        b.tileUpdate.setOnClickListener { Updater.manualCheck(this, lifecycleScope) { showUpdateState(it) } }
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
        showUpdateState(Updater.latest)
        b.tileLive.requestFocus()

        // فحص التحديث تلقائياً عند فتح التطبيق
        Updater.autoCheck(this, lifecycleScope) { showUpdateState(it) }
    }

    private fun showUpdateState(info: UpdateInfo?) {
        b.countUpdate.text = if (Updater.hasUpdate(info)) getString(R.string.tile_update_new)
                             else getString(R.string.tile_update_sub, Updater.currentName)
    }

    override fun onResume() {
        super.onResume()
        if (Cache.session == null || !::b.isInitialized) return
        setupTopbar(b.topbar, Tabs.HOME)
        val s = Cache.session!!
        b.tvAccount.text = getString(R.string.account_id, s.username)
        b.tvInternet.text = getString(R.string.internet, "${s.activeCons ?: "0"}/${s.maxCons ?: "∞"}")
        refreshCounts(false)
    }

    private fun refreshCounts(force: Boolean) {
        val subs = mapOf(
            ContentType.LIVE to (b.countLive to R.string.tile_live_sub),
            ContentType.MOVIE to (b.countMovies to R.string.tile_movies_sub),
            ContentType.SERIES to (b.countSeries to R.string.tile_series_sub)
        )
        subs.forEach { (type, pair) ->
            val (tv, sub) = pair
            val cached = Cache.items(type)
            if (cached != null && !force) { tv.text = "${getString(sub)} • ${cached.size}"; return@forEach }
            tv.text = getString(sub)
            lifecycleScope.launch {
                try {
                    val n = Cache.load(type, force).second.size
                    tv.text = "${getString(sub)} • $n"
                } catch (e: Exception) { tv.text = getString(sub) }
            }
        }
    }
}
