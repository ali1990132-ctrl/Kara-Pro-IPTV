package com.asvpn.player.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.asvpn.player.R
import com.asvpn.player.data.Link
import com.asvpn.player.data.Net
import com.asvpn.player.data.Store
import com.asvpn.player.vpn.Vpn
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        fun start(ctx: Context, title: String, url: String) = start(ctx, listOf(Link(title, url)), 0)

        fun start(ctx: Context, items: List<Link>, index: Int) {
            // keep the intent small: pass at most 300 items around the selected one
            val from = (index - 150).coerceAtLeast(0)
            val slice = items.subList(from, (from + 300).coerceAtMost(items.size))
            val i = Intent(ctx, PlayerActivity::class.java)
                .putExtra("titles", slice.map { it.title }.toTypedArray())
                .putExtra("urls", slice.map { it.url }.toTypedArray())
                .putExtra("index", index - from)
            ctx.startActivity(i)
        }
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private var links: List<Link> = emptyList()
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private val pickSub = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addSubtitle(uri, Ui.fileName(this, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemUi()

        playerView = findViewById(R.id.playerView)
        titleView = findViewById(R.id.title)
        val topBar = findViewById<View>(R.id.topBar)
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { vis ->
            topBar.visibility = vis
            if (vis != View.VISIBLE) hideSystemUi()
        })

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSubs).setOnClickListener { subtitleMenu() }
        findViewById<View>(R.id.btnSpeed).setOnClickListener { speedMenu() }
        findViewById<View>(R.id.btnAspect).setOnClickListener { cycleAspect() }
        findViewById<View>(R.id.vpnBadge).visibility = if (Vpn.state == Vpn.State.ON) View.VISIBLE else View.GONE

        links = readLinks(intent)
        if (links.isEmpty()) { finish(); return }
        initPlayer(intent.getIntExtra("index", 0).coerceIn(0, links.size - 1))
    }

    private fun readLinks(i: Intent): List<Link> {
        val urls = i.getStringArrayExtra("urls")
        if (urls != null) {
            val titles = i.getStringArrayExtra("titles") ?: emptyArray()
            return urls.mapIndexed { n, u -> Link(titles.getOrNull(n) ?: Ui.titleFromUrl(u), u) }
        }
        // opened from another app (ACTION_VIEW)
        val data = i.data ?: return emptyList()
        val name = if (data.scheme == "content") Ui.fileName(this, data) else Ui.titleFromUrl(data.toString())
        return listOf(Link(name, data.toString()))
    }

    private fun initPlayer(index: Int) {
        val http = OkHttpDataSource.Factory(Net.client).setUserAgent(Net.UA)
        val dataSource = DefaultDataSource.Factory(this, http)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
            .build()
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
        player = p
        playerView.player = p
        playerView.setShowNextButton(links.size > 1)
        playerView.setShowPreviousButton(links.size > 1)
        playerView.resizeMode = resizeMode
        applySubtitleStyle()

        p.setMediaItems(links.map { toItem(it) }, index, startPositionFor(links[index].url))
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                titleView.text = mediaItem?.mediaMetadata?.title ?: ""
                currentLink()?.let { Store.addHistory(it) }
            }

            override fun onPlayerError(error: PlaybackException) {
                MaterialAlertDialogBuilder(this@PlayerActivity)
                    .setTitle("تعذّر التشغيل")
                    .setMessage("الرابط لا يعمل أو غير مدعوم.\n" +
                            (if (Vpn.state != Vpn.State.ON) "جرّب تشغيل الـ VPN ثم أعد المحاولة.\n" else "") +
                            "\n(${error.errorCodeName})")
                    .setPositiveButton("إعادة المحاولة") { _, _ -> p.prepare(); p.play() }
                    .setNegativeButton("خروج") { _, _ -> finish() }
                    .show()
            }
        })
        titleView.text = links[index].title
        Store.addHistory(links[index])
        p.prepare()
        p.playWhenReady = true
    }

    private fun toItem(l: Link): MediaItem {
        val b = MediaItem.Builder()
            .setUri(l.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(l.title).build())
        val path = l.url.substringBefore('?').lowercase()
        when {
            path.endsWith(".m3u8") -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            path.endsWith(".mpd") -> b.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        return b.build()
    }

    private fun startPositionFor(url: String): Long =
        if (Store.resume) Store.position(url) else 0L

    private fun currentLink(): Link? {
        val p = player ?: return null
        return links.getOrNull(p.currentMediaItemIndex)
    }

    // ---------------- subtitles ----------------

    private fun subtitleMenu() {
        val p = player ?: return
        val textOn = !p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val options = arrayOf(
            "إضافة ملف ترجمة من الجهاز",
            "إضافة ترجمة من رابط",
            if (textOn) "إخفاء الترجمة" else "إظهار الترجمة",
            "تكبير الخط",
            "تصغير الخط",
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("الترجمة")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickSub.launch(arrayOf("*/*"))
                    1 -> Ui.input(this, "رابط الترجمة", "https://…/file.srt", ltr = true) { u ->
                        if (u.startsWith("http")) addSubtitle(Uri.parse(u), u)
                    }
                    2 -> p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, textOn).build()
                    3 -> { Store.subSize = (Store.subSize + 2).coerceAtMost(42); applySubtitleStyle() }
                    4 -> { Store.subSize = (Store.subSize - 2).coerceAtLeast(12); applySubtitleStyle() }
                }
            }
            .show()
    }

    private fun addSubtitle(uri: Uri, name: String) {
        val p = player ?: return
        val idx = p.currentMediaItemIndex
        val item = p.currentMediaItem ?: return
        val lower = name.lowercase()
        val mime = when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLanguage("ar")
            .setLabel(name)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val pos = p.currentPosition
        val newItem = item.buildUpon().setSubtitleConfigurations(listOf(sub)).build()
        p.removeMediaItem(idx)
        p.addMediaItem(idx, newItem)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage("ar")
            .build()
        p.seekTo(idx, pos)
        p.prepare()
        p.play()
        Toast.makeText(this, "تمت إضافة الترجمة", Toast.LENGTH_SHORT).show()
    }

    private fun applySubtitleStyle() {
        val sv = playerView.subtitleView ?: return
        sv.setStyle(
            CaptionStyleCompat(
                Store.subColor,
                if (Store.subBg) Color.argb(160, 0, 0, 0) else Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                Typeface.DEFAULT_BOLD
            )
        )
        sv.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, Store.subSize.toFloat())
    }

    // ---------------- speed & aspect ----------------

    private fun speedMenu() {
        val p = player ?: return
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = speeds.map { if (it == 1f) "عادي" else "${it}x" }.toTypedArray()
        val current = speeds.indexOfFirst { it == p.playbackParameters.speed }
        MaterialAlertDialogBuilder(this)
            .setTitle("سرعة التشغيل")
            .setSingleChoiceItems(labels, current) { d, w -> p.setPlaybackSpeed(speeds[w]); d.dismiss() }
            .show()
    }

    private fun cycleAspect() {
        val (mode, label) = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "تكبير"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL to "ملء الشاشة"
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT to "ملاءمة"
        }
        resizeMode = mode
        playerView.resizeMode = mode
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun savePosition() {
        val p = player ?: return
        val link = currentLink() ?: return
        val dur = p.duration
        val pos = p.currentPosition
        // don't resume live streams or finished videos
        if (dur > 0 && pos < dur - 10_000) Store.savePosition(link.url, pos)
        else Store.savePosition(link.url, 0)
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
