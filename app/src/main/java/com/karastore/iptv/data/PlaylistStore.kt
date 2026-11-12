package com.karastore.iptv.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** قوائم/اشتراكات متعددة محفوظة على الجهاز */
object PlaylistStore {
    private const val PREF = "kara_playlists"
    private const val K_LIST = "list"
    private val gson = Gson()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun all(ctx: Context): MutableList<Session> {
        val json = prefs(ctx).getString(K_LIST, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Session>>() {}.type
        return try { gson.fromJson(json, type) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
    }

    private fun save(ctx: Context, list: List<Session>) {
        prefs(ctx).edit().putString(K_LIST, gson.toJson(list)).apply()
    }

    private fun keyOf(s: Session): String = when (s.mode) {
        LoginMode.XTREAM -> "x:${s.server}|${s.username}"
        LoginMode.M3U -> "m:${s.m3uUrl}"
    }

    /** يضيف قائمة (أو يحدّثها إن كانت موجودة) */
    fun add(ctx: Context, session: Session) {
        val list = all(ctx)
        val idx = list.indexOfFirst { keyOf(it) == keyOf(session) }
        if (idx >= 0) list[idx] = session else list.add(session)
        save(ctx, list)
    }

    fun remove(ctx: Context, session: Session) {
        val list = all(ctx)
        list.removeAll { keyOf(it) == keyOf(session) }
        save(ctx, list)
    }

    fun isActive(active: Session?, s: Session): Boolean =
        active != null && keyOf(active) == keyOf(s)
}
