package com.karastore.iptv.ui.list

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.karastore.iptv.R
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.ActivityContentListBinding
import com.karastore.iptv.ui.adapters.CategoryAdapter
import com.karastore.iptv.ui.adapters.ContentAdapter
import com.karastore.iptv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class ContentListActivity : AppCompatActivity() {

    private lateinit var b: ActivityContentListBinding
    private lateinit var type: ContentType
    private var seriesId: String? = null

    private lateinit var catAdapter: CategoryAdapter
    private lateinit var contentAdapter: ContentAdapter
    private var categories: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityContentListBinding.inflate(layoutInflater)
        setContentView(b.root)

        type = ContentType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: ContentType.LIVE.name)
        seriesId = intent.getStringExtra(EXTRA_SERIES_ID)
        b.tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: ""

        b.btnBack.setOnClickListener { finish() }

        catAdapter = CategoryAdapter { pos -> onCategorySelected(pos) }
        b.rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvCategories.adapter = catAdapter

        contentAdapter = ContentAdapter { item -> onItemClick(item) }
        b.rvContent.layoutManager = GridLayoutManager(this, 3)
        b.rvContent.adapter = contentAdapter

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { contentAdapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        if (seriesId != null) {
            // وضع الحلقات: لا تصنيفات
            b.rvCategories.visibility = View.GONE
            loadEpisodes()
        } else {
            loadCategories()
        }
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
        val id = if (cat.id == ALL_ID) null else cat.id
        loadContent(id)
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
            // فتح حلقات المسلسل
            startActivity(Intent(this, ContentListActivity::class.java).apply {
                putExtra(EXTRA_TYPE, ContentType.SERIES.name)
                putExtra(EXTRA_TITLE, item.name)
                putExtra(EXTRA_SERIES_ID, item.id)
            })
            return
        }
        val url = item.directUrl
        if (url.isNullOrBlank()) { toast(getString(R.string.player_error)); return }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, url)
            putExtra(PlayerActivity.EXTRA_TITLE, item.name)
        })
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
        private const val ALL_ID = "__all__"
    }
}
