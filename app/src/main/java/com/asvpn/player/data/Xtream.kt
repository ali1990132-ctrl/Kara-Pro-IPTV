package com.asvpn.player.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Type
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ContentType { LIVE, MOVIE, SERIES, EPISODE }

data class Category(val id: String, val name: String)

data class ContentItem(
    val id: String,
    val name: String,
    val logo: String?,
    val type: ContentType,
    val url: String? = null,
    val categoryId: String? = null,
    val rating: String? = null,
    val added: Long? = null,
    val season: String? = null,
)

data class SeriesDetails(
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val cover: String?,
    val episodes: List<ContentItem>,
)

// ---------- Xtream JSON ----------
data class XLogin(@SerializedName("user_info") val userInfo: XUser?)

data class XUser(
    val username: String?,
    val status: String?,
    val auth: Int?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("active_cons") val activeCons: String?,
    @SerializedName("max_connections") val maxConnections: String?,
)

data class XCategory(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?,
)

data class XLive(
    val name: String?,
    @SerializedName("stream_id") val streamId: Long?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
)

data class XVod(
    val name: String?,
    @SerializedName("stream_id") val streamId: Long?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val rating: String?,
    val added: String?,
)

data class XSeries(
    val name: String?,
    @SerializedName("series_id") val seriesId: Long?,
    val cover: String?,
    @SerializedName("category_id") val categoryId: String?,
    val rating: String?,
    @SerializedName("last_modified") val lastModified: String?,
)

data class XSeriesInfo(val info: XSeriesMeta?, val episodes: Map<String, List<XEpisode>>?)

data class XSeriesMeta(
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
)

data class XEpisode(
    val id: String?,
    val title: String?,
    @SerializedName("episode_num") val episodeNum: Any?,
    @SerializedName("container_extension") val containerExtension: String?,
    val info: XEpisodeInfo?,
)

data class XEpisodeInfo(@SerializedName("movie_image") val movieImage: String?)

/** Xtream Codes client — works only with the five A&S servers. */
class XtreamApi(server: String, username: String, password: String) {

    val base = normalize(server)
    private val gson = Gson()
    private val u = enc(username)
    private val p = enc(password)

    private fun api(action: String? = null, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=$u&password=$p$a$extra"
    }

    private suspend fun <T> fetch(url: String, type: Type, http: OkHttpClient = client): T? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "*/*").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("السيرفر رفض الطلب (${resp.code})")
            val reader = resp.body?.charStream() ?: throw RuntimeException("رد فارغ من السيرفر")
            try {
                gson.fromJson<T>(reader, type)
            } catch (e: Exception) {
                throw RuntimeException("رد غير متوقّع من السيرفر")
            }
        }
    }

    /** Returns user info when the credentials are valid (even if the package expired). */
    suspend fun login(http: OkHttpClient = client): XUser {
        if (!isAllowed(base)) throw RuntimeException("السيرفر غير مسموح")
        val res: XLogin? = fetch(api(), XLogin::class.java, http)
        val info = res?.userInfo ?: throw RuntimeException("بيانات الدخول غير صحيحة")
        if (info.auth == 0) throw RuntimeException("بيانات الدخول غير صحيحة")
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
        ContentType.LIVE -> {
            val t = object : TypeToken<List<XLive>>() {}.type
            val list: List<XLive> = fetch(api("get_live_streams"), t) ?: emptyList()
            list.filter { it.streamId != null }.map {
                ContentItem(
                    it.streamId.toString(), it.name ?: "", it.streamIcon, ContentType.LIVE,
                    "$base/live/$u/$p/${it.streamId}.ts", it.categoryId
                )
            }
        }
        ContentType.MOVIE -> {
            val t = object : TypeToken<List<XVod>>() {}.type
            val list: List<XVod> = fetch(api("get_vod_streams"), t) ?: emptyList()
            list.filter { it.streamId != null }.map {
                val ext = it.containerExtension?.ifBlank { null } ?: "mp4"
                ContentItem(
                    it.streamId.toString(), it.name ?: "", it.streamIcon, ContentType.MOVIE,
                    "$base/movie/$u/$p/${it.streamId}.$ext", it.categoryId, it.rating, it.added?.toLongOrNull()
                )
            }
        }
        else -> {
            val t = object : TypeToken<List<XSeries>>() {}.type
            val list: List<XSeries> = fetch(api("get_series"), t) ?: emptyList()
            list.filter { it.seriesId != null }.map {
                ContentItem(
                    it.seriesId.toString(), it.name ?: "", it.cover, ContentType.SERIES,
                    null, it.categoryId, it.rating, it.lastModified?.toLongOrNull()
                )
            }
        }
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
                        ep.id ?: "", ep.title?.ifBlank { null } ?: "حلقة $num",
                        ep.info?.movieImage?.ifBlank { null } ?: cover, ContentType.EPISODE,
                        "$base/series/$u/$p/${ep.id}.$ext", season = season
                    )
                )
            }
        }
        return SeriesDetails(info?.info?.plot, info?.info?.genre, info?.info?.releaseDate, cover, eps)
    }

    companion object {
        const val UA = "VLC/3.0.20 LibVLC/3.0.20"

        val ALLOWED_HOSTS = listOf(
            "http://8ksh1856.site:8080",
            "http://ktvs.xyz:8080",
            "http://karaali.online:2052",
            "http://renvioweb.online:8080",
            "http://amlive.cc:80",
        )

        fun isAllowed(host: String): Boolean {
            val h = normalize(host).lowercase()
            return ALLOWED_HOSTS.any { it.lowercase() == h || it.lowercase().removeSuffix(":80") == h }
        }

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        private val loginClient: OkHttpClient by lazy {
            client.newBuilder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()
        }

        /** Tries the credentials on all five servers at once; returns the first that accepts them. */
        suspend fun autoLogin(username: String, password: String): Pair<String, XUser> {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val result = CompletableDeferred<Pair<String, XUser>>()
            val errors = Collections.synchronizedList(mutableListOf<Exception>())
            val jobs = ALLOWED_HOSTS.map { host ->
                scope.launch {
                    try {
                        val info = XtreamApi(host, username, password).login(loginClient)
                        result.complete(host to info)
                    } catch (e: Exception) {
                        errors.add(e)
                        if (errors.size == ALLOWED_HOSTS.size) {
                            val auth = errors.firstOrNull { (it.message ?: "").contains("غير صحيحة") }
                            result.completeExceptionally(
                                auth ?: RuntimeException("تعذّر الاتصال بالسيرفرات — جرّب تشغيل الـ VPN")
                            )
                        }
                    }
                }
            }
            try {
                return result.await()
            } finally {
                jobs.forEach { it.cancel() }
            }
        }

        fun normalize(raw: String): String {
            var s = raw.trim().replace(" ", "")
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            return s.trimEnd('/')
        }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    }
}

data class Session(
    val host: String,
    val username: String,
    val password: String,
    val status: String? = null,
    val expDate: String? = null,
) {
    fun api() = XtreamApi(host, username, password)

    private fun expMillis(): Long? = expDate?.toLongOrNull()?.takeIf { it > 0 }?.times(1000)

    fun isExpired(): Boolean {
        if (status.equals("Expired", true) || status.equals("Disabled", true) || status.equals("Banned", true)) return true
        val e = expMillis() ?: return false
        return e < System.currentTimeMillis()
    }

    /** days left, or null when unlimited */
    fun daysLeft(): Long? {
        val e = expMillis() ?: return null
        return ((e - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    }

    fun expiryText(): String? {
        val e = expMillis() ?: return null
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(e))
    }
}

object SessionStore {
    private const val PREF = "xt_session"
    private val gson = Gson()

    fun save(ctx: Context, s: Session) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("s", gson.toJson(s)).putString("last_user", s.username).apply()

    fun load(ctx: Context): Session? = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("s", null)
            ?.let { gson.fromJson(it, Session::class.java) }
    } catch (_: Exception) {
        null
    }

    fun lastUser(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("last_user", "") ?: ""

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("s").apply()
        Cache.clear()
        Cache.session = null
    }
}

/** In-memory content cache for the app run. */
object Cache {
    var session: Session? = null
    private val cats = HashMap<ContentType, List<Category>>()
    private val items = HashMap<ContentType, List<ContentItem>>()

    fun items(t: ContentType) = items[t]
    fun clear() { cats.clear(); items.clear() }

    suspend fun load(t: ContentType, force: Boolean = false): Pair<List<Category>, List<ContentItem>> {
        if (!force) {
            val c = cats[t]; val i = items[t]
            if (c != null && i != null) return c to i
        }
        val api = session?.api() ?: throw RuntimeException("سجّل الدخول من جديد")
        val c = api.categories(t)
        val i = api.items(t)
        cats[t] = c; items[t] = i
        return c to i
    }
}
