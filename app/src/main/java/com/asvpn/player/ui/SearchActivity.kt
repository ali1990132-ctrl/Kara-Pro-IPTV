package com.asvpn.player.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asvpn.player.R
import com.asvpn.player.data.Cache
import com.asvpn.player.data.ContentItem
import com.asvpn.player.data.ContentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var adapter: PosterAdapter
    private lateinit var state: TextView
    private var type: ContentType? = null
    private var job: Job? = null
    private var query = ""
    private val filterViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        state = findViewById(R.id.state)

        val grid = findViewById<RecyclerView>(R.id.grid)
        adapter = PosterAdapter { item, _ -> Nav.open(this, item, adapter.items) }
        grid.layoutManager = GridLayoutManager(this, Grid.span(grid, 115))
        grid.adapter = adapter

        val row = findViewById<LinearLayout>(R.id.filters)
        val opts = listOf<Pair<String, ContentType?>>(
            "الكل" to null, "أفلام" to ContentType.MOVIE, "مسلسلات" to ContentType.SERIES, "قنوات" to ContentType.LIVE
        )
        opts.forEachIndexed { i, (label, t) ->
            val tv = layoutInflater.inflate(R.layout.item_chip, row, false) as TextView
            tv.text = label
            tv.isSelected = i == 0
            tv.setOnClickListener {
                type = t
                filterViews.forEach { it.isSelected = it == tv }
                search()
            }
            filterViews.add(tv)
            row.addView(tv)
        }

        findViewById<EditText>(R.id.query).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                search()
            }
        })
    }

    private fun search() {
        job?.cancel()
        if (query.length < 2) {
            adapter.items = emptyList()
            state.visibility = View.VISIBLE
            state.text = "اكتب حرفين على الأقل للبحث"
            return
        }
        job = lifecycleScope.launch {
            delay(300)
            state.visibility = View.VISIBLE
            state.text = "جاري البحث…"
            val types = type?.let { listOf(it) } ?: listOf(ContentType.MOVIE, ContentType.SERIES, ContentType.LIVE)
            val results = mutableListOf<ContentItem>()
            for (t in types) {
                val list = Cache.items(t) ?: runCatching { Cache.load(t).second }.getOrNull() ?: continue
                results += list.filter { it.name.contains(query, ignoreCase = true) }.take(200)
            }
            adapter.items = results
            if (results.isEmpty()) state.text = "لا توجد نتائج" else state.visibility = View.GONE
        }
    }
}
