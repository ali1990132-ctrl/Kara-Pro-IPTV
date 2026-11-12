package com.karastore.iptv.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.karastore.iptv.R
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.LoginMode
import com.karastore.iptv.data.PlaylistStore
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.Session
import com.karastore.iptv.data.SessionStore
import com.karastore.iptv.databinding.ActivityLoginBinding
import com.karastore.iptv.ui.main.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private var mode = LoginMode.XTREAM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val addMode = intent.getBooleanExtra(EXTRA_ADD, false)
        // دخول تلقائي إذا كانت الجلسة محفوظة (إلا بوضع "إضافة قائمة")
        if (!addMode) {
            SessionStore.load(this)?.let { saved ->
                Repo.init(saved)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            }
        }

        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                mode = if (tab.position == 0) LoginMode.XTREAM else LoginMode.M3U
                b.llXtream.visibility = if (mode == LoginMode.XTREAM) android.view.View.VISIBLE else android.view.View.GONE
                b.llM3u.visibility = if (mode == LoginMode.M3U) android.view.View.VISIBLE else android.view.View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        b.btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val name = b.etName.text.toString().trim()
        val session: Session = if (mode == LoginMode.XTREAM) {
            val server = b.etServer.text.toString().trim()
            val user = b.etUser.text.toString().trim()
            val pass = b.etPass.text.toString().trim()
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                toast(getString(R.string.err_fields)); return
            }
            Session(LoginMode.XTREAM, server = server, username = user, password = pass, name = name)
        } else {
            val url = b.etM3u.text.toString().trim()
            if (url.isEmpty()) { toast(getString(R.string.err_fields)); return }
            Session(LoginMode.M3U, m3uUrl = url, name = name)
        }

        setLoading(true)
        Repo.init(session)
        lifecycleScope.launch {
            try {
                Repo.connect()
                SessionStore.save(this@LoginActivity, session, b.cbRemember.isChecked)
                PlaylistStore.add(this@LoginActivity, session)
                ContentCache.clearAll()
                startActivity(Intent(this@LoginActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } catch (e: Exception) {
                setLoading(false)
                val msg = when (e) {
                    is java.net.SocketTimeoutException,
                    is java.io.InterruptedIOException ->
                        "انتهت مهلة الاتصال — السيرفر بطيء أو القائمة كبيرة، جرّب مرة ثانية"
                    is java.net.UnknownHostException ->
                        "ما كدرنا نوصل للسيرفر — تأكد من الرابط والإنترنت"
                    else -> e.message ?: getString(R.string.err_login)
                }
                Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        b.progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        b.btnLogin.isEnabled = !loading
        b.btnLogin.alpha = if (loading) 0.6f else 1f
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_ADD = "add_mode"
    }
}
