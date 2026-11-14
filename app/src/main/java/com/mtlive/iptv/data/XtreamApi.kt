package com.mtlive.iptv.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Type
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** عميل Xtream Codes — يشتغل ويه أي هوست */
class XtreamApi(server: String, private val username: String, private val password: String) {

    val base = normalize(server)
    private val gson = Gson()
    private val u = enc(username)
    private val p = enc(password)

    private fun api(action: String? = null, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=$u&password=$p$a$extra"
    }

    private suspend fun <T> fetch(url: String, type: Type): T? = fetchWith(client, url, type)

    private suspend fun <T> fetchWith(http: OkHttpClient, url: String, type: Type): T? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("السيرفر رفض الطلب (${resp.code})")
            val reader = resp.body?.charStream() ?: throw RuntimeException("رد فارغ من السيرفر")
            try {
                gson.fromJson<T>(reader, type)
            } catch (e: Exception) {
                throw RuntimeException("رد غير متوقّع من السيرفر — تأكد من الرابط")
            }
        }
    }

    suspend fun login(): XUser = loginWith(client)

    suspend fun loginWith(http: OkHttpClient): XUser {
        if (!isAllowed(base)) throw RuntimeException("السيرفر غير مسموح")
        val res: XLogin? = fetchWith(http, api(), XLogin::class.java)
        val info = res?.userInfo ?: throw RuntimeException("بيانات الدخول غير صحيحة")
        if (info.auth == 0) throw RuntimeException("بيانات الدخول غير صحيحة")
        if (info.status.equals("Disabled", true) || info.status.equals("Expired", true))
            throw RuntimeException("الاشتراك منتهي أو غير مفعّل")
        return info
    }

    suspend fun categories(type: ContentType): List<Category> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            else -> "get_series_categories"
        }
        val t = object : TypeToken<List<XCategory>>() {}.type
        val list: List<XCategory> = fetch(api(action), t) ?: emptyList()
        return list.mapNotNull { c -> c.categoryId?.let { Category(it, c.categoryName ?: "—") } }
    }

    suspend fun items(type: ContentType): List<ContentItem> = when (type) {
        ContentType.LIVE -> live()
        ContentType.MOVIE -> vod()
        else -> series()
    }

    private suspend fun live(): List<ContentItem> {
        val t = object : TypeToken<List<XLive>>() {}.type
        val list: List<XLive> = fetch(api("get_live_streams"), t) ?: emptyList()
        return list.filter { it.streamId != null }.map {
            ContentItem(
                id = it.streamId.toString(), name = it.name ?: "", logo = it.streamIcon,
                type = ContentType.LIVE, url = "$base/live/$u/$p/${it.streamId}.ts",
                categoryId = it.categoryId
            )
        }
    }

    private suspend fun vod(): List<ContentItem> {
        val t = object : TypeToken<List<XVod>>() {}.type
        val list: List<XVod> = fetch(api("get_vod_streams"), t) ?: emptyList()
        return list.filter { it.streamId != null }.map {
            val ext = it.containerExtension?.ifBlank { null } ?: "mp4"
            ContentItem(
                id = it.streamId.toString(), name = it.name ?: "", logo = it.streamIcon,
                type = ContentType.MOVIE, url = "$base/movie/$u/$p/${it.streamId}.$ext",
                categoryId = it.categoryId, rating = it.rating, added = it.added?.toLongOrNull()
            )
        }
    }

    private suspend fun series(): List<ContentItem> {
        val t = object : TypeToken<List<XSeries>>() {}.type
        val list: List<XSeries> = fetch(api("get_series"), t) ?: emptyList()
        return list.filter { it.seriesId != null }.map {
            ContentItem(
                id = it.seriesId.toString(), name = it.name ?: "", logo = it.cover,
                type = ContentType.SERIES, categoryId = it.categoryId, rating = it.rating,
                added = it.lastModified?.toLongOrNull()
            )
        }
    }

    suspend fun movieInfo(id: String): MovieInfo? {
        val r: XVodInfoResp? = try { fetch(api("get_vod_info", "&vod_id=$id"), XVodInfoResp::class.java) } catch (e: Exception) { null }
        val i = r?.info ?: return null
        return MovieInfo(
            plot = i.plot?.ifBlank { null } ?: i.description,
            genre = i.genre, releaseDate = i.releaseDate, duration = i.duration,
            backdrop = i.backdropPath?.firstOrNull { it.isNotBlank() }, cast = i.cast
        )
    }

    suspend fun seriesDetails(id: String): SeriesDetails {
        val info: XSeriesInfo? = fetch(api("get_series_info", "&series_id=$id"), XSeriesInfo::class.java)
        val cover = info?.info?.cover
        val eps = mutableListOf<ContentItem>()
        info?.episodes?.toSortedMap(compareBy<String> { it.toIntOrNull() ?: 0 }.thenBy { it })?.forEach { (season, list) ->
            list.forEach { ep ->
                val ext = ep.containerExtension?.ifBlank { null } ?: "mp4"
                val num = ep.episodeNum?.toString()?.removeSuffix(".0") ?: ""
                eps.add(
                    ContentItem(
                        id = ep.id ?: "", name = ep.title?.ifBlank { null } ?: "حلقة $num",
                        logo = ep.info?.movieImage?.ifBlank { null } ?: cover,
                        type = ContentType.EPISODE, url = "$base/series/$u/$p/${ep.id}.$ext",
                        season = season
                    )
                )
            }
        }
        return SeriesDetails(
            plot = info?.info?.plot, genre = info?.info?.genre, releaseDate = info?.info?.releaseDate,
            backdrop = info?.info?.backdropPath?.firstOrNull { it.isNotBlank() }, episodes = eps
        )
    }

    companion object {
        const val UA = "VLC/3.0.20 LibVLC/3.0.20"

        /** التطبيق يشتغل فقط على هاي السيرفرات */
        val ALLOWED_HOSTS = listOf(
            "http://8ksh1856.site:8080",
            "http://ktvs.xyz:8080",
            "http://karaali.online:2052",
            "http://renvioweb.online:8080",
            "http://amlive.cc:80"
        )

        fun isAllowed(host: String): Boolean {
            val h = normalize(host).lowercase()
            return ALLOWED_HOSTS.any { it.lowercase() == h || it.lowercase().removeSuffix(":80") == h }
        }

        /** رقم السيرفر للعرض بدل الرابط */
        fun serverLabel(host: String): String {
            val h = normalize(host).lowercase()
            val i = ALLOWED_HOSTS.indexOfFirst { it.lowercase() == h || it.lowercase().removeSuffix(":80") == h }
            return if (i >= 0) "Server ${i + 1}" else "—"
        }

        private val loginClient: OkHttpClient by lazy {
            client.newBuilder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()
        }

        /**
         * يجرّب اسم المستخدم وكلمة المرور على كل السيرفرات بنفس الوقت،
         * ويرجع أول سيرفر يقبل الدخول.
         */
        suspend fun autoLogin(username: String, password: String): Pair<String, XUser> {
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
            val result = kotlinx.coroutines.CompletableDeferred<Pair<String, XUser>>()
            val errors = java.util.Collections.synchronizedList(mutableListOf<Exception>())
            val jobs = ALLOWED_HOSTS.map { host ->
                scope.launch {
                    try {
                        val info = XtreamApi(host, username, password).loginWith(loginClient)
                        result.complete(host to info)
                    } catch (e: Exception) {
                        errors.add(e)
                        if (errors.size == ALLOWED_HOSTS.size) {
                            val authFail = errors.firstOrNull { (it.message ?: "").contains("الاشتراك") || (it.message ?: "").contains("غير صحيحة") }
                            result.completeExceptionally(authFail ?: RuntimeException("بيانات الدخول غير صحيحة أو السيرفر غير متاح"))
                        }
                    }
                }
            }
            try { return result.await() } finally { jobs.forEach { it.cancel() } }
        }

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun normalize(raw: String): String {
            var s = raw.trim().replace(" ", "")
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            s = s.trimEnd('/')
            // لو المستخدم لصق رابط كامل (get.php / player_api.php) ناخذ الهوست بس
            listOf("/get.php", "/player_api.php", "/c/", "/live/").forEach { k ->
                val i = s.indexOf(k, ignoreCase = true)
                if (i > 8) s = s.substring(0, i)
            }
            return s.trimEnd('/')
        }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    }
}
