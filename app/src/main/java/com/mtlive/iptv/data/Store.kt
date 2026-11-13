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

/** إعدادات التطبيق (نفس إعدادات A&S) */
object Settings {
    private const val PREF = "mt_settings"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** وضع التوافقية الأعلى بالمشغّل */
    fun compatMode(ctx: Context) = p(ctx).getBoolean("engine_compat", false)
    fun setCompatMode(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("engine_compat", v).apply()

    fun pin(ctx: Context): String = p(ctx).getString("pin", "0000") ?: "0000"
    fun setPin(ctx: Context, v: String) = p(ctx).edit().putString("pin", v).apply()

    /** إظهار الشعار على البث */
    fun watermark(ctx: Context) = p(ctx).getBoolean("watermark", true)
    fun setWatermark(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("watermark", v).apply()

    /** تشغيل القناة بالمربع الصغير قبل الشاشة الكاملة */
    fun livePreview(ctx: Context) = p(ctx).getBoolean("live_preview", true)
    fun setLivePreview(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("live_preview", v).apply()
}

/** باقات مقفولة أو مخفية لكل نوع */
class CategoryFlagStore(private val pref: String) {
    private fun key(t: ContentType) = "${pref}_${t.name}"
    fun get(ctx: Context, t: ContentType): Set<String> =
        ctx.getSharedPreferences(pref, Context.MODE_PRIVATE).getStringSet(key(t), emptySet())?.toSet() ?: emptySet()
    fun has(ctx: Context, t: ContentType, id: String) = id in get(ctx, t)
    fun set(ctx: Context, t: ContentType, id: String, on: Boolean) {
        val s = get(ctx, t).toMutableSet()
        if (on) s.add(id) else s.remove(id)
        ctx.getSharedPreferences(pref, Context.MODE_PRIVATE).edit().putStringSet(key(t), s).apply()
    }
    fun clearAll(ctx: Context) = ctx.getSharedPreferences(pref, Context.MODE_PRIVATE).edit().clear().apply()
}

val LockedCategories = CategoryFlagStore("mt_locked")
val HiddenCategories = CategoryFlagStore("mt_hidden")

/** سجل المشاهدة */
object HistoryStore {
    private const val PREF = "mt_history"
    private const val MAX = 100
    private val gson = com.google.gson.Gson()

    fun all(ctx: Context): MutableList<ContentItem> {
        val json = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("list", null) ?: return mutableListOf()
        val t = object : com.google.gson.reflect.TypeToken<MutableList<ContentItem>>() {}.type
        return try { gson.fromJson(json, t) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
    }

    fun add(ctx: Context, item: ContentItem) {
        val l = all(ctx)
        l.removeAll { it.id == item.id && it.type == item.type }
        l.add(0, item)
        while (l.size > MAX) l.removeAt(l.size - 1)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("list", gson.toJson(l)).apply()
    }

    fun clear(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
}

/** الحسابات المحفوظة (قوائم التشغيل) */
data class SavedAccount(val host: String, val username: String, val password: String)

object AccountsStore {
    private const val PREF = "mt_accounts"
    private val gson = com.google.gson.Gson()

    fun all(ctx: Context): MutableList<SavedAccount> {
        val json = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("list", null) ?: return mutableListOf()
        val t = object : com.google.gson.reflect.TypeToken<MutableList<SavedAccount>>() {}.type
        return try { gson.fromJson(json, t) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
    }

    fun add(ctx: Context, a: SavedAccount) {
        val l = all(ctx)
        l.removeAll { it.host == a.host && it.username == a.username }
        l.add(0, a)
        save(ctx, l)
    }

    fun remove(ctx: Context, a: SavedAccount) {
        val l = all(ctx)
        l.removeAll { it.host == a.host && it.username == a.username }
        save(ctx, l)
    }

    private fun save(ctx: Context, l: List<SavedAccount>) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("list", gson.toJson(l)).apply()
}
