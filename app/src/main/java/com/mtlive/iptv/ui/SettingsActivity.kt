package com.mtlive.iptv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.mtlive.iptv.R
import com.mtlive.iptv.data.AccountsStore
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.ContentType
import com.mtlive.iptv.data.HiddenCategories
import com.mtlive.iptv.data.HistoryStore
import com.mtlive.iptv.data.Session
import com.mtlive.iptv.data.SessionStore
import com.mtlive.iptv.data.Settings
import com.mtlive.iptv.databinding.ActivitySettingsBinding
import com.mtlive.iptv.databinding.ItemSettingBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()
        setupTopbar(b.topbar, Tabs.SETTINGS)
        bindAccount()
        b.btnSupport.setOnClickListener { openSupport() }
        buildRows()
    }

    private fun bindAccount() {
        val s = Cache.session ?: return
        b.tvUser.text = s.username
        b.tvHost.text = com.mtlive.iptv.data.XtreamApi.serverLabel(s.host)
        b.tvExpiryBig.text = getString(R.string.expires, s.expiryText() ?: getString(R.string.unlimited))
        val exp = s.expDate?.toLongOrNull()
        if (exp != null && exp > 0) {
            val days = Math.ceil((exp * 1000 - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0)
            b.tvRemaining.text = getString(R.string.remaining, days)
            b.tvRemaining.visibility = View.VISIBLE
        } else b.tvRemaining.visibility = View.GONE
        b.tvCons.text = if (s.maxCons != null) getString(R.string.connections, s.activeCons ?: "0", s.maxCons) else ""
    }

    private fun row(icon: Int, title: Int, value: String = "", onClick: (ItemSettingBinding) -> Unit): ItemSettingBinding {
        val r = ItemSettingBinding.inflate(layoutInflater, b.rows, false)
        r.icon.setImageResource(icon)
        r.tvTitle.setText(title)
        r.tvValue.text = value
        r.root.setOnClickListener { onClick(r) }
        b.rows.addView(r.root)
        return r
    }

    private fun onOff(v: Boolean) = getString(if (v) R.string.on else R.string.off)

    private fun buildRows() {
        b.rows.removeAllViews()

        row(R.drawable.ic_star, R.string.favorites) { pickFavorites() }
        row(R.drawable.ic_history, R.string.history) { showHistory() }
        row(R.drawable.ic_refresh, R.string.refresh_content, countsText() ?: "") { r -> refreshContent(r) }
        row(R.drawable.ic_accounts, R.string.accounts, AccountsStore.all(this).size.toString()) { showAccounts() }
        row(R.drawable.ic_language, R.string.language, currentLangLabel()) { pickLanguage() }
        row(R.drawable.ic_engine, R.string.engine,
            getString(if (Settings.compatMode(this)) R.string.engine_compat else R.string.engine_auto)) { r -> pickEngine(r) }
        row(R.drawable.ic_preview, R.string.live_preview, onOff(Settings.livePreview(this))) { r ->
            Settings.setLivePreview(this, !Settings.livePreview(this))
            r.tvValue.text = onOff(Settings.livePreview(this))
        }
        row(R.drawable.ic_movie, R.string.watermark, onOff(Settings.watermark(this))) { r ->
            Settings.setWatermark(this, !Settings.watermark(this))
            r.tvValue.text = onOff(Settings.watermark(this))
        }
        row(R.drawable.ic_lock, R.string.change_pin, getString(R.string.pin_hint)) { changePin() }
        row(R.drawable.ic_visibility, R.string.show_hidden) {
            HiddenCategories.clearAll(this)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        }
        row(R.drawable.ic_delete, R.string.clear_history) {
            HistoryStore.clear(this)
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        }
        val up = row(R.drawable.ic_update, R.string.tile_update, getString(R.string.tile_update_sub, Updater.currentName)) { r ->
            Updater.manualCheck(this, lifecycleScope) { info ->
                r.tvValue.text = if (Updater.hasUpdate(info)) getString(R.string.tile_update_new)
                                 else getString(R.string.tile_update_sub, Updater.currentName)
            }
        }
        if (Updater.hasUpdate(Updater.latest)) up.tvValue.text = getString(R.string.tile_update_new)
        row(R.drawable.ic_logout, R.string.logout) { confirmLogout() }

        b.rows.getChildAt(0)?.requestFocus()
    }

    private fun countsText(): String? {
        val l = Cache.items(ContentType.LIVE)?.size ?: return null
        val m = Cache.items(ContentType.MOVIE)?.size ?: return null
        val s = Cache.items(ContentType.SERIES)?.size ?: return null
        return getString(R.string.counts_fmt, l, m, s)
    }

    private fun refreshContent(r: ItemSettingBinding) {
        r.progress.visibility = View.VISIBLE
        r.tvValue.text = getString(R.string.loading)
        lifecycleScope.launch {
            try {
                Cache.clear()
                val l = Cache.load(ContentType.LIVE, true).second.size
                val m = Cache.load(ContentType.MOVIE, true).second.size
                val s = Cache.load(ContentType.SERIES, true).second.size
                r.tvValue.text = getString(R.string.counts_fmt, l, m, s)
            } catch (e: Exception) {
                r.tvValue.text = getString(R.string.load_error, e.message ?: "")
            } finally {
                r.progress.visibility = View.GONE
            }
        }
    }

    private fun pickFavorites() {
        val labels = arrayOf(getString(R.string.live), getString(R.string.movies), getString(R.string.series))
        AlertDialog.Builder(this)
            .setTitle(R.string.favorites)
            .setItems(labels) { _, w ->
                val t = when (w) { 0 -> ContentType.LIVE; 1 -> ContentType.MOVIE; else -> ContentType.SERIES }
                startActivity(SectionActivity.intent(this, t, SectionActivity.KEY_FAV))
            }.show()
    }

    private fun showHistory() {
        val list = HistoryStore.all(this)
        if (list.isEmpty()) { Toast.makeText(this, R.string.empty_history, Toast.LENGTH_SHORT).show(); return }
        val names = list.map { item ->
            val tag = when (item.type) {
                ContentType.LIVE -> getString(R.string.live)
                ContentType.MOVIE -> getString(R.string.movies)
                else -> getString(R.string.series)
            }
            "${item.name}  •  $tag"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.history)
            .setItems(names) { _, w ->
                PlayerQueue.set(listOf(list[w]), 0)
                startActivity(Intent(this, PlayerActivity::class.java))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAccounts() {
        val accounts = AccountsStore.all(this).filter { com.mtlive.iptv.data.XtreamApi.isAllowed(it.host) }
        val current = Cache.session
        val labels = accounts.map { a ->
            val mark = if (current != null && a.host == current.host && a.username == current.username) "✓  " else ""
            "$mark${a.username}  —  ${com.mtlive.iptv.data.XtreamApi.serverLabel(a.host)}"
        }.toMutableList()
        labels.add("＋  " + getString(R.string.add_account))
        AlertDialog.Builder(this)
            .setTitle(R.string.accounts)
            .setItems(labels.toTypedArray()) { _, w ->
                if (w == accounts.size) {
                    // حساب جديد: نرجع لشاشة الدخول بدون حذف الحسابات المحفوظة
                    SessionStore.clear(this)
                    Cache.session = null
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                } else {
                    val a = accounts[w]
                    AlertDialog.Builder(this)
                        .setTitle(a.username)
                        .setPositiveButton(R.string.login) { _, _ -> switchAccount(a.host, a.username, a.password) }
                        .setNeutralButton(R.string.delete) { _, _ -> AccountsStore.remove(this, a); buildRows() }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }.show()
    }

    private fun switchAccount(host: String, user: String, pass: String) {
        lifecycleScope.launch {
            try {
                val info = com.mtlive.iptv.data.XtreamApi(host, user, pass).login()
                val s = Session(host, user, pass, info.expDate, info.activeCons, info.maxConnections)
                SessionStore.save(this@SettingsActivity, s)
                SessionStore.rememberLast(this@SettingsActivity, host, user)
                Cache.clear()
                Cache.session = s
                startActivity(Intent(this@SettingsActivity, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, e.message ?: "خطأ بالاتصال", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun currentLangLabel(): String =
        when (AppCompatDelegate.getApplicationLocales().toLanguageTags().lowercase().take(2)) {
            "ar" -> getString(R.string.lang_ar)
            "en" -> getString(R.string.lang_en)
            else -> getString(R.string.lang_device)
        }

    private fun pickLanguage() {
        val labels = arrayOf(getString(R.string.lang_device), getString(R.string.lang_ar), getString(R.string.lang_en))
        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setItems(labels) { _, w ->
                AppCompatDelegate.setApplicationLocales(
                    when (w) {
                        1 -> LocaleListCompat.forLanguageTags("ar")
                        2 -> LocaleListCompat.forLanguageTags("en")
                        else -> LocaleListCompat.getEmptyLocaleList()
                    }
                )
            }.show()
    }

    private fun pickEngine(r: ItemSettingBinding) {
        val labels = arrayOf(getString(R.string.engine_auto), getString(R.string.engine_compat))
        AlertDialog.Builder(this)
            .setTitle(R.string.engine)
            .setSingleChoiceItems(labels, if (Settings.compatMode(this)) 1 else 0) { d, w ->
                Settings.setCompatMode(this, w == 1)
                r.tvValue.text = labels[w]
                d.dismiss()
            }.show()
    }

    private fun pinInput(hint: Int) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        setHint(hint)
    }

    private fun changePin() {
        val cur = pinInput(R.string.pin_current)
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_current)
            .setView(cur)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (cur.text.toString() != Settings.pin(this)) {
                    Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val neu = pinInput(R.string.pin_new)
                AlertDialog.Builder(this)
                    .setTitle(R.string.pin_new)
                    .setView(neu)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val v = neu.text.toString().trim()
                        if (v.length in 3..8) {
                            Settings.setPin(this, v)
                            Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(this, R.string.pin_new, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openSupport() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + getString(R.string.support_wa))))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.support_number), Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmLogout() {
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
}
