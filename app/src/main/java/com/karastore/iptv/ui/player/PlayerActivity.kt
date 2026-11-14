package com.karastore.iptv.ui.player

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.gson.Gson
import com.karastore.iptv.R
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.FavoritesStore
import com.karastore.iptv.data.HistoryStore
import com.karastore.iptv.data.Settings
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.ActivityPlayerBinding
import okhttp3.OkHttpClient
import java.io.File

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private var item: ContentItem? = null
    private var isLive = false
    private var channels: List<ContentItem> = emptyList()
    private var currentIndex = 0
    private var fallbackUrl: String = ""
    private var locked = false

    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        enterImmersive()

        fallbackUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        item = try { Gson().fromJson(intent.getStringExtra(EXTRA_ITEM), ContentItem::class.java) } catch (e: Exception) { null }
        isLive = item?.type == ContentType.LIVE

        if (isLive && item != null) {
            val cat = ContentCache.filter(ContentType.LIVE, item!!.categoryId)
                .ifEmpty { ContentCache.all(ContentType.LIVE) }
                .ifEmpty { listOf(item!!) }
            channels = if (cat.size > 1000) cat.subList(0, 1000) else cat
            currentIndex = channels.indexOfFirst { it.id == item!!.id }.coerceAtLeast(0)
        } else {
            channels = item?.let { listOf(it) } ?: emptyList()
            currentIndex = 0
        }

        val multi = isLive && channels.size > 1
        b.playerView.setShowNextButton(multi)
        b.playerView.setShowPreviousButton(multi)
        b.liveBadge.visibility = if (isLive) View.VISIBLE else View.GONE
        b.tvWatermark.visibility = View.GONE
        b.btnChannels.visibility = if (multi) View.VISIBLE else View.GONE
        b.tvTitle.text = (channels.getOrNull(currentIndex) ?: item)?.name ?: ""

        b.btnRetry.setOnClickListener { initPlayer() }
        b.btnAspect.setOnClickListener { cycleAspect() }
        b.btnRotate.setOnClickListener { toggleRotation() }
        b.btnLock.setOnClickListener { setLocked(true) }
        b.btnUnlock.setOnClickListener { setLocked(false) }
        b.btnNight.setOnClickListener { sleepMenu() }
        b.btnFav.setOnClickListener { toggleFav() }
        b.btnChannels.setOnClickListener { channelsMenu() }
        refreshFav()

        b.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (!locked) b.topBar.visibility = visibility
            }
        )
    }

    override fun onStart() { super.onStart(); initPlayer() }
    override fun onStop() { super.onStop(); releasePlayer() }

    private fun initPlayer() {
        releasePlayer()
        b.errorBox.visibility = View.GONE
        b.progress.visibility = View.VISIBLE

        val httpFactory = OkHttpDataSource.Factory(OkHttpClient()).setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
        val sourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(httpFactory)
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(Settings.compatMode(this))

        val exo = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(sourceFactory).build()

        if (isLive && channels.size > 1) {
            val items = channels.map { MediaItem.fromUri(Uri.parse(it.directUrl ?: "")) }
            exo.setMediaItems(items, currentIndex, 0L)
        } else {
            val u = channels.getOrNull(0)?.directUrl ?: fallbackUrl
            if (u.isBlank()) { showError(); return }
            val uri = if (u.startsWith("/")) Uri.fromFile(File(u)) else Uri.parse(u)
            exo.setMediaItem(MediaItem.fromUri(uri))
        }

        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                b.progress.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) { showError() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player?.currentMediaItemIndex ?: currentIndex
                onChannelChanged()
            }
        })
        exo.prepare()
        b.playerView.player = exo
        player = exo

        // سجّل المشاهدة للعنصر الحالي
        (channels.getOrNull(currentIndex) ?: item)?.let { HistoryStore.add(this, it) }
    }

    private fun onChannelChanged() {
        val ch = channels.getOrNull(currentIndex) ?: return
        b.tvTitle.text = ch.name
        HistoryStore.add(this, ch)
        refreshFav()
    }

    private fun current(): ContentItem? = channels.getOrNull(currentIndex) ?: item

    private fun toggleFav() {
        val c = current() ?: return
        FavoritesStore.toggle(this, c)
        refreshFav()
    }

    private fun refreshFav() {
        val c = current() ?: return
        val fav = FavoritesStore.isFav(this, c.id)
        b.btnFav.setImageResource(if (fav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
    }

    private fun channelsMenu() {
        if (channels.isEmpty()) return
        val names = channels.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.nav_live)
            .setItems(names) { _, which ->
                currentIndex = which
                player?.seekTo(which, 0L)
            }.show()
    }

    private fun sleepMenu() {
        val mins = intArrayOf(15, 30, 60)
        val labels = arrayOf("15 min", "30 min", "60 min", getString(R.string.sleep_off))
        AlertDialog.Builder(this)
            .setTitle(R.string.sleep_timer)
            .setItems(labels) { _, which ->
                sleepRunnable?.let { handler.removeCallbacks(it) }
                if (which < mins.size) {
                    val r = Runnable { player?.pause(); Toast.makeText(this, "⏾", Toast.LENGTH_SHORT).show() }
                    sleepRunnable = r
                    handler.postDelayed(r, mins[which] * 60_000L)
                    Toast.makeText(this, "${mins[which]} min", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun setLocked(lock: Boolean) {
        locked = lock
        if (lock) {
            b.topBar.visibility = View.GONE
            b.playerView.useController = false
            b.playerView.hideController()
            b.btnUnlock.visibility = View.VISIBLE
        } else {
            b.playerView.useController = true
            b.btnUnlock.visibility = View.GONE
            b.playerView.showController()
        }
    }

    private val aspectModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private var aspectIndex = 0
    private fun cycleAspect() {
        aspectIndex = (aspectIndex + 1) % aspectModes.size
        b.playerView.resizeMode = aspectModes[aspectIndex]
    }

    private var landscape = false
    private fun toggleRotation() {
        landscape = !landscape
        requestedOrientation = if (landscape)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, b.root)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun showError() {
        b.progress.visibility = View.GONE
        b.errorBox.visibility = View.VISIBLE
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepRunnable?.let { handler.removeCallbacks(it) }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ITEM = "item"
    }
}
