package com.asvpn.player.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.asvpn.player.R
import com.asvpn.player.data.Cache
import com.asvpn.player.data.ContentType
import com.asvpn.player.data.SessionStore
import com.asvpn.player.vpn.Vpn
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var chip: TextView
    private lateinit var tabs: List<TextView>
    private var current = -1
    private val vpnListener: (Vpn.State) -> Unit = { updateChip(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionStore.load(this)
        if (session == null) {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        Cache.session = session
        setContentView(R.layout.activity_main)

        chip = findViewById(R.id.vpnChip)
        chip.setOnClickListener { HostActivity.open(this, HostActivity.VPN) }
        findViewById<android.view.View>(R.id.btnSearch).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener {
            HostActivity.open(this, HostActivity.SETTINGS)
        }

        tabs = listOf(
            findViewById(R.id.tabHome), findViewById(R.id.tabLive),
            findViewById(R.id.tabMovies), findViewById(R.id.tabSeries)
        )
        tabs.forEachIndexed { i, t -> t.setOnClickListener { select(i) } }
        select(savedInstanceState?.getInt("tab") ?: 0, force = true)

        handleShare(intent)
        refreshAccount()
    }

    /** re-checks the subscription with the server and shows the expired notice if needed */
    private fun refreshAccount() {
        val s = Cache.session ?: return
        if (s.isExpired()) Nav.expiredDialog(this)
        lifecycleScope.launch {
            try {
                val info = s.api().login()
                val fresh = s.copy(status = info.status, expDate = info.expDate)
                SessionStore.save(this@MainActivity, fresh)
                Cache.session = fresh
                if (fresh.isExpired() && !s.isExpired()) Nav.expiredDialog(this@MainActivity)
                (supportFragmentManager.findFragmentById(R.id.container) as? DashboardFragment)?.bindAccount()
            } catch (_: Exception) {
            }
        }
    }

    fun select(i: Int, force: Boolean = false) {
        if (i == current && !force) return
        current = i
        tabs.forEachIndexed { n, t -> t.isSelected = n == i }
        val f: Fragment = when (i) {
            1 -> SectionFragment.of(ContentType.LIVE)
            2 -> SectionFragment.of(ContentType.MOVIE)
            3 -> SectionFragment.of(ContentType.SERIES)
            else -> DashboardFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (current != 0) select(0) else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = Regex("https?://\\S+").find(text)?.value ?: return
            PlayerActivity.start(this, Ui.titleFromUrl(url), url)
        }
    }

    override fun onStart() {
        super.onStart()
        if (::chip.isInitialized) {
            Vpn.addListener(vpnListener)
            Vpn.refresh(this)
        }
    }

    override fun onStop() {
        super.onStop()
        Vpn.removeListener(vpnListener)
    }

    private fun updateChip(s: Vpn.State) {
        val (text, color) = when (s) {
            Vpn.State.ON -> "VPN ✓" to R.color.green
            Vpn.State.CONNECTING -> "VPN …" to R.color.orange
            Vpn.State.ERROR -> "VPN ✕" to R.color.red
            Vpn.State.OFF -> "VPN" to R.color.text2
        }
        chip.text = text
        chip.setTextColor(ContextCompat.getColor(this, color))
    }
}
