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

/** تحميل وتحليل قائمة M3U/M3U8 */
class M3uRepository(private val url: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // group-title -> قنوات
    private var grouped: LinkedHashMap<String, MutableList<ContentItem>> = LinkedHashMap()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .header("Accept", "*/*")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("السيرفر رفض الطلب (كود ${resp.code}) — تأكد من الرابط أو جرّب تبويب Xtream Codes")
            }
            val body = resp.body ?: throw RuntimeException("رد فارغ من السيرفر")
            val reader = body.charStream().buffered()
            parse(reader)
        }

        if (grouped.isEmpty()) {
            throw RuntimeException("ما لگينا أي قناة بالرابط — غالباً الرابط غلط أو منتهي الاشتراك أو مو بصيغة M3U")
        }
        loaded = true
    }

    /** تحليل تدفّقي يتحمّل القوائم الكبيرة بدون استهلاك الذاكرة */
    private fun parse(reader: BufferedReader) {
        grouped = LinkedHashMap()
        var idx = 0
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup = "عام"

        reader.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingName = line.substringAfterLast(",").trim()
                    pendingLogo = attr(line, "tvg-logo")
                    pendingGroup = attr(line, "group-title")?.ifBlank { null } ?: "عام"
                }
                line.startsWith("#") -> { /* تجاهل أسطر التعريف الأخرى */ }
                else -> {
                    // سطر رابط
                    if (pendingName != null) {
                        val item = ContentItem(
                            id = (idx++).toString(),
                            name = pendingName!!.ifBlank { "قناة $idx" },
                            logo = pendingLogo,
                            type = ContentType.LIVE,
                            directUrl = line
                        )
                        grouped.getOrPut(pendingGroup) { mutableListOf() }.add(item)
                        pendingName = null
                        pendingLogo = null
                        pendingGroup = "عام"
                    }
                }
            }
        }
    }

    private fun attr(line: String, key: String): String? {
        val regex = Regex("$key=\"([^\"]*)\"")
        return regex.find(line)?.groupValues?.getOrNull(1)
    }

    fun categories(): List<Category> =
        grouped.keys.map { name -> Category(name, name) }

    fun channels(categoryId: String?): List<ContentItem> {
        if (categoryId == null) return grouped.values.flatten()
        return grouped[categoryId] ?: emptyList()
    }

    fun isLoaded() = loaded
}
