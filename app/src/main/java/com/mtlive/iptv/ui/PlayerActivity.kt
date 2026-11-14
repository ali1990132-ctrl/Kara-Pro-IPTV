package com.mtlive.iptv.ui

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.mtlive.iptv.data.ContentType
import com.mtlive.iptv.data.HistoryStore
import com.mtlive.iptv.data.Settings
import com.mtlive.iptv.data.XtreamApi
import com.mtlive.iptv.databinding.ActivityPlayerBinding

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var index = 0
    private var position = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()
        index = PlayerQueue.index
        if (PlayerQueue.items.isEmpty()) { finish(); return }

        b.btnRetry.setOnClickListener { position = 0L; initPlayer() }
        setupZoomButtons()
        b.playerView.setControllerVisibilityListener(
            androidx.media3.ui.PlayerView.ControllerVisibilityListener { v ->
                b.tvTitle.visibility = v
                // لما يطلع شريط التحكم نرفع الشعار فوقه
                b.imgWatermark.animate().translationY(if (v == View.VISIBLE) -90f * resources.displayMetrics.density else 0f).setDuration(150).start()
            }
        )
        val multi = PlayerQueue.items.size > 1
        b.playerView.setShowNextButton(multi)
        b.playerView.setShowPreviousButton(multi)
    }

    override fun onStart() { super.onStart(); initPlayer() }

    override fun onStop() {
        super.onStop()
        player?.let { index = it.currentMediaItemIndex; position = if (isLive()) 0L else it.currentPosition }
        player?.release()
        player = null
    }

    private fun isLive() = PlayerQueue.items.getOrNull(index)?.type == ContentType.LIVE

    private fun initPlayer() {
        player?.release()
        b.errorBox.visibility = View.GONE
        b.progress.visibility = View.VISIBLE

        val http = OkHttpDataSource.Factory(XtreamApi.client).setUserAgent(XtreamApi.UA)
        val sources = DefaultMediaSourceFactory(this).setDataSourceFactory(http)
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(Settings.compatMode(this))
        val exo = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(sources).build()

        val media = PlayerQueue.items.map {
            MediaItem.Builder()
                .setUri(Uri.parse(it.url ?: ""))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.name).build())
                .build()
        }
        exo.setMediaItems(media, index.coerceIn(0, media.size - 1), position)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                b.progress.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                b.progress.visibility = View.GONE
                b.errorBox.visibility = View.VISIBLE
                b.btnRetry.requestFocus()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = exo.currentMediaItemIndex
                PlayerQueue.index = index
                b.tvTitle.text = mediaItem?.mediaMetadata?.title ?: ""
                b.errorBox.visibility = View.GONE
                PlayerQueue.items.getOrNull(index)?.let { HistoryStore.add(this@PlayerActivity, it) }
            }
        })
        b.tvTitle.text = PlayerQueue.items.getOrNull(index)?.name ?: ""
        PlayerQueue.items.getOrNull(index)?.let { HistoryStore.add(this, it) }
        exo.playWhenReady = true
        exo.prepare()
        b.playerView.player = exo
        player = exo
    }

    private val modes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private var modeIndex = 0
    private fun cycleAspect() {
        modeIndex = (modeIndex + 1) % modes.size
        b.playerView.resizeMode = modes[modeIndex]
        val label = when (modeIndex) { 0 -> "Fit"; 1 -> "Zoom"; else -> "Fill" }
        android.widget.Toast.makeText(this, label, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** أيقونات تكبير الشاشة داخل شريط التحكم (جنب الإعدادات) */
    private fun setupZoomButtons() {
        // زر ملء الشاشة المدمج: يكبّر الصورة (Zoom) ويرجعها
        b.playerView.setFullscreenButtonClickListener { full ->
            modeIndex = if (full) 1 else 0
            b.playerView.resizeMode = modes[modeIndex]
        }
        // زر إضافي يبدّل بين: ملائم / تكبير / تمديد
        val bar = b.playerView.findViewById<android.view.ViewGroup>(androidx.media3.ui.R.id.exo_basic_controls) ?: return
        val size = (48 * resources.displayMetrics.density).toInt()
        val btn = android.widget.ImageButton(this).apply {
            setImageResource(com.mtlive.iptv.R.drawable.ic_aspect)
            setBackgroundResource(android.R.color.transparent)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = "zoom"
            isFocusable = true
            setOnClickListener { cycleAspect() }
        }
        bar.addView(btn, 0, android.view.ViewGroup.LayoutParams(size, size))
    }

    /** ريموت التلفزيون: فوق/تحت يغيّر القناة بالبث المباشر */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val p = player
        if (p != null && isLive() && !b.playerView.isControllerFullyVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (p.hasNextMediaItem()) p.seekToNextMediaItem(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullscreen()
    }
}
