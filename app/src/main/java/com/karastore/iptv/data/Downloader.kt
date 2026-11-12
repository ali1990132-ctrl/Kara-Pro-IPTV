package com.karastore.iptv.data

import android.content.Context
import com.karastore.iptv.data.model.ContentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** تنزيل الأفلام/الحلقات للمشاهدة بدون إنترنت (للـ VOD فقط) */
object Downloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** المعرّفات قيد التنزيل حالياً */
    val active = mutableSetOf<String>()

    fun isDownloading(id: String) = active.contains(id)

    /**
     * يبدأ التنزيل في الخلفية.
     * onResult(success, message) يُستدعى على خيط الخلفية — نفّذ UI على الخيط الرئيسي من المُستدعي.
     */
    fun start(
        ctx: Context,
        item: ContentItem,
        onResult: (Boolean, String) -> Unit
    ) {
        val url = item.directUrl
        if (url.isNullOrBlank()) { onResult(false, "لا يوجد رابط للتنزيل"); return }
        if (active.contains(item.id)) { onResult(false, "التنزيل جارٍ بالفعل"); return }

        active.add(item.id)
        scope.launch {
            try {
                val dir = File(ctx.getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val ext = item.containerExtension ?: "mp4"
                val file = File(dir, "${item.id}.$ext")
                download(url, file)
                // خزّن العنصر مع مسار الملف المحلي
                val localItem = item.copy(directUrl = file.absolutePath)
                DownloadsStore.add(ctx, localItem)
                onResult(true, "اكتمل تنزيل: ${item.name}")
            } catch (e: Exception) {
                onResult(false, "فشل التنزيل: ${e.message ?: ""}")
            } finally {
                active.remove(item.id)
            }
        }
    }

    private fun download(url: String, target: File) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("كود ${resp.code}")
            val body = resp.body ?: throw RuntimeException("رد فارغ")
            target.outputStream().use { out ->
                body.byteStream().copyTo(out, 1 shl 16)
            }
        }
    }

    fun deleteFile(item: ContentItem) {
        try { item.directUrl?.let { File(it).takeIf { f -> f.exists() }?.delete() } } catch (_: Exception) {}
    }
}
