package com.karastore.iptv.data

import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/** تحميل وتحليل قائمة M3U/M3U8 مع تصنيفها إلى بث/أفلام/مسلسلات */
class M3uRepository(private val url: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // النوع -> (اسم المجموعة -> عناصر)
    private val data = mutableMapOf<ContentType, LinkedHashMap<String, MutableList<ContentItem>>>()
    private var loaded = false

    /** فحص سريع للوصول بدون تحميل القائمة كاملة */
    suspend fun probe() = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .header("Range", "bytes=0-2048")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code != 200 && resp.code != 206)
                throw RuntimeException("السيرفر رفض الطلب (كود ${resp.code}) — تأكد من الرابط")
            resp.body?.source()?.let { it.request(2048L) }
        }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .header("Accept", "*/*")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful)
                throw RuntimeException("السيرفر رفض الطلب (كود ${resp.code}) — تأكد من الرابط")
            val body = resp.body ?: throw RuntimeException("رد فارغ من السيرفر")
            parse(body.charStream().buffered())
        }
        if (data.values.all { it.isEmpty() })
            throw RuntimeException("ما لگينا أي محتوى بالرابط — غالباً الرابط غلط أو منتهي أو مو بصيغة M3U")
        loaded = true
    }

    private fun bucket(type: ContentType) = data.getOrPut(type) { LinkedHashMap() }

    private fun parse(reader: BufferedReader) {
        data.clear()
        var idx = 0
        var name: String? = null
        var logo: String? = null
        var group = "عام"

        reader.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            when {
                line.startsWith("#EXTINF", true) -> {
                    name = line.substringAfterLast(",").trim()
                    logo = attr(line, "tvg-logo")
                    group = attr(line, "group-title")?.ifBlank { null } ?: "عام"
                }
                line.startsWith("#") -> {}
                else -> {
                    val n = name
                    if (n != null) {
                        val type = when {
                            line.contains("/movie/", true) -> ContentType.MOVIE
                            line.contains("/series/", true) -> ContentType.SERIES
                            else -> ContentType.LIVE
                        }
                        val ext = if (type != ContentType.LIVE)
                            line.substringAfterLast('.', "").takeIf { it.length in 2..4 } else null
                        val item = ContentItem(
                            id = (idx++).toString(),
                            name = n.ifBlank { "عنصر $idx" },
                            logo = logo,
                            type = type,
                            containerExtension = ext,
                            directUrl = line,
                            categoryId = group
                        )
                        bucket(type).getOrPut(group) { mutableListOf() }.add(item)
                        name = null; logo = null; group = "عام"
                    }
                }
            }
        }
    }

    private fun attr(line: String, key: String): String? =
        Regex("$key=\"([^\"]*)\"").find(line)?.groupValues?.getOrNull(1)

    fun categories(type: ContentType): List<Category> =
        (data[type]?.keys ?: emptySet()).map { Category(it, it) }

    fun items(type: ContentType, categoryId: String?): List<ContentItem> {
        val map = data[type] ?: return emptyList()
        return if (categoryId == null) map.values.flatten() else (map[categoryId] ?: emptyList())
    }

    fun isLoaded() = loaded
}
