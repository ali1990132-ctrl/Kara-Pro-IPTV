package com.karastore.iptv.ui.list

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.karastore.iptv.R
import com.karastore.iptv.data.DownloadsStore
import com.karastore.iptv.data.Downloader
import com.karastore.iptv.data.FavoritesStore
import com.karastore.iptv.data.HistoryStore
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.ActivityContentListBinding
import com.karastore.iptv.ui.ItemActions
import com.karastore.iptv.ui.adapters.CategoryAdapter
import com.karastore.iptv.ui.adapters.ContentAdapter
import kotlinx.coroutines.launch

class ContentListActivity : AppCompatActivity() {

    private lateinit var b: ActivityContentListBinding
    private lateinit var type: ContentType
    private var seriesId: String? = null
    private var source: String? = null

    private lateinit var catAdapter: CategoryAdapter
    private lateinit var contentAdapter: ContentAdapter
    private var categories: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityContentListBinding.inflate(layoutInflater)
        setContentView(b.root)

        type = ContentType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: ContentType.LIVE.name)
        seriesId = intent.getStringExtra(EXTRA_SERIES_ID)
        source = intent.getStringExtra(EXTRA_SOURCE)
        b.tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: ""

        b.btnBack.setOnClickListener { finish() }

        catAdapter = CategoryAdapter { pos -> onCategorySelected(pos) }
        b.rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvCategories.adapter = catAdapter

        val isDownloads = source == SRC_DOWNLOADS
        contentAdapter = ContentAdapter(
            onClick = { item -> onItemClick(item) },
            onLongClick = { item -> onItemLongClick(item) },
            onDownload = if (isDownloads) null else { item -> ItemActions.download(this, item) },
            downloadingProvider = if (!isDownloads) null else { item ->
                if (Downloader.isDownloading(item.id)) Downloader.progress(item.id) else null
            }
        )
        b.rvContent.layoutManager = GridLayoutManager(this, 3)
        b.rvContent.adapter = contentAdapter

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { contentAdapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        when {
            source != null -> { b.rvCategories.visibility = View.GONE; loadSource() }
            seriesId != null -> { b.rvCategories.visibility = View.GONE; loadEpisodes() }
            else -> loadCategories()
        }
    }

    private val pollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            loadSource()
            pollHandler.postDelayed(this, 1500)
        }
    }

    override fun onResume() {
        super.onResume()
        // تحديث القوائم المحلية عند الرجوع
        if (source != null) loadSource()
        // تحديث دوري لإظهار تقدّم التنزيل
        if (source == SRC_DOWNLOADS) {
            pollHandler.removeCallbacks(pollRunnable)
            pollHandler.postDelayed(pollRunnable, 1500)
        }
    }

    override fun onPause() {
        super.onPause()
        pollHandler.removeCallbacks(pollRunnable)
    }

    private fun loadSource() {
        val items: List<ContentItem> = when (source) {
            SRC_FAVORITES -> FavoritesStore.all(this)
            SRC_HISTORY -> HistoryStore.all(this)
            SRC_DOWNLOADS -> DownloadsStore.all(this)
            else -> emptyList()
        }
        contentAdapter.submit(items)
        b.tvEmpty.text = when (source) {
            SRC_FAVORITES -> getString(R.string.empty_favorites)
            SRC_HISTORY -> getString(R.string.empty_history)
            SRC_DOWNLOADS -> getString(R.string.empty_downloads)
            else -> getString(R.string.empty_list)
        }
        b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadCategories() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val list = Repo.categories(type)
                categories = listOf(Category(ALL_ID, getString(R.string.all))) + list
                catAdapter.submit(categories)
                loadContent(null)
            } catch (e: Exception) {
                setLoading(false)
                toast(getString(R.string.err_network))
            }
        }
    }

    private fun onCategorySelected(pos: Int) {
        val cat = categories.getOrNull(pos) ?: return
        loadContent(if (cat.id == ALL_ID) null else cat.id)
    }

    private fun loadContent(categoryId: String?) {
        setLoading(true)
        b.etSearch.text?.clear()
        lifecycleScope.launch {
            try {
                val items = Repo.content(type, categoryId)
                contentAdapter.submit(items)
                setLoading(false)
                b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                setLoading(false)
                toast(getString(R.string.err_network))
            }
        }
    }

    private fun loadEpisodes() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val items = Repo.episodes(seriesId!!)
                contentAdapter.submit(items)
                setLoading(false)
                b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                setLoading(false)
                toast(getString(R.string.err_network))
            }
        }
    }

    private fun onItemClick(item: ContentItem) {
        if (item.type == ContentType.SERIES) {
            startActivity(Intent(this, ContentListActivity::class.java).apply {
                putExtra(EXTRA_TYPE, ContentType.SERIES.name)
                putExtra(EXTRA_TITLE, item.name)
                putExtra(EXTRA_SERIES_ID, item.id)
            })
            return
        }
        ItemActions.play(this, item)
    }

    private fun onItemLongClick(item: ContentItem) {
        if (item.type == ContentType.SERIES) return
        if (source == SRC_DOWNLOADS) {
            // في التنزيلات: تشغيل أو حذف
            AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(arrayOf(getString(R.string.action_play), getString(R.string.action_delete))) { _, which ->
                    if (which == 0) ItemActions.play(this, item)
                    else {
                        Downloader.deleteFile(item)
                        DownloadsStore.remove(this, item.id)
                        loadSource()
                    }
                }.show()
        } else {
            ItemActions.show(this, item)
        }
    }

    private fun setLoading(loading: Boolean) {
        b.progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) b.tvEmpty.visibility = View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SOURCE = "source"
        const val SRC_FAVORITES = "favorites"
        const val SRC_HISTORY = "history"
        const val SRC_DOWNLOADS = "downloads"
        private const val ALL_ID = "__all__"
    }
}
