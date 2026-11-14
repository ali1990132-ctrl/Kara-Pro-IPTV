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
    private var pairing: com.karastore.iptv.util.PairingServer? = null
    private var pairingDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val addMode = intent.getBooleanExtra(EXTRA_ADD, false)
        // رابط QR وارد (نقل اشتراك من جهاز ثاني)؟ عبّي الحقول بدل الدخول التلقائي
        val incoming = com.karastore.iptv.util.QrShare.parse(intent?.data)

        // دخول تلقائي إذا كانت الجلسة محفوظة (إلا بوضع "إضافة قائمة" أو رابط وارد)
        if (!addMode && incoming == null) {
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
        b.btnQrShare.setOnClickListener { showQr() }

        // رابط وارد: عبّي الحقول بعد تجهيز المستمعين
        incoming?.let { prefill(it) }
    }

    // سيرفرات النظام (سيرفر 1-5) — يختار النظام المطابق تلقائياً
    private val presetServers = arrayOf(
        "http://ktvs.xyz:8080",
        "http://karaali.online:2052",
        "http://8ksh1856.site:8080",
        "http://renvioweb.online:8080",
        "http://amlive.cc:80"
    )

    private fun attemptLogin() {
        val name = b.etName.text.toString().trim()
        val sessions: List<Session> = if (mode == LoginMode.XTREAM) {
            val user = b.etUser.text.toString().trim()
            val pass = b.etPass.text.toString().trim()
            if (user.isEmpty() || pass.isEmpty()) { toast(getString(R.string.err_fields)); return }
            // سيرفر محدّد (من باركود) وإلا جرّب كل سيرفرات النظام
            val typed = b.etServer.text.toString().trim()
            val servers = if (typed.isNotEmpty()) listOf(typed) else presetServers.toList()
            servers.map { Session(LoginMode.XTREAM, server = it, username = user, password = pass, name = name) }
        } else {
            val url = b.etM3u.text.toString().trim()
            if (url.isEmpty()) { toast(getString(R.string.err_fields)); return }
            listOf(Session(LoginMode.M3U, m3uUrl = url, name = name))
        }
        connectSessions(sessions)
    }

    /** يجرّب الجلسات بالترتيب ويتصل بأول سيرفر مطابق */
    private fun connectSessions(sessions: List<Session>) {
        setLoading(true)
        lifecycleScope.launch {
            var connected: Session? = null
            var lastErr: Exception? = null
            for ((i, s) in sessions.withIndex()) {
                if (sessions.size > 1) toast(getString(R.string.trying_server, i + 1))
                Repo.init(s)
                try { Repo.connect(); connected = s; break }
                catch (e: Exception) { lastErr = e }
            }
            if (connected == null) {
                setLoading(false)
                val msg = when (lastErr) {
                    is java.net.SocketTimeoutException,
                    is java.io.InterruptedIOException ->
                        "انتهت مهلة الاتصال — السيرفر بطيء، جرّب مرة ثانية"
                    is java.net.UnknownHostException ->
                        "ما كدرنا نوصل للسيرفر — تأكد من الإنترنت"
                    else -> getString(R.string.err_login)
                }
                Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                return@launch
            }
            SessionStore.save(this@LoginActivity, connected, b.cbRemember.isChecked)
            PlaylistStore.add(this@LoginActivity, connected)
            ContentCache.clearAll()
            startActivity(Intent(this@LoginActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    /** يعبّي الحقول من جلسة واردة (QR) ويختار التبويب المناسب */
    private fun prefill(s: Session) {
        if (s.mode == LoginMode.XTREAM) {
            b.tabs.getTabAt(0)?.select()
            b.etServer.setText(s.server)
            b.etUser.setText(s.username)
            b.etPass.setText(s.password)
        } else {
            b.tabs.getTabAt(1)?.select()
            b.etM3u.setText(s.m3uUrl)
        }
        if (s.name.isNotBlank()) b.etName.setText(s.name)
    }

    /** يشغّل صفحة الإدخال المحلية ويعرض باركودها ليمسحه الموبايل */
    private fun showQr() {
        val ip = com.karastore.iptv.util.PairingServer.localIp(this)
        if (ip == null) { toast(getString(R.string.qr_need_wifi)); return }

        // أوقف أي سيرفر سابق ثم شغّل واحداً جديداً
        stopPairing()
        val server = com.karastore.iptv.util.PairingServer(
            com.karastore.iptv.util.PairingServer.PORT
        ) { session ->
            runOnUiThread {
                pairingDialog?.dismiss()
                stopPairing()
                prefill(session)
                attemptLogin()
            }
        }
        try {
            server.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        } catch (e: Exception) {
            toast(e.message ?: "server error"); return
        }
        pairing = server

        val url = "http://$ip:${com.karastore.iptv.util.PairingServer.PORT}/"
        val bmp = try { com.karastore.iptv.util.QrShare.bitmap(url) }
        catch (e: Exception) { toast(e.message ?: "QR error"); stopPairing(); return }

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(px(20), px(20), px(20), px(8))
        }
        val title = android.widget.TextView(this).apply {
            text = getString(R.string.qr_title)
            textSize = 16f; setTextColor(0xFF0B1220.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, px(14))
        }
        val img = android.widget.ImageView(this).apply {
            setImageBitmap(bmp)
            layoutParams = android.widget.LinearLayout.LayoutParams(px(240), px(240))
        }
        val steps = android.widget.TextView(this).apply {
            text = getString(R.string.qr_steps)
            textSize = 13f; setTextColor(0xFF33415A.toInt())
            setPadding(0, px(14), 0, px(10))
        }
        val hint = android.widget.TextView(this).apply {
            text = getString(R.string.qr_open_hint)
            textSize = 12f; setTextColor(0xFF5A6472.toInt())
            gravity = android.view.Gravity.CENTER
        }
        val link = android.widget.TextView(this).apply {
            text = url
            textSize = 16f; setTextColor(0xFF1E9BFF.toInt())
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, px(2), 0, 0)
        }
        container.addView(title); container.addView(img)
        container.addView(steps); container.addView(hint); container.addView(link)

        pairingDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(container)
            .setOnDismissListener { stopPairing() }
            .setPositiveButton(getString(R.string.qr_close), null)
            .show()
    }

    private fun stopPairing() {
        try { pairing?.stop() } catch (_: Exception) {}
        pairing = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPairing()
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
