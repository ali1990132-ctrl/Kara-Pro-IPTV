package com.asvpn.player.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.asvpn.player.R
import com.asvpn.player.vpn.Vpn
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var chip: TextView
    private val vpnListener: (Vpn.State) -> Unit = { updateChip(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        chip = findViewById(R.id.vpnChip)
        chip.setOnClickListener { findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_vpn }

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            show(
                when (item.itemId) {
                    R.id.nav_lists -> ListsFragment()
                    R.id.nav_vpn -> VpnFragment()
                    R.id.nav_settings -> SettingsFragment()
                    else -> HomeFragment()
                }
            )
            true
        }
        if (savedInstanceState == null) show(HomeFragment())
        handleShare(intent)
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

    private fun show(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
    }

    override fun onStart() {
        super.onStart()
        Vpn.addListener(vpnListener)
        Vpn.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        Vpn.removeListener(vpnListener)
    }

    private fun updateChip(s: Vpn.State) {
        val (text, color) = when (s) {
            Vpn.State.ON -> "VPN متصل" to R.color.green
            Vpn.State.CONNECTING -> "جاري الاتصال…" to R.color.orange
            Vpn.State.ERROR -> "فشل الاتصال" to R.color.red
            Vpn.State.OFF -> "VPN غير متصل" to R.color.text2
        }
        chip.text = text
        chip.setTextColor(ContextCompat.getColor(this, color))
    }
}
