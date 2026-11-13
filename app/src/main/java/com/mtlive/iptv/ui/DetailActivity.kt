package com.mtlive.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.gson.Gson
import com.mtlive.iptv.R
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.ContentItem
import com.mtlive.iptv.data.ContentType
import com.mtlive.iptv.data.Favorites
import com.mtlive.iptv.databinding.ActivityDetailBinding
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityDetailBinding
    private lateinit var item: ContentItem
    private var episodes: List<ContentItem> = emptyList()
    private lateinit var seasonAdapter: SeasonAdapter
    private lateinit var episodeAdapter: EpisodeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()

        item = Gson().fromJson(intent.getStringExtra(EXTRA_ITEM), ContentItem::class.java)

        b.tvName.text = item.name
        b.imgPoster.load(item.logo?.ifBlank { null }) {
            placeholder(R.drawable.poster_placeholder)
            error(R.drawable.poster_placeholder)
            fallback(R.drawable.poster_placeholder)
        }
        val r = item.rating?.toDoubleOrNull()
        b.tvMeta.text = if (r != null && r > 0) "★ " + String.format(java.util.Locale.ENGLISH, "%.1f", r) else ""

        b.btnBack.setOnClickListener { finish() }
        b.btnFav.setOnClickListener {
            Favorites.toggle(this, item.type, item.id)
            refreshFav()
        }
        refreshFav()

        seasonAdapter = SeasonAdapter { s -> showSeason(s) }
        b.rvSeasons.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvSeasons.adapter = seasonAdapter
        episodeAdapter = EpisodeAdapter { index, _ -> playEpisode(index) }
        b.rvEpisodes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvEpisodes.adapter = episodeAdapter

        if (item.type == ContentType.SERIES) loadSeries() else loadMovie()
        b.btnPlay.requestFocus()
    }

    private fun refreshFav() {
        val fav = Favorites.isFav(this, item.type, item.id)
        b.btnFav.setText(if (fav) R.string.remove_fav else R.string.add_fav)
        b.btnFav.setCompoundDrawablesRelativeWithIntrinsicBounds(if (fav) R.drawable.ic_star else R.drawable.ic_star_border, 0, 0, 0)
    }

    private fun setBackdrop(url: String?) {
        val u = url?.ifBlank { null } ?: item.logo?.ifBlank { null } ?: return
        b.imgBackdrop.load(u) { crossfade(true) }
    }

    private fun loadMovie() {
        b.btnPlay.setOnClickListener {
            PlayerQueue.set(listOf(item), 0)
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        b.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val info = try { Cache.session?.api()?.movieInfo(item.id) } catch (e: Exception) { null }
            b.progress.visibility = View.GONE
            setBackdrop(info?.backdrop)
            if (info != null) {
                val meta = listOfNotNull(
                    b.tvMeta.text.toString().ifBlank { null },
                    info.releaseDate?.take(4)?.ifBlank { null },
                    info.genre?.ifBlank { null },
                    info.duration?.ifBlank { null }
                )
                b.tvMeta.text = meta.joinToString("  •  ")
                b.tvPlot.text = info.plot ?: ""
            }
        }
    }

    private fun loadSeries() {
        b.btnPlay.setOnClickListener { if (episodes.isNotEmpty()) playEpisode(0) }
        b.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val d = Cache.session!!.api().seriesDetails(item.id)
                b.progress.visibility = View.GONE
                setBackdrop(d.backdrop)
                val meta = listOfNotNull(
                    b.tvMeta.text.toString().ifBlank { null },
                    d.releaseDate?.take(4)?.ifBlank { null },
                    d.genre?.ifBlank { null }
                )
                b.tvMeta.text = meta.joinToString("  •  ")
                b.tvPlot.text = d.plot ?: ""
                allEpisodes = d.episodes
                val seasons = d.episodes.mapNotNull { it.season }.distinct()
                if (seasons.isNotEmpty()) {
                    b.boxEpisodes.visibility = View.VISIBLE
                    seasonAdapter.selected = seasons.first()
                    seasonAdapter.submit(seasons)
                    showSeason(seasons.first())
                }
            } catch (e: Exception) {
                b.progress.visibility = View.GONE
                b.tvPlot.text = getString(R.string.load_error, e.message ?: "")
            }
        }
    }

    private var allEpisodes: List<ContentItem> = emptyList()

    private fun showSeason(season: String) {
        episodes = allEpisodes.filter { it.season == season }
        episodeAdapter.submit(episodes)
    }

    private fun playEpisode(index: Int) {
        if (episodes.isEmpty()) return
        PlayerQueue.set(episodes, index)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    companion object { const val EXTRA_ITEM = "item" }
}
