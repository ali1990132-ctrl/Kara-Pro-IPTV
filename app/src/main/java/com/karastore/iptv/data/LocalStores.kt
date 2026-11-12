package com.karastore.iptv.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.karastore.iptv.data.model.ContentItem

private val gson = Gson()
private fun prefs(ctx: Context) =
    ctx.getSharedPreferences("kara_local", Context.MODE_PRIVATE)

private fun readList(ctx: Context, key: String): MutableList<ContentItem> {
    val json = prefs(ctx).getString(key, null) ?: return mutableListOf()
    val type = object : TypeToken<MutableList<ContentItem>>() {}.type
    return try { gson.fromJson(json, type) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
}

private fun writeList(ctx: Context, key: String, list: List<ContentItem>) {
    prefs(ctx).edit().putString(key, gson.toJson(list)).apply()
}

/** المفضلة */
object FavoritesStore {
    private const val KEY = "favorites"

    fun all(ctx: Context): List<ContentItem> = readList(ctx, KEY)

    fun isFav(ctx: Context, id: String): Boolean = readList(ctx, KEY).any { it.id == id }

    fun toggle(ctx: Context, item: ContentItem): Boolean {
        val list = readList(ctx, KEY)
        val existing = list.indexOfFirst { it.id == item.id }
        val nowFav: Boolean
        if (existing >= 0) { list.removeAt(existing); nowFav = false }
        else { list.add(0, item); nowFav = true }
        writeList(ctx, KEY, list)
        return nowFav
    }
}

/** سجل المشاهدة */
object HistoryStore {
    private const val KEY = "history"
    private const val MAX = 100

    fun all(ctx: Context): List<ContentItem> = readList(ctx, KEY)

    fun add(ctx: Context, item: ContentItem) {
        val list = readList(ctx, KEY)
        list.removeAll { it.id == item.id }
        list.add(0, item)
        while (list.size > MAX) list.removeAt(list.size - 1)
        writeList(ctx, KEY, list)
    }

    fun clear(ctx: Context) = writeList(ctx, KEY, emptyList())
}

/** التنزيلات المكتملة */
object DownloadsStore {
    private const val KEY = "downloads"

    fun all(ctx: Context): List<ContentItem> = readList(ctx, KEY)

    fun add(ctx: Context, item: ContentItem) {
        val list = readList(ctx, KEY)
        if (list.none { it.id == item.id }) { list.add(0, item); writeList(ctx, KEY, list) }
    }

    /** يضيف أو يحدّث عنصراً (لتحديث المسار المحلي بعد اكتمال التحميل) */
    fun upsert(ctx: Context, item: ContentItem) {
        val list = readList(ctx, KEY)
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(0, item)
        writeList(ctx, KEY, list)
    }

    fun remove(ctx: Context, id: String) {
        val list = readList(ctx, KEY)
        list.removeAll { it.id == id }
        writeList(ctx, KEY, list)
    }
}

/** التصنيفات/الباقات المخفية لكل نوع */
object HiddenCategoriesStore {
    private const val PREF = "kara_hidden"
    private fun key(type: com.karastore.iptv.data.model.ContentType) = "hidden_${type.name}"

    fun get(ctx: Context, type: com.karastore.iptv.data.model.ContentType): Set<String> =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet(key(type), emptySet())?.toSet() ?: emptySet()

    fun isHidden(ctx: Context, type: com.karastore.iptv.data.model.ContentType, id: String?): Boolean =
        id != null && get(ctx, type).contains(id)

    fun setHidden(ctx: Context, type: com.karastore.iptv.data.model.ContentType, id: String, hidden: Boolean) {
        val set = get(ctx, type).toMutableSet()
        if (hidden) set.add(id) else set.remove(id)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putStringSet(key(type), set).apply()
    }
}

/** إعدادات التطبيق */
object Settings {
    private const val PREF = "kara_settings"
    private const val K_ENGINE_COMPAT = "engine_compat"

    /** وضع التوافقية الأعلى في المشغّل */
    fun compatMode(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(K_ENGINE_COMPAT, false)

    fun setCompatMode(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(K_ENGINE_COMPAT, v).apply()
}
