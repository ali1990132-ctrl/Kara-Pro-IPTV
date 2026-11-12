package com.karastore.iptv.ui.player

import android.os.Bundle
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.karastore.iptv.databinding.ActivityPlayerBinding
import okhttp3.OkHttpClient

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
            .setUserAgent("KaraPro/1.0")
        val sourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpFactory)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(sourceFactory)
            .build()

        exo.setMediaItem(MediaItem.fromUri(url))
        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                b.progress.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                showError()
            }
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
    }
}
