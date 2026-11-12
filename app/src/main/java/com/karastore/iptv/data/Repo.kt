package com.karastore.iptv.data

import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.data.model.XtreamUserInfo

/**
 * مستودع موحّد يخفي الفرق بين Xtream و M3U عن الواجهات.
 * يُهيّأ مرة واحدة بعد تسجيل الدخول.
 */
object Repo {
    var session: Session? = null
        private set

    var userInfo: XtreamUserInfo? = null

    private var xtream: XtreamApi? = null
    private var m3u: M3uRepository? = null

    val isXtream get() = session?.mode == LoginMode.XTREAM

    fun init(session: Session) {
        this.session = session
        userInfo = null
        when (session.mode) {
            LoginMode.XTREAM -> {
                xtream = XtreamApi(session.server, session.username, session.password)
                m3u = null
            }
            LoginMode.M3U -> {
                m3u = M3uRepository(session.m3uUrl)
                xtream = null
            }
        }
    }

    /** يتحقق من الاتصال؛ يرمي استثناء عند الفشل */
    suspend fun connect() {
        when (session?.mode) {
            LoginMode.XTREAM -> userInfo = xtream!!.login()
            LoginMode.M3U -> m3u!!.probe()
            null -> throw IllegalStateException("no session")
        }
    }

    suspend fun categories(type: ContentType): List<Category> = when {
        isXtream -> when (type) {
            ContentType.LIVE -> xtream!!.liveCategories()
            ContentType.MOVIE -> xtream!!.vodCategories()
            ContentType.SERIES -> xtream!!.seriesCategories()
            else -> emptyList()
        }
        else -> {
            if (m3u?.isLoaded() != true) m3u!!.load()
            m3u!!.categories()
        }
    }

    suspend fun content(type: ContentType, categoryId: String?): List<ContentItem> = when {
        isXtream -> when (type) {
            ContentType.LIVE -> xtream!!.liveStreams(categoryId)
            ContentType.MOVIE -> xtream!!.vodStreams(categoryId)
            ContentType.SERIES -> xtream!!.seriesList(categoryId)
            else -> emptyList()
        }
        else -> {
            if (m3u?.isLoaded() != true) m3u!!.load()
            m3u!!.channels(categoryId)
        }
    }

    suspend fun episodes(seriesId: String): List<ContentItem> =
        xtream?.seriesEpisodes(seriesId) ?: emptyList()

    fun logout() {
        session = null
        userInfo = null
        xtream = null
        m3u = null
    }
}
