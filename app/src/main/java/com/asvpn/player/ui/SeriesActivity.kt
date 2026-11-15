package com.asvpn.player.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.asvpn.player.R
import com.asvpn.player.data.Cache
import com.asvpn.player.data.ContentItem
import com.asvpn.player.data.Link
import kotlinx.coroutines.launch

class SeriesActivity : AppCompatActivity() {

    private var seasons: List<String> = emptyList()
    private var episodes: List<ContentItem> = emptyList()
    private var shown: List<ContentItem> = emptyList()
    private lateinit var epAdapter: RowAdapter
    private lateinit var seasonAdapter: ChipAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)
        val id = intent.getStringExtra("id") ?: run { finish(); return }
        findViewById<TextView>(R.id.title).text = intent.getStringExtra("name") ?: ""
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.cover).load(intent.getStringExtra("cover")) {
            crossfade(true); error(R.drawable.ic_movie)
        }

        seasonAdapter = ChipAdapter { pos -> showSeason(pos) }
        findViewById<RecyclerView>(R.id.seasons).apply {
            layoutManager = LinearLayoutManager(this@SeriesActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = seasonAdapter
        }
        epAdapter = RowAdapter(onClick = { pos ->
            if (pos in shown.indices) {
                PlayerActivity.start(this, shown.map { Link("${intent.getStringExtra("name") ?: ""} - ${it.name}", it.url ?: "") }, pos)
            }
        })
        findViewById<RecyclerView>(R.id.episodes).apply {
            layoutManager = LinearLayoutManager(this@SeriesActivity)
            adapter = epAdapter
        }

        val state = findViewById<TextView>(R.id.state)
        lifecycleScope.launch {
            try {
                val api = Cache.session?.api() ?: throw RuntimeException("سجّل الدخول من جديد")
                val d = api.seriesDetails(id)
                findViewById<TextView>(R.id.plot).text = d.plot ?: ""
                findViewById<TextView>(R.id.meta).text =
                    listOfNotNull(d.genre?.ifBlank { null }, d.releaseDate?.take(4)?.ifBlank { null }).joinToString(" • ")
                episodes = d.episodes
                seasons = episodes.mapNotNull { it.season }.distinct()
                seasonAdapter.labels = seasons.map { "الموسم $it" }
                if (episodes.isEmpty()) state.text = "لا توجد حلقات" else { state.visibility = View.GONE; showSeason(0) }
            } catch (e: Exception) {
                state.text = "تعذّر التحميل — تأكد من الإنترنت أو شغّل الـ VPN"
            }
        }
    }

    private fun showSeason(pos: Int) {
        val s = seasons.getOrNull(pos)
        shown = if (s == null) episodes else episodes.filter { it.season == s }
        epAdapter.rows = shown.mapIndexed { i, e -> Row(e.name, "الحلقة ${i + 1}") }
    }
}
