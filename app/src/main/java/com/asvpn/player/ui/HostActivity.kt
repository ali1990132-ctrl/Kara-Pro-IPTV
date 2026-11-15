package com.asvpn.player.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.asvpn.player.R

/** Simple screen with a back button that hosts one fragment. */
class HostActivity : AppCompatActivity() {

    companion object {
        const val VPN = "vpn"
        const val SETTINGS = "settings"
        const val LINKS = "links"
        const val LISTS = "lists"

        fun open(ctx: Context, page: String) =
            ctx.startActivity(Intent(ctx, HostActivity::class.java).putExtra("page", page))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val page = intent.getStringExtra("page") ?: SETTINGS
        val (title, f) = when (page) {
            VPN -> "VPN" to VpnFragment()
            LINKS -> "مشغّل الروابط" to LinksFragment()
            LISTS -> "قوائم التشغيل" to ListsFragment()
            else -> "الإعدادات" to SettingsFragment()
        }
        findViewById<TextView>(R.id.title).text = title
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.container, f as Fragment).commit()
        }
    }
}
