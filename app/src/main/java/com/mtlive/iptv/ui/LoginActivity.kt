package com.mtlive.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mtlive.iptv.R
import com.mtlive.iptv.data.AccountsStore
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.SavedAccount
import com.mtlive.iptv.data.Session
import com.mtlive.iptv.data.SessionStore
import com.mtlive.iptv.data.XtreamApi
import com.mtlive.iptv.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()

        b.etUser.setText(SessionStore.lastUser(this))

        b.btnLogin.setOnClickListener { doLogin() }
        b.etPass.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false
        }
    }

    private fun doLogin() {
        val user = b.etUser.text.toString().trim()
        val pass = b.etPass.text.toString().trim()
        if (user.isEmpty() || pass.isEmpty()) {
            showError(getString(R.string.fill_all)); return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                // يجرّب كل السيرفرات المسموحة ويختار اللي يقبل الحساب
                val (normalized, info) = XtreamApi.autoLogin(user, pass)
                val s = Session(normalized, user, pass, info.expDate, info.activeCons, info.maxConnections)
                SessionStore.save(this@LoginActivity, s)
                SessionStore.rememberLast(this@LoginActivity, normalized, user)
                AccountsStore.add(this@LoginActivity, SavedAccount(normalized, user, pass))
                Cache.clear()
                Cache.session = s
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                finish()
            } catch (e: Exception) {
                setLoading(false)
                val msg = if (e is java.io.IOException) "تعذّر الاتصال بالسيرفر — تأكد من الرابط والإنترنت"
                          else e.message ?: "خطأ بالاتصال — تأكد من الرابط"
                showError(msg)
            }
        }
    }

    private fun setLoading(on: Boolean) {
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.btnLogin.text = if (on) "" else getString(R.string.login)
        b.btnLogin.isEnabled = !on
        if (on) b.tvError.visibility = View.GONE
    }

    private fun showError(msg: String) {
        b.tvError.text = msg
        b.tvError.visibility = View.VISIBLE
    }
}
