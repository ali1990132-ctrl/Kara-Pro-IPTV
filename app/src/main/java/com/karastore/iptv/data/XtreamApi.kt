package com.karastore.iptv.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.karastore.iptv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** عميل Xtream Codes API */
class XtreamApi(
    private val server: String,
    private val username: String,
    private val password: String
) {
    private val base = normalize(server)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private fun api(action: String? = null, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=$username&password=$password$a$extra"
    }

    /** قراءة متدفّقة لتقليل استهلاك الذاكرة مع القوائم الضخمة */
    private suspend fun <T> fetch(url: String, type: java.lang.reflect.Type): T? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
            .header("Accept", "*/*")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful)
                throw RuntimeException("السيرفر رفض الطلب (كود ${resp.code})")
            val reader = resp.body?.charStream() ?: throw RuntimeException("رد فارغ من السيرفر")
            try {
                gson.fromJson<T>(reader, type)
            } catch (e: com.google.gson.JsonSyntaxException) {
                throw RuntimeException("رد غير متوقّع من السيرفر — تأكد من العنوان")
            } catch (e: com.google.gson.JsonIOException) {
                throw RuntimeException("خطأ بقراءة رد السيرفر")
            }
        }
    }

    /** تسجيل الدخول والتحقق من الاشتراك */
    suspend fun login(): XtreamUserInfo {
        val res: XtreamLoginResponse? = fetch(api(), XtreamLoginResponse::class.java)
        val info = res?.userInfo
            ?: throw RuntimeException("بيانات الدخول غير صحيحة — تأكد من المستخدم وكلمة المرور")
        if (info.auth == 0 || info.status.equals("Disabled", true))
            throw RuntimeException("الاشتراك غير مفعّل أو منتهي")
        return info
    }

    private suspend fun cats(action: String): List<Category> {
        val type = object : TypeToken<List<XtreamCategory>>() {}.type
        val list: List<XtreamCategory> = fetch(api(action), type) ?: emptyList()
        return list.mapNotNull { c ->
            val id = c.categoryId ?: return@mapNotNull null
            Category(id, c.categoryName ?: "—")
        }
    }

    suspend fun liveCategories(): List<Category> = cats("get_live_categories")
    suspend fun vodCategories(): List<Category> = cats("get_vod_categories")
    suspend fun seriesCategories(): List<Category> = cats("get_series_categories")

    suspend fun liveStreams(categoryId: String?): List<ContentItem> {
        val extra = if (categoryId != null) "&category_id=$categoryId" else ""
        val type = object : TypeToken<List<XtreamLiveStream>>() {}.type
        val list: List<XtreamLiveStream> = fetch(api("get_live_streams", extra), type) ?: emptyList()
        return list.map {
            ContentItem(
                id = it.streamId?.toString() ?: "",
                name = it.name ?: "",
                logo = it.streamIcon,
                type = ContentType.LIVE,
                directUrl = "$base/live/$username/$password/${it.streamId}.ts",
                categoryId = it.categoryId
            )
        }
    }

    suspend fun vodStreams(categoryId: String?): List<ContentItem> {
        val extra = if (categoryId != null) "&category_id=$categoryId" else ""
        val type = object : TypeToken<List<XtreamVodStream>>() {}.type
        val list: List<XtreamVodStream> = fetch(api("get_vod_streams", extra), type) ?: emptyList()
        return list.map {
            val ext = it.containerExtension ?: "mp4"
            ContentItem(
                id = it.streamId?.toString() ?: "",
                name = it.name ?: "",
                logo = it.streamIcon,
                type = ContentType.MOVIE,
                containerExtension = ext,
                directUrl = "$base/movie/$username/$password/${it.streamId}.$ext",
                categoryId = it.categoryId,
                rating = it.rating,
                added = it.added?.toLongOrNull()
            )
        }
    }

    suspend fun seriesList(categoryId: String?): List<ContentItem> {
        val extra = if (categoryId != null) "&category_id=$categoryId" else ""
        val type = object : TypeToken<List<XtreamSeries>>() {}.type
        val list: List<XtreamSeries> = fetch(api("get_series", extra), type) ?: emptyList()
        return list.map {
            ContentItem(
                id = it.seriesId?.toString() ?: "",
                name = it.name ?: "",
                logo = it.cover,
                type = ContentType.SERIES,
                categoryId = it.categoryId,
                rating = it.rating,
                added = it.lastModified?.toLongOrNull()
            )
        }
    }

    /** حلقات مسلسل معيّن (مرتبة) */
    suspend fun seriesEpisodes(seriesId: String): List<ContentItem> {
        val info: XtreamSeriesInfo? = fetch(api("get_series_info", "&series_id=$seriesId"), XtreamSeriesInfo::class.java)
        val seriesCover = info?.info?.coverBig?.ifBlank { null } ?: info?.info?.cover?.ifBlank { null }
        val result = mutableListOf<ContentItem>()
        info?.episodes?.toSortedMap(compareBy { it.toIntOrNull() ?: 0 })?.forEach { (season, eps) ->
            eps.forEach { ep ->
                val ext = ep.containerExtension ?: "mp4"
                val img = ep.info?.movieImage?.ifBlank { null } ?: seriesCover
                result.add(
                    ContentItem(
                        id = ep.id ?: "",
                        name = "م$season - ${ep.title ?: "حلقة ${ep.episodeNum}"}",
                        logo = img,
                        type = ContentType.EPISODE,
                        containerExtension = ext,
                        directUrl = "$base/series/$username/$password/${ep.id}.$ext"
                    )
                )
            }
        }
        return result
    }

    companion object {
        /** توحيد عنوان السيرفر: إضافة http وإزالة الشرطة الأخيرة */
        fun normalize(raw: String): String {
            var s = raw.trim()
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s.trimEnd('/')
        }
    }
}
