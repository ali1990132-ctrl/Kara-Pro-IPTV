package com.mtlive.iptv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.mtlive.iptv.R
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.Category
import com.mtlive.iptv.data.ContentItem
import com.mtlive.iptv.data.ContentType
import com.mtlive.iptv.data.Favorites
import com.mtlive.iptv.databinding.ActivitySectionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SectionActivity : AppCompatActivity() {

    private lateinit var b: ActivitySectionBinding
    private lateinit var type: ContentType

    private var allItems: List<ContentItem> = emptyList()
    private var categories: List<Category> = emptyList()
    private var currentKey = KEY_ALL
    private var currentName = ""
    private var query = ""

    private lateinit var catAdapter: CategoryAdapter
    private var posterAdapter: PosterAdapter? = null
    private var channelAdapter: ChannelAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        b = ActivitySectionBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()

        type = ContentType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: ContentType.MOVIE.name)
        currentName = getString(R.string.all)

        setupTopbar(b.topbar, when (type) { ContentType.LIVE -> Tabs.LIVE; ContentType.MOVIE -> Tabs.MOVIES; else -> Tabs.SERIES })

        catAdapter = CategoryAdapter { row -> selectCategory(row.key, row.name) }
        catAdapter.selectedKey = KEY_ALL
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

        load(false)
    }

    private fun setupGrid() {
        val dm = resources.displayMetrics
        val panelPx = (250 * dm.density).toInt()
        val contentPx = dm.widthPixels - panelPx - (20 * dm.density).toInt()
        val contentDp = contentPx / dm.density
        if (type == ContentType.LIVE) {
            val span = (contentDp / 290).toInt().coerceAtLeast(1)
            b.rvItems.layoutManager = GridLayoutManager(this, span)
            channelAdapter = ChannelAdapter(
                isFav = { id -> Favorites.isFav(this, type, id) },
                onClick = { index, _ -> playLive(index) },
                onLongClick = { item ->
                    val now = Favorites.toggle(this, type, item.id)
                    Toast.makeText(this, if (now) R.string.add_fav else R.string.remove_fav, Toast.LENGTH_SHORT).show()
                    refreshCategoryCounts()
                }
            )
            b.rvItems.adapter = channelAdapter
        } else {
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

    private fun refreshCategoryCounts() {
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.Default) {
                val counts = allItems.groupingBy { it.categoryId ?: "" }.eachCount()
                val favs = Favorites.ids(this@SectionActivity, type)
                val list = mutableListOf(
                    CatRow(KEY_ALL, getString(R.string.all), allItems.size),
                    CatRow(KEY_FAV, getString(R.string.favorites), allItems.count { it.id in favs })
                )
                if (type != ContentType.LIVE) list.add(CatRow(KEY_RECENT, getString(R.string.recent), minOf(RECENT_LIMIT, allItems.size)))
                categories.forEach { c -> list.add(CatRow(c.id, c.name, counts[c.id] ?: 0)) }
                list
            }
            catAdapter.submit(rows)
        }
    }

    private fun selectCategory(key: String, name: String) {
        currentKey = key
        currentName = name
        applyFilter()
        b.rvItems.scrollToPosition(0)
    }

    private fun applyFilter() {
        val key = currentKey
        val q = query
        lifecycleScope.launch {
            val list = withContext(Dispatchers.Default) {
                var l = when (key) {
                    KEY_ALL -> allItems
                    KEY_FAV -> { val f = Favorites.ids(this@SectionActivity, type); allItems.filter { it.id in f } }
                    KEY_RECENT -> allItems.sortedByDescending { it.added ?: 0L }.take(RECENT_LIMIT)
                    else -> allItems.filter { it.categoryId == key }
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

    private fun playLive(index: Int) {
        val list = channelAdapter?.items ?: return
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
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val KEY_ALL = "__all"
        private const val KEY_FAV = "__fav"
        private const val KEY_RECENT = "__recent"
        private const val RECENT_LIMIT = 200

        fun intent(ctx: Context, type: ContentType) =
            Intent(ctx, SectionActivity::class.java).putExtra(EXTRA_TYPE, type.name)
    }
}

/** قائمة التشغيل الحالية للمشغّل (لتنقّل القنوات) */
object PlayerQueue {
    var items: List<ContentItem> = emptyList(); private set
    var index: Int = 0

    fun set(list: List<ContentItem>, i: Int) {
        // نحدّد المدى حتى لا تكبر القائمة كثيراً
        val from = (i - 500).coerceAtLeast(0)
        val to = (i + 500).coerceAtMost(list.size)
        items = list.subList(from, to).toList()
        index = i - from
    }
}
