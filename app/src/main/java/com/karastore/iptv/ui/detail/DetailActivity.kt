package com.karastore.iptv.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.gson.Gson
import com.karastore.iptv.R
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.Downloader
import com.karastore.iptv.data.FavoritesStore
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.ActivityDetailBinding
import com.karastore.iptv.ui.ItemActions
import com.karastore.iptv.ui.adapters.PosterAdapter
import com.karastore.iptv.ui.list.ContentListActivity

class DetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityDetailBinding
    private lateinit var item: ContentItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        val json = intent.getStringExtra(EXTRA_ITEM)
        item = try { Gson().fromJson(json, ContentItem::class.java) } catch (e: Exception) { finish(); return }

        bind()
        b.btnBack.setOnClickListener { finish() }
        b.btnStart.setOnClickListener { start() }
        b.btnFav.setOnClickListener { toggleFav() }
        b.btnDownload.setOnClickListener {
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            Downloader.start(this, item) { _, msg -> runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }
        }

        loadRelated()
    }

    private fun bind() {
        b.tvTitle.text = item.name
        b.imgBackdrop.load(item.logo) { error(R.drawable.poster_placeholder) }

        val catName = ContentCache.categoriesWithCounts(item.type)
            .firstOrNull { it.id == item.categoryId }?.name
        b.tvMeta.text = catName ?: ""
        b.tvMeta.visibility = if (catName.isNullOrBlank()) View.GONE else View.VISIBLE

        val r = item.rating?.toFloatOrNull()
        if (r != null && r > 0f) {
            b.tvRating.text = "${item.rating} ★"
            b.tvRating.visibility = View.VISIBLE
        } else b.tvRating.visibility = View.GONE

        // التحميل متاح للأفلام والحلقات (وعناصر M3U القابلة للتشغيل)
        val canDownload = item.type == ContentType.MOVIE ||
            (item.type == ContentType.SERIES && !item.directUrl.isNullOrBlank())
        b.btnDownload.visibility = if (canDownload) View.VISIBLE else View.GONE
        refreshFav()
    }

    private fun start() {
        // مسلسلات M3U = حلقة قابلة للتشغيل مباشرة
        if (item.type == ContentType.SERIES && !item.directUrl.isNullOrBlank()) {
            ItemActions.play(this, item); return
        }
        if (item.type == ContentType.SERIES) {
            startActivity(Intent(this, ContentListActivity::class.java).apply {
                putExtra(ContentListActivity.EXTRA_TYPE, ContentType.SERIES.name)
                putExtra(ContentListActivity.EXTRA_TITLE, item.name)
                putExtra(ContentListActivity.EXTRA_SERIES_ID, item.id)
                putExtra(ContentListActivity.EXTRA_SERIES_COVER, item.logo)
            })
        } else {
            ItemActions.play(this, item)
        }
    }

    private fun toggleFav() {
        FavoritesStore.toggle(this, item)
        refreshFav()
    }

    private fun refreshFav() {
        val fav = FavoritesStore.isFav(this, item.id)
        b.btnFav.setImageResource(if (fav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
    }

    private fun loadRelated() {
        val related = ContentCache.filter(item.type, item.categoryId)
            .filter { it.id != item.id }
            .take(20)
        if (related.isEmpty()) return
        b.tvRelatedLabel.visibility = View.VISIBLE
        val adapter = PosterAdapter { open ->
            startActivity(Intent(this, DetailActivity::class.java).apply {
                putExtra(EXTRA_ITEM, Gson().toJson(open))
            })
        }
        b.rvRelated.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvRelated.adapter = adapter
        adapter.submit(related)
    }

    companion object {
        const val EXTRA_ITEM = "item"
    }
}
