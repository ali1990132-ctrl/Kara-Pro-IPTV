package com.mtlive.iptv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.mtlive.iptv.R
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.Category
import com.mtlive.iptv.data.ContentItem
import com.mtlive.iptv.data.ContentType
import com.mtlive.iptv.data.Favorites
import com.mtlive.iptv.data.HiddenCategories
import com.mtlive.iptv.data.HistoryStore
import com.mtlive.iptv.data.LockedCategories
import com.mtlive.iptv.data.Settings
import com.mtlive.iptv.data.XtreamApi
import com.mtlive.iptv.databinding.ActivitySectionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class SectionActivity : AppCompatActivity() {

    private lateinit var b: ActivitySectionBinding
    private lateinit var type: ContentType

    private var allItems: List<ContentItem> = emptyList()
    private var categories: List<Category> = emptyList()
    private var currentKey = KEY_ALL
    private var currentName = ""
    private var query = ""
    private val unlocked = HashSet<String>()

    private lateinit var catAdapter: CategoryAdapter
    private var posterAdapter: PosterAdapter? = null
    private var channelAdapter: ChannelAdapter? = null

    // مربع المعاينة
    private var previewEnabled = false
    private var preview: ExoPlayer? = null
    private var previewId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        b = ActivitySectionBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()

        type = ContentType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: ContentType.MOVIE.name)
        currentKey = intent.getStringExtra(EXTRA_KEY) ?: KEY_ALL
        currentName = getString(if (currentKey == KEY_FAV) R.string.favorites else R.string.all)
        previewEnabled = type == ContentType.LIVE && Settings.livePreview(this)

        setupTopbar(b.topbar, when (type) { ContentType.LIVE -> Tabs.LIVE; ContentType.MOVIE -> Tabs.MOVIES; else -> Tabs.SERIES })

        catAdapter = CategoryAdapter(
            onClick = { row -> openCategory(row) },
            onLongClick = { row -> categoryMenu(row) }
        )
        catAdapter.selectedKey = currentKey
        b.rvCategories.layoutManager = LinearLayoutManager(this)
        b.rvCategories.adapter = catAdapter

        setupGrid()

        b.btnBack.setOnClickListener { finish() }
        b.btnSearch.setOnClickListener { toggleSearch() }
        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; applyFilter() }
        })
        b.tvMessage.setOnClickListener { load(true) }
        b.previewBox.setOnClickListener { indexOfPreview().let { if (it >= 0) openFullscreen(it) } }

        load(false)
    }

    private fun setupGrid() {
        val dm = resources.displayMetrics
        val panelPx = (250 * dm.density).toInt()
        var contentPx = dm.widthPixels - panelPx - (20 * dm.density).toInt()
        if (type == ContentType.LIVE) {
            if (previewEnabled) {
                // المربع ياخذ تقريباً 45% من المساحة
                val boxW = (contentPx * 0.45f).toInt()
                b.previewPanel.layoutParams.width = boxW
                b.previewBox.layoutParams.height = boxW * 9 / 16
                b.previewPanel.visibility = View.VISIBLE
                b.previewWatermark.visibility = if (Settings.watermark(this)) View.VISIBLE else View.GONE
                contentPx -= boxW + (10 * dm.density).toInt()
            }
            val span = ((contentPx / dm.density) / 290).toInt().coerceAtLeast(1)
            b.rvItems.layoutManager = GridLayoutManager(this, span)
            channelAdapter = ChannelAdapter(
                isFav = { id -> Favorites.isFav(this, type, id) },
                onClick = { index, _ -> onChannelClick(index) },
                onLongClick = { item ->
                    val now = Favorites.toggle(this, type, item.id)
                    Toast.makeText(this, if (now) R.string.add_fav else R.string.remove_fav, Toast.LENGTH_SHORT).show()
                    refreshCategoryCounts()
                }
            )
            b.rvItems.adapter = channelAdapter
        } else {
            val contentDp = contentPx / dm.density
            val span = (contentDp / 118).toInt().coerceAtLeast(3)
            val itemW = contentPx / span
            val posterH = ((itemW - 10 * dm.density) * 1.5f).toInt()
            b.rvItems.layoutManager = GridLayoutManager(this, span)
            posterAdapter = PosterAdapter(posterH) { item -> openDetail(item) }
            b.rvItems.adapter = posterAdapter
        }
        b.rvItems.setHasFixedSize(true)
        b.rvItems.setItemViewCacheSize(30)
    }

    private fun load(force: Boolean) {
        b.progress.visibility = View.VISIBLE
        b.tvMessage.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val (c, i) = Cache.load(type, force)
                categories = c
                allItems = i
                refreshCategoryCounts()
                applyFilter()
                b.progress.visibility = View.GONE
                b.rvCategories.post { b.rvCategories.getChildAt(0)?.requestFocus() }
            } catch (e: Exception) {
                b.progress.visibility = View.GONE
                b.tvMessage.visibility = View.VISIBLE
                b.tvMessage.text = getString(R.string.load_error, e.message ?: "") + "\n\n" + getString(R.string.retry)
            }
        }
    }

    /** العناصر بعد استبعاد الباقات المخفية */
    private fun visibleItems(): List<ContentItem> {
        val hidden = HiddenCategories.get(this, type)
        return if (hidden.isEmpty()) allItems else allItems.filter { it.categoryId !in hidden }
    }

    private fun refreshCategoryCounts() {
        val hidden = HiddenCategories.get(this, type)
        val locked = LockedCategories.get(this, type)
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.Default) {
                val visible = if (hidden.isEmpty()) allItems else allItems.filter { it.categoryId !in hidden }
                val counts = visible.groupingBy { it.categoryId ?: "" }.eachCount()
                val favs = Favorites.ids(this@SectionActivity, type)
                val list = mutableListOf(
                    CatRow(KEY_ALL, getString(R.string.all), visible.size, special = true),
                    CatRow(KEY_FAV, getString(R.string.favorites), visible.count { it.id in favs }, special = true)
                )
                if (type != ContentType.LIVE) list.add(CatRow(KEY_RECENT, getString(R.string.recent), minOf(RECENT_LIMIT, visible.size), special = true))
                categories.filter { it.id !in hidden }.forEach { c ->
                    list.add(CatRow(c.id, c.name, counts[c.id] ?: 0, locked = c.id in locked))
                }
                list
            }
            catAdapter.submit(rows)
        }
    }

    private fun openCategory(row: CatRow) {
        if (row.locked && row.key !in unlocked) {
            askPin {
                unlocked.add(row.key)
                openCategory(row)
            }
            return
        }
        catAdapter.select(row.key)
        currentKey = row.key
        currentName = row.name
        applyFilter()
        b.rvItems.scrollToPosition(0)
    }

    private fun categoryMenu(row: CatRow) {
        val locked = LockedCategories.has(this, type, row.key)
        val opts = arrayOf(getString(if (locked) R.string.cat_unlock else R.string.cat_lock), getString(R.string.cat_hide))
        AlertDialog.Builder(this)
            .setTitle(row.name)
            .setItems(opts) { _, w ->
                if (w == 0) {
                    if (locked) askPin { LockedCategories.set(this, type, row.key, false); refreshCategoryCounts() }
                    else { LockedCategories.set(this, type, row.key, true); unlocked.remove(row.key); refreshCategoryCounts() }
                } else {
                    val doHide = {
                        HiddenCategories.set(this, type, row.key, true)
                        if (currentKey == row.key) { currentKey = KEY_ALL; currentName = getString(R.string.all); catAdapter.select(KEY_ALL) }
                        refreshCategoryCounts(); applyFilter()
                    }
                    if (locked) askPin { doHide() } else doHide()
                }
            }.show()
    }

    private fun askPin(onOk: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "••••"
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_enter)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (input.text.toString() == Settings.pin(this)) onOk()
                else Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyFilter() {
        val key = currentKey
        val q = query
        lifecycleScope.launch {
            val list = withContext(Dispatchers.Default) {
                val base = visibleItems()
                var l = when (key) {
                    KEY_ALL -> base
                    KEY_FAV -> { val f = Favorites.ids(this@SectionActivity, type); base.filter { it.id in f } }
                    KEY_RECENT -> base.sortedByDescending { it.added ?: 0L }.take(RECENT_LIMIT)
                    else -> base.filter { it.categoryId == key }
                }
                // الباقات المقفولة ما تطلع بـ "الكل" إلا بعد فتحها
                if (key == KEY_ALL || key == KEY_RECENT || key == KEY_FAV) {
                    val locked = LockedCategories.get(this@SectionActivity, type) - unlocked
                    if (locked.isNotEmpty()) l = l.filter { it.categoryId !in locked }
                }
                if (q.isNotEmpty()) l = l.filter { it.name.contains(q, ignoreCase = true) }
                l
            }
            if (key != currentKey || q != query) return@launch
            posterAdapter?.submit(list)
            channelAdapter?.submit(list)
            b.tvTitle.text = "$currentName (${list.size})"
            if (list.isEmpty() && b.progress.visibility != View.VISIBLE) {
                b.tvMessage.visibility = View.VISIBLE
                b.tvMessage.text = getString(R.string.no_items)
            } else if (b.tvMessage.text == getString(R.string.no_items)) {
                b.tvMessage.visibility = View.GONE
            }
        }
    }

    private fun toggleSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (b.etSearch.visibility == View.VISIBLE) {
            b.etSearch.setText("")
            b.etSearch.visibility = View.GONE
            b.spacer.visibility = View.VISIBLE
            imm.hideSoftInputFromWindow(b.etSearch.windowToken, 0)
        } else {
            b.etSearch.visibility = View.VISIBLE
            b.spacer.visibility = View.GONE
            b.etSearch.requestFocus()
            imm.showSoftInput(b.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // ------------------- البث المباشر -------------------

    /** أول ضغطة تشغّل القناة بالمربع، والضغطة الثانية (أو على المربع) تفتح الشاشة الكاملة */
    private fun onChannelClick(index: Int) {
        val id = channelAdapter?.items?.getOrNull(index)?.id
        if (!previewEnabled || (id != null && id == previewId)) openFullscreen(index)
        else playPreview(index)
    }

    private fun indexOfPreview(): Int = channelAdapter?.items?.indexOfFirst { it.id == previewId } ?: -1

    private fun playPreview(index: Int) {
        val ch = channelAdapter?.items?.getOrNull(index) ?: return
        previewId = ch.id
        b.previewName.text = ch.name
        b.previewLogo.visibility = View.GONE
        b.previewError.visibility = View.GONE
        b.previewProgress.visibility = View.VISIBLE

        val p = preview ?: buildPreviewPlayer().also { preview = it; b.previewPlayer.player = it }
        p.setMediaItem(MediaItem.fromUri(Uri.parse(ch.url ?: "")))
        p.prepare()
        p.playWhenReady = true
        HistoryStore.add(this, ch)
    }

    private fun buildPreviewPlayer(): ExoPlayer {
        val http = OkHttpDataSource.Factory(XtreamApi.client).setUserAgent(XtreamApi.UA)
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                b.previewProgress.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                b.previewProgress.visibility = View.GONE
                b.previewError.visibility = View.VISIBLE
            }
        })
        return exo
    }

    private fun releasePreview() {
        preview?.release()
        preview = null
        b.previewPlayer.player = null
        b.previewProgress.visibility = View.GONE
        if (previewId == null) b.previewLogo.visibility = View.VISIBLE
    }

    private fun openFullscreen(index: Int) {
        val list = channelAdapter?.items ?: return
        if (index !in list.indices) return
        PlayerQueue.set(list, index)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun openDetail(item: ContentItem) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ITEM, Gson().toJson(item)))
    }

    override fun onResume() {
        super.onResume()
        if (::catAdapter.isInitialized && allItems.isNotEmpty()) {
            refreshCategoryCounts()
            if (currentKey == KEY_FAV) applyFilter()
        }
        // رجعنا من الشاشة الكاملة: نكمل نفس القناة بالمربع
        if (previewEnabled && ::b.isInitialized && previewId != null && preview == null) {
            val cur = PlayerQueue.items.getOrNull(PlayerQueue.index)
            var i = cur?.let { c -> channelAdapter?.items?.indexOfFirst { it.id == c.id } } ?: -1
            if (i < 0) i = indexOfPreview()
            if (i >= 0) { playPreview(i); b.rvItems.scrollToPosition(i) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::b.isInitialized) releasePreview()
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_KEY = "key"
        const val KEY_ALL = "__all"
        const val KEY_FAV = "__fav"
        const val KEY_RECENT = "__recent"
        private const val RECENT_LIMIT = 200

        fun intent(ctx: Context, type: ContentType, startKey: String? = null) =
            Intent(ctx, SectionActivity::class.java).putExtra(EXTRA_TYPE, type.name).apply {
                if (startKey != null) putExtra(EXTRA_KEY, startKey)
            }
    }
}

/** قائمة التشغيل الحالية للمشغّل (لتنقّل القنوات) */
object PlayerQueue {
    var items: List<ContentItem> = emptyList(); private set
    var index: Int = 0

    fun set(list: List<ContentItem>, i: Int) {
        val from = (i - 500).coerceAtLeast(0)
        val to = (i + 500).coerceAtMost(list.size)
        items = list.subList(from, to).toList()
        index = i - from
    }
}
