package com.karastore.iptv.data.model

import com.google.gson.annotations.SerializedName

/** نوع المحتوى المعروض في الواجهة */
enum class ContentType { LIVE, MOVIE, SERIES, EPISODE }

/** عنصر تصنيف موحّد (قناة/أفلام/مسلسلات) */
data class Category(
    val id: String,
    val name: String
)

/** عنصر محتوى موحّد تستخدمه الواجهة */
data class ContentItem(
    val id: String,
    val name: String,
    val logo: String?,
    val type: ContentType,
    val containerExtension: String? = null,
    /** رابط تشغيل جاهز (يُستخدم مع M3U) */
    val directUrl: String? = null,
    val categoryId: String? = null,
    val rating: String? = null,
    /** وقت الإضافة (unix) لترتيب "المضاف مؤخراً" */
    val added: Long? = null
)

// ----------------------- نماذج Xtream -----------------------

data class XtreamLoginResponse(
    @SerializedName("user_info") val userInfo: XtreamUserInfo?,
    @SerializedName("server_info") val serverInfo: XtreamServerInfo?
)

data class XtreamUserInfo(
    val username: String?,
    val password: String?,
    val status: String?,
    val auth: Int?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("is_trial") val isTrial: String?,
    @SerializedName("active_cons") val activeCons: String?,
    @SerializedName("max_connections") val maxConnections: String?
)

data class XtreamServerInfo(
    val url: String?,
    val port: String?,
    @SerializedName("https_port") val httpsPort: String?,
    @SerializedName("server_protocol") val serverProtocol: String?
)

data class XtreamCategory(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?
)

data class XtreamLiveStream(
    val num: Long?,
    val name: String?,
    @SerializedName("stream_id") val streamId: Long?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?
)

data class XtreamVodStream(
    val num: Long?,
    val name: String?,
    @SerializedName("stream_id") val streamId: Long?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val rating: String?,
    val added: String?
)

data class XtreamSeries(
    val num: Long?,
    val name: String?,
    @SerializedName("series_id") val seriesId: Long?,
    val cover: String?,
    @SerializedName("category_id") val categoryId: String?,
    val rating: String?,
    @SerializedName("last_modified") val lastModified: String?
)

data class XtreamSeriesInfo(
    val info: XtreamSeriesCover?,
    val episodes: Map<String, List<XtreamEpisode>>?
)

data class XtreamSeriesCover(
    val cover: String?,
    @SerializedName("cover_big") val coverBig: String?
)

data class XtreamEpisode(
    val id: String?,
    val title: String?,
    @SerializedName("episode_num") val episodeNum: Long?,
    @SerializedName("container_extension") val containerExtension: String?,
    val info: XtreamEpisodeInfo?
)

data class XtreamEpisodeInfo(
    @SerializedName("movie_image") val movieImage: String?
)
