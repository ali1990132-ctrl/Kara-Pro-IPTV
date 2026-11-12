package com.karastore.iptv.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.karastore.iptv.R
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.FragmentHomeBinding
import com.karastore.iptv.ui.adapters.PosterAdapter
import com.karastore.iptv.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val session = Repo.session
        val name = session?.name?.ifBlank { session.username }?.ifBlank { getString(R.string.app_name) }
            ?: getString(R.string.app_name)
        b.tvName.text = name
        b.tvRemaining.text = remainingText()

        if (!Repo.isXtream) {
            b.tileMovies.visibility = View.GONE
            b.tileSeries.visibility = View.GONE
        }

        b.tileLive.setOnClickListener { (activity as? MainActivity)?.goToTab(R.id.nav_live) }
        b.tileMovies.setOnClickListener { (activity as? MainActivity)?.goToTab(R.id.nav_movies) }
        b.tileSeries.setOnClickListener { (activity as? MainActivity)?.goToTab(R.id.nav_series) }

        if (Repo.isXtream) loadRecent()
    }

    private fun loadRecent() {
        b.rvRecentMovies.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        b.rvRecentSeries.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ContentCache.load(ContentType.MOVIE)
                show(b.labelRecentMovies, b.rvRecentMovies, ContentCache.recentlyAdded(ContentType.MOVIE, 15))
            } catch (_: Exception) {}
            try {
                ContentCache.load(ContentType.SERIES)
                show(b.labelRecentSeries, b.rvRecentSeries, ContentCache.recentlyAdded(ContentType.SERIES, 15))
            } catch (_: Exception) {}
        }
    }

    private fun show(label: View, rv: androidx.recyclerview.widget.RecyclerView, items: List<ContentItem>) {
        if (_b == null || items.isEmpty()) return
        label.visibility = View.VISIBLE
        val adapter = PosterAdapter { item ->
            startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_ITEM, Gson().toJson(item))
            })
        }
        rv.adapter = adapter
        adapter.submit(items)
    }

    private fun remainingText(): String {
        val exp = Repo.userInfo?.expDate?.toLongOrNull() ?: return getString(R.string.unlimited)
        val days = Math.ceil((exp * 1000 - System.currentTimeMillis()) / 86_400_000.0).toInt()
        return "$days ${getString(R.string.unit_days)}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
