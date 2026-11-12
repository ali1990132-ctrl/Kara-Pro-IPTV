package com.karastore.iptv.data

import android.content.Context
import com.karastore.iptv.data.model.ContentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    /** المعرّفات قيد التنزيل حالياً ونسبة تقدّمها (0..100، -1 غير معروف) */
    private val active = HashMap<String, Int>()

    fun isDownloading(id: String) = active.containsKey(id)
    fun progress(id: String): Int = active[id] ?: -1

    /**
     * يبدأ التنزيل في الخلفية ويضيف العنصر فوراً لقائمة التنزيلات (بحالة "جارٍ").
     * onResult(success, message) يُستدعى على خيط الخلفية.
     */
    fun start(
        ctx: Context,
        item: ContentItem,
        onResult: (Boolean, String) -> Unit
    ) {
        val url = item.directUrl
        if (url.isNullOrBlank()) { onResult(false, "لا يوجد رابط للتنزيل"); return }
        if (active.containsKey(item.id)) { onResult(false, "التنزيل جارٍ بالفعل"); return }

        active[item.id] = -1
        // يظهر فوراً بقائمة التنزيلات وهو يحمّل
        DownloadsStore.upsert(ctx, item)

        scope.launch {
            try {
                val dir = File(ctx.getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val ext = item.containerExtension ?: "mp4"
                val file = File(dir, "${item.id}.$ext")
                download(url, file) { pct -> active[item.id] = pct }
                // خزّن العنصر مع مسار الملف المحلي (اكتمل)
                DownloadsStore.upsert(ctx, item.copy(directUrl = file.absolutePath))
                onResult(true, "اكتمل تنزيل: ${item.name}")
            } catch (e: Exception) {
                DownloadsStore.remove(ctx, item.id)
                onResult(false, "فشل التنزيل: ${e.message ?: ""}")
            } finally {
                active.remove(item.id)
            }
        }
    }

    private fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("كود ${resp.code}")
            val body = resp.body ?: throw RuntimeException("رد فارغ")
            val total = body.contentLength()
            var read = 0L
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(((read * 100) / total).toInt())
                    }
                }
            }
        }
    }

    fun deleteFile(item: ContentItem) {
        try { item.directUrl?.let { File(it).takeIf { f -> f.exists() }?.delete() } } catch (_: Exception) {}
    }
}
