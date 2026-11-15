package com.asvpn.player.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Link(val title: String = "", val url: String = "", val group: String = "")

data class Playlist(val id: Long = 0, var name: String = "", var items: MutableList<Link>? = mutableListOf())

object Store {
    private lateinit var sp: SharedPreferences
    private val gson = Gson()

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("asvpn", Context.MODE_PRIVATE)
    }

    fun str(key: String): String = sp.getString(key, "") ?: ""
    fun putStr(key: String, value: String) = sp.edit().putString(key, value).apply()

    fun playlists(): MutableList<Playlist> = try {
        gson.fromJson<MutableList<Playlist>>(
            sp.getString("playlists", "[]"),
            object : TypeToken<MutableList<Playlist>>() {}.type
        ) ?: mutableListOf()
    } catch (e: Exception) {
        mutableListOf()
    }

    fun savePlaylists(list: List<Playlist>) = sp.edit().putString("playlists", gson.toJson(list)).apply()

    fun history(): MutableList<Link> = try {
        gson.fromJson<MutableList<Link>>(
            sp.getString("history", "[]"),
            object : TypeToken<MutableList<Link>>() {}.type
        ) ?: mutableListOf()
    } catch (e: Exception) {
        mutableListOf()
    }

    fun addHistory(link: Link) {
        val h = history()
        h.removeAll { it.url == link.url }
        h.add(0, link)
        sp.edit().putString("history", gson.toJson(h.take(50))).apply()
    }

    fun clearHistory() = sp.edit().remove("history").apply()

    fun position(url: String): Long = sp.getLong("pos_" + url.hashCode(), 0L)
    fun savePosition(url: String, pos: Long) = sp.edit().putLong("pos_" + url.hashCode(), pos).apply()

    var subSize: Int
        get() = sp.getInt("sub_size", 20)
        set(v) = sp.edit().putInt("sub_size", v).apply()

    var subColor: Int
        get() = sp.getInt("sub_color", Color.WHITE)
        set(v) = sp.edit().putInt("sub_color", v).apply()

    var subBg: Boolean
        get() = sp.getBoolean("sub_bg", false)
        set(v) = sp.edit().putBoolean("sub_bg", v).apply()

    var resume: Boolean
        get() = sp.getBoolean("resume", true)
        set(v) = sp.edit().putBoolean("resume", v).apply()

    /** 0 = WARP, 1 = custom WireGuard config */
    var vpnMode: Int
        get() = sp.getInt("vpn_mode", 0)
        set(v) = sp.edit().putInt("vpn_mode", v).apply()
}

object Net {
    const val UA = "ASVPN/1.0 (Linux; Android)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
            return r.body?.string() ?: ""
        }
    }
}

object M3u {
    private val groupRx = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Link> {
        val out = mutableListOf<Link>()
        var title: String? = null
        var group = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    title = line.substringAfterLast(",").trim()
                    group = groupRx.find(line)?.groupValues?.get(1) ?: ""
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val t = title
                    val name = if (!t.isNullOrBlank()) t else line.substringAfterLast('/').substringBefore('?')
                    out.add(Link(name, line, group))
                    title = null
                    group = ""
                }
            }
        }
        return out
    }
}
