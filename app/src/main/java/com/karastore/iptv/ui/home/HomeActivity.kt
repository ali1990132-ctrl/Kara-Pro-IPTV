package com.karastore.iptv.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.karastore.iptv.R
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.SessionStore
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.ActivityHomeBinding
import com.karastore.iptv.ui.list.ContentListActivity
import com.karastore.iptv.ui.login.LoginActivity
import com.karastore.iptv.util.Format

class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val session = Repo.session
        if (session == null) { goLogin(); return }

        val display = session.name.ifBlank { session.username.ifBlank { getString(R.string.app_name) } }
        b.tvWelcome.text = "${getString(R.string.welcome)} $display"

        val info = Repo.userInfo
        if (Repo.isXtream && info != null) {
            val date = Format.expDate(info.expDate)
            b.tvExpiry.text = if (date != null)
                "${getString(R.string.expires)}: $date"
            else getString(R.string.status_active)
        } else {
            b.tvExpiry.text = getString(R.string.status_active)
        }

        // في وضع M3U لا توجد أفلام/مسلسلات منفصلة
        if (!Repo.isXtream) {
            b.tileMovies.visibility = View.GONE
            b.tileSeries.visibility = View.GONE
        }

        b.tileLive.setOnClickListener { openList(ContentType.LIVE, getString(R.string.home_live)) }
        b.tileMovies.setOnClickListener { openList(ContentType.MOVIE, getString(R.string.home_movies)) }
        b.tileSeries.setOnClickListener { openList(ContentType.SERIES, getString(R.string.home_series)) }
        b.tileSettings.setOnClickListener { showSettings() }
        b.btnLogout.setOnClickListener { confirmLogout() }
    }

    private fun openList(type: ContentType, title: String) {
        startActivity(Intent(this, ContentListActivity::class.java).apply {
            putExtra(ContentListActivity.EXTRA_TYPE, type.name)
            putExtra(ContentListActivity.EXTRA_TITLE, title)
        })
    }

    private fun showSettings() {
        AlertDialog.Builder(this)
            .setTitle(R.string.home_settings)
            .setMessage(getString(R.string.app_name) + " v1.0")
            .setPositiveButton(R.string.logout) { _, _ -> doLogout() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setMessage(R.string.logout)
            .setPositiveButton(R.string.logout) { _, _ -> doLogout() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doLogout() {
        SessionStore.clear(this)
        Repo.logout()
        goLogin()
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
