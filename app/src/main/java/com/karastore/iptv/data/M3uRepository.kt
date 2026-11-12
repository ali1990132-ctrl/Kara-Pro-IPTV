package com.karastore.iptv.data

import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** تحميل وتحليل قائمة M3U/M3U8 */
class M3uRepository(private val url: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // group-title -> قنوات
    private var grouped: LinkedHashMap<String, MutableList<ContentItem>> = LinkedHashMap()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "KaraPro/1.0")
            .build()
        val text = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            resp.body?.string() ?: throw RuntimeException("empty body")
        }
        parse(text)
        loaded = true
    }

    private fun parse(text: String) {
        grouped = LinkedHashMap()
        val lines = text.lines()
        var i = 0
        var idx = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val name = line.substringAfterLast(",").trim()
                val logo = attr(line, "tvg-logo")
                val group = attr(line, "group-title")?.ifBlank { null } ?: "عام"
                // ابحث عن سطر الرابط التالي
                var j = i + 1
                while (j < lines.size && (lines[j].trim().isEmpty() || lines[j].trim().startsWith("#"))) j++
                if (j < lines.size) {
                    val streamUrl = lines[j].trim()
                    val item = ContentItem(
                        id = (idx++).toString(),
                        name = name.ifBlank { "قناة ${idx}" },
                        logo = logo,
                        type = ContentType.LIVE,
                        directUrl = streamUrl
                    )
                    grouped.getOrPut(group) { mutableListOf() }.add(item)
                    i = j
                }
            }
            i++
        }
    }

    private fun attr(line: String, key: String): String? {
        val regex = Regex("$key=\"([^\"]*)\"")
        return regex.find(line)?.groupValues?.getOrNull(1)
    }

    fun categories(): List<Category> =
        grouped.keys.mapIndexed { i, name -> Category(name, name) }

    fun channels(categoryId: String?): List<ContentItem> {
        if (categoryId == null) return grouped.values.flatten()
        return grouped[categoryId] ?: emptyList()
    }

    fun isLoaded() = loaded
}
