package com.mtlive.iptv.data

import com.google.gson.annotations.SerializedName

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
    val season: String? = null
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val duration: String?,
    val backdrop: String?,
    val cast: String?
)

data class SeriesDetails(
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val backdrop: String?,
    val episodes: List<ContentItem>
)

// ---------- Xtream JSON ----------
data class XLogin(
    @SerializedName("user_info") val userInfo: XUser?
)

data class XUser(
    val username: String?,
    val status: String?,
    val auth: Int?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("active_cons") val activeCons: String?,
    @SerializedName("max_connections") val maxConnections: String?
)

data class XCategory(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?
)

data class XLive(
    val name: String?,
    @SerializedName("stream_id") val streamId: Long?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?
)

data class XVod(
    val name: String?,
    @SerializedName("stream_id") val streamId: Long?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val rating: String?,
    val added: String?
)

data class XSeries(
    val name: String?,
    @SerializedName("series_id") val seriesId: Long?,
    val cover: String?,
    @SerializedName("category_id") val categoryId: String?,
    val rating: String?,
    @SerializedName("last_modified") val lastModified: String?
)

data class XVodInfoResp(val info: XVodInfo?)

data class XVodInfo(
    val plot: String?,
    val description: String?,
    val genre: String?,
    @SerializedName("releasedate") val releaseDate: String?,
    val duration: String?,
    val cast: String?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?
)

data class XSeriesInfo(
    val info: XSeriesMeta?,
    val episodes: Map<String, List<XEpisode>>?
)

data class XSeriesMeta(
    val cover: String?,
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?
)

data class XEpisode(
    val id: String?,
    val title: String?,
    @SerializedName("episode_num") val episodeNum: Any?,
    @SerializedName("container_extension") val containerExtension: String?,
    val info: XEpisodeInfo?
)

data class XEpisodeInfo(
    @SerializedName("movie_image") val movieImage: String?
)
