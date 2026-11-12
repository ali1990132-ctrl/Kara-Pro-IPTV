package com.karastore.iptv.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.Gson
import com.karastore.iptv.R
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.ActivitySearchBinding
import com.karastore.iptv.ui.ItemActions
import com.karastore.iptv.ui.adapters.ContentAdapter
import com.karastore.iptv.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var b: ActivitySearchBinding
    private lateinit var adapter: ContentAdapter
    private val pool = mutableListOf<ContentItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }

        adapter = ContentAdapter(
            onClick = { item -> onItemClick(item) },
            onLongClick = { item -> if (item.type != ContentType.SERIES) ItemActions.show(this, item) }
        )
        b.rvResults.layoutManager = GridLayoutManager(this, 3)
        b.rvResults.adapter = adapter

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query(s?.toString()?.trim().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        loadPool()
    }

    private fun loadPool() {
        b.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                ContentCache.load(ContentType.LIVE)
                pool.addAll(ContentCache.all(ContentType.LIVE))
                if (Repo.isXtream) {
                    ContentCache.load(ContentType.MOVIE)
                    pool.addAll(ContentCache.all(ContentType.MOVIE))
                    ContentCache.load(ContentType.SERIES)
                    pool.addAll(ContentCache.all(ContentType.SERIES))
                }
            } catch (_: Exception) {}
            b.progress.visibility = View.GONE
            b.etSearch.requestFocus()
        }
    }

    private fun query(q: String) {
        if (q.length < 2) { adapter.submit(emptyList()); b.tvEmpty.visibility = View.GONE; return }
        val res = pool.asSequence().filter { it.name.contains(q, true) }.take(300).toList()
        adapter.submit(res)
        b.tvEmpty.visibility = if (res.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onItemClick(item: ContentItem) {
        if (item.type == ContentType.LIVE) {
            ItemActions.play(this, item)
        } else {
            startActivity(Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_ITEM, Gson().toJson(item))
            })
        }
    }
}
