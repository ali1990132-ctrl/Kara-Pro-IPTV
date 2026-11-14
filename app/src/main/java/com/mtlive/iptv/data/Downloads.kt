package com.mtlive.iptv.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** عنصر بقائمة التنزيلات */
data class DownloadRec(
    val key: String,          // نوع + رقم
    val title: String,
    val image: String?,
    val type: ContentType,
    val url: String,
    val dmId: Long,
    val path: String,
    val added: Long = System.currentTimeMillis()
)

data class DownloadState(val status: Int, val percent: Int)

/** التنزيلات: تنزل بخدمة التنزيل مال أندرويد وتنحفظ داخل ملفات التطبيق */
object Downloads {
    private const val PREF = "mt_downloads"
    private val gson = Gson()

    fun all(ctx: Context): MutableList<DownloadRec> {
        val json = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("list", null) ?: return mutableListOf()
        val t = object : TypeToken<MutableList<DownloadRec>>() {}.type
        return try { gson.fromJson(json, t) ?: mutableListOf() } catch (e: Exception) { mutableListOf() }
    }

    private fun save(ctx: Context, l: List<DownloadRec>) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("list", gson.toJson(l)).apply()

    fun keyOf(item: ContentItem) = "${item.type.name}_${item.id}"

    fun exists(ctx: Context, item: ContentItem) = all(ctx).any { it.key == keyOf(item) }

    /** يبدي التنزيل ويضيفه للقائمة مباشرة. يرجع false إذا موجود قبل */
    fun start(ctx: Context, item: ContentItem, title: String = item.name): Boolean {
        val url = item.url ?: return false
        if (exists(ctx, item)) return false
        val ext = url.substringAfterLast('.', "mp4").substringBefore('?').take(5).ifBlank { "mp4" }
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60).ifBlank { item.id }
        val name = "${safe}_${item.id}.$ext"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("A&S 4K")
            .addRequestHeader("User-Agent", XtreamApi.UA)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_MOVIES, name)
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(req)
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val path = File(dir, name).absolutePath
        val l = all(ctx)
        l.add(0, DownloadRec(keyOf(item), title, item.logo, item.type, url, id, path))
        save(ctx, l)
        return true
    }

    fun state(ctx: Context, rec: DownloadRec): DownloadState {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            dm.query(DownloadManager.Query().setFilterById(rec.dmId))?.use { c ->
                if (c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    return DownloadState(status, if (status == DownloadManager.STATUS_SUCCESSFUL) 100 else pct)
                }
            }
        } catch (e: Exception) { }
        // ما موجود بخدمة التنزيل: إذا الملف موجود نعتبره مكتمل
        return if (File(rec.path).exists()) DownloadState(DownloadManager.STATUS_SUCCESSFUL, 100)
               else DownloadState(DownloadManager.STATUS_FAILED, 0)
    }

    fun remove(ctx: Context, rec: DownloadRec) {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try { dm.remove(rec.dmId) } catch (e: Exception) { }
        try { File(rec.path).delete() } catch (e: Exception) { }
        save(ctx, all(ctx).filter { it.key != rec.key })
    }

    /** إعادة تنزيل عنصر فشل */
    fun retry(ctx: Context, rec: DownloadRec) {
        remove(ctx, rec)
        val type = rec.type
        val id = rec.key.substringAfter('_')
        start(ctx, ContentItem(id = id, name = rec.title, logo = rec.image, type = type, url = rec.url), rec.title)
    }
}
