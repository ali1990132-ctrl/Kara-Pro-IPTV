package com.karastore.iptv.ui.player

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.gson.Gson
import com.karastore.iptv.data.HistoryStore
import com.karastore.iptv.data.Settings
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.databinding.ActivityPlayerBinding
import okhttp3.OkHttpClient
import java.io.File

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var url: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        url = intent.getStringExtra(EXTRA_URL) ?: ""
        b.btnRetry.setOnClickListener { initPlayer() }

        // سجّل المشاهدة
        intent.getStringExtra(EXTRA_ITEM)?.let { json ->
            try {
                val item = Gson().fromJson(json, ContentItem::class.java)
                if (item != null) HistoryStore.add(this, item)
            } catch (_: Exception) {}
        }
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initPlayer() {
        releasePlayer()
        if (url.isBlank()) { showError(); return }

        b.errorBox.visibility = View.GONE
        b.progress.visibility = View.VISIBLE

        val httpFactory = OkHttpDataSource.Factory(OkHttpClient())
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
        val sourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpFactory)

        // وضع التوافقية: يفعّل التحويل الاحتياطي للترميز لتشغيل قنوات أكثر
        val compat = Settings.compatMode(this)
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(compat)

        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(sourceFactory)
            .build()

        val uri = if (url.startsWith("/")) Uri.fromFile(File(url)) else Uri.parse(url)
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                b.progress.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) { showError() }
        })
        exo.prepare()

        b.playerView.player = exo
        player = exo
    }

    private fun showError() {
        b.progress.visibility = View.GONE
        b.errorBox.visibility = View.VISIBLE
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ITEM = "item"
    }
}
