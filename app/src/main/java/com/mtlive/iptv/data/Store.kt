package com.mtlive.iptv.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Session(
    val host: String,
    val username: String,
    val password: String,
    val expDate: String? = null,
    val activeCons: String? = null,
    val maxCons: String? = null
) {
    fun api() = XtreamApi(host, username, password)

    /** تاريخ الانتهاء بصيغة مقروءة، أو null إذا غير محدود */
    fun expiryText(): String? {
        val ts = expDate?.toLongOrNull() ?: return null
        if (ts <= 0) return null
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(ts * 1000))
    }
}

object SessionStore {
    private const val PREF = "mt_session"

    fun save(ctx: Context, s: Session) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("host", s.host).putString("user", s.username).putString("pass", s.password)
            .putString("exp", s.expDate).putString("ac", s.activeCons).putString("mc", s.maxCons)
            .apply()
    }

    fun load(ctx: Context): Session? {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val host = p.getString("host", null) ?: return null
        val user = p.getString("user", null) ?: return null
        val pass = p.getString("pass", null) ?: return null
        return Session(host, user, pass, p.getString("exp", null), p.getString("ac", null), p.getString("mc", null))
    }

    /** آخر بيانات دخول (حتى بعد تسجيل الخروج) لتعبئة الحقول */
    fun lastHost(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("last_host", "") ?: ""
    fun lastUser(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("last_user", "") ?: ""

    fun rememberLast(ctx: Context, host: String, user: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("last_host", host).putString("last_user", user).apply()
    }

    fun clear(ctx: Context) {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val h = p.getString("last_host", ""); val u = p.getString("last_user", "")
        p.edit().clear().putString("last_host", h).putString("last_user", u).apply()
        Cache.clear()
    }
}

/** ذاكرة مؤقتة للمحتوى طوال تشغيل التطبيق */
object Cache {
    var session: Session? = null
    private val cats = HashMap<ContentType, List<Category>>()
    private val items = HashMap<ContentType, List<ContentItem>>()

    fun categories(t: ContentType) = cats[t]
    fun items(t: ContentType) = items[t]
    fun put(t: ContentType, c: List<Category>, i: List<ContentItem>) { cats[t] = c; items[t] = i }
    fun clear() { cats.clear(); items.clear() }

    suspend fun load(t: ContentType, force: Boolean = false): Pair<List<Category>, List<ContentItem>> {
        if (!force) {
            val c = cats[t]; val i = items[t]
            if (c != null && i != null) return c to i
        }
        val api = session?.api() ?: throw RuntimeException("سجّل الدخول من جديد")
        val c = api.categories(t)
        val i = api.items(t)
        put(t, c, i)
        return c to i
    }
}

object Favorites {
    private const val PREF = "mt_fav"
    private fun key(t: ContentType) = "fav_${t.name}"

    fun ids(ctx: Context, t: ContentType): Set<String> =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet(key(t), emptySet()) ?: emptySet()

    fun isFav(ctx: Context, t: ContentType, id: String) = id in ids(ctx, t)

    fun toggle(ctx: Context, t: ContentType, id: String): Boolean {
        val s = ids(ctx, t).toMutableSet()
        val now = if (!s.remove(id)) { s.add(id); true } else false
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putStringSet(key(t), s).apply()
        return now
    }
}
