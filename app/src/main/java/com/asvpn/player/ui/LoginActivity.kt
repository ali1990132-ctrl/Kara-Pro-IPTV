package com.asvpn.player.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asvpn.player.R
import com.asvpn.player.data.Cache
import com.asvpn.player.data.Session
import com.asvpn.player.data.SessionStore
import com.asvpn.player.data.XtreamApi
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var user: EditText
    private lateinit var pass: EditText
    private lateinit var btn: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SessionStore.load(this) != null) { goMain(); return }
        setContentView(R.layout.activity_login)
        user = findViewById(R.id.etUser)
        pass = findViewById(R.id.etPass)
        btn = findViewById(R.id.btnLogin)
        progress = findViewById(R.id.progress)
        error = findViewById(R.id.tvError)

        user.setText(SessionStore.lastUser(this))
        btn.setOnClickListener { doLogin() }
        pass.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false
        }
        findViewById<View>(R.id.btnVpn).setOnClickListener { HostActivity.open(this, HostActivity.VPN) }
        findViewById<View>(R.id.btnLinks).setOnClickListener { HostActivity.open(this, HostActivity.LINKS) }
    }

    private fun doLogin() {
        val u = user.text.toString().trim()
        val p = pass.text.toString().trim()
        if (u.isEmpty() || p.isEmpty()) { showError("اكتب اسم المستخدم وكلمة المرور"); return }
        setLoading(true)
        lifecycleScope.launch {
            try {
                val (host, info) = XtreamApi.autoLogin(u, p)
                val s = Session(host, u, p, info.status, info.expDate)
                SessionStore.save(this@LoginActivity, s)
                Cache.clear()
                Cache.session = s
                goMain()
            } catch (e: Exception) {
                setLoading(false)
                showError(
                    if (e is java.io.IOException) "تعذّر الاتصال بالسيرفر — جرّب تشغيل الـ VPN"
                    else e.message ?: "خطأ بالاتصال"
                )
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(on: Boolean) {
        progress.visibility = if (on) View.VISIBLE else View.GONE
        btn.text = if (on) "" else "دخول"
        btn.isEnabled = !on
        if (on) error.visibility = View.GONE
    }

    private fun showError(msg: String) {
        error.text = msg
        error.visibility = View.VISIBLE
    }
}
