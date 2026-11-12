package com.karastore.iptv.data

import android.content.Context

enum class LoginMode { XTREAM, M3U }

/** بيانات جلسة المستخدم المحفوظة محلياً */
data class Session(
    val mode: LoginMode,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val name: String = ""
)

object SessionStore {
    private const val PREF = "kara_session"
    private const val K_MODE = "mode"
    private const val K_SERVER = "server"
    private const val K_USER = "user"
    private const val K_PASS = "pass"
    private const val K_M3U = "m3u"
    private const val K_NAME = "name"
    private const val K_REMEMBER = "remember"

    fun save(ctx: Context, s: Session, remember: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
            putString(K_MODE, s.mode.name)
            putString(K_SERVER, s.server)
            putString(K_USER, s.username)
            putString(K_PASS, s.password)
            putString(K_M3U, s.m3uUrl)
            putString(K_NAME, s.name)
            putBoolean(K_REMEMBER, remember)
            apply()
        }
    }

    fun load(ctx: Context): Session? {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.getBoolean(K_REMEMBER, false)) return null
        val mode = p.getString(K_MODE, null) ?: return null
        return Session(
            mode = LoginMode.valueOf(mode),
            server = p.getString(K_SERVER, "") ?: "",
            username = p.getString(K_USER, "") ?: "",
            password = p.getString(K_PASS, "") ?: "",
            m3uUrl = p.getString(K_M3U, "") ?: "",
            name = p.getString(K_NAME, "") ?: ""
        )
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
