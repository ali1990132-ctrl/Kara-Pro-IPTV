package com.karastore.iptv.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.karastore.iptv.R
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.HiddenCategoriesStore
import com.karastore.iptv.data.LockedCategoriesStore
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.FragmentHomeBinding
import com.karastore.iptv.ui.adapters.FeaturedAdapter
import com.karastore.iptv.ui.adapters.PosterAdapter
import com.karastore.iptv.ui.detail.DetailActivity
import com.karastore.iptv.ui.search.SearchActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    private val handler = Handler(Looper.getMainLooper())
    private var autoSlide: Runnable? = null
    private var featuredCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnSearch.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        b.btnMenu.setOnClickListener {
            PlaylistSheet().show(childFragmentManager, "playlists")
        }

        b.rvRecentMovies.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        b.rvRecentSeries.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        if (Repo.isXtream) loadContent() else loadM3uHome()
    }

    private fun loadM3uHome() {
        b.pager.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ContentCache.load(ContentType.LIVE)
                val live = ContentCache.recentlyAdded(ContentType.LIVE, 30, emptySet())
                if (_b != null && live.isNotEmpty()) {
                    b.labelRecentMovies.setText(R.string.nav_live)
                    show(b.labelRecentMovies, b.rvRecentMovies, live)
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadContent() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ContentCache.load(ContentType.MOVIE)
                val hidden = HiddenCategoriesStore.get(requireContext(), ContentType.MOVIE) +
                    LockedCategoriesStore.get(requireContext(), ContentType.MOVIE)
                val recentM = ContentCache.recentlyAdded(ContentType.MOVIE, 15, hidden)
                setupFeatured(recentM.take(6))
                show(b.labelRecentMovies, b.rvRecentMovies, recentM)
            } catch (_: Exception) {}
            try {
                ContentCache.load(ContentType.SERIES)
                val hiddenS = HiddenCategoriesStore.get(requireContext(), ContentType.SERIES) +
                    LockedCategoriesStore.get(requireContext(), ContentType.SERIES)
                show(b.labelRecentSeries, b.rvRecentSeries, ContentCache.recentlyAdded(ContentType.SERIES, 15, hiddenS))
            } catch (_: Exception) {}
        }
    }

    private fun setupFeatured(items: List<ContentItem>) {
        if (_b == null || items.isEmpty()) return
        val adapter = FeaturedAdapter { item -> openDetail(item) }
        b.pager.adapter = adapter
        adapter.submit(items)
        featuredCount = items.size
        buildDots(items.size)

        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateDots(position) }
        })
        startAutoSlide()
    }

    private fun buildDots(count: Int) {
        b.dots.removeAllViews()
        val size = (6 * resources.displayMetrics.density).toInt()
        val margin = (3 * resources.displayMetrics.density).toInt()
        for (i in 0 until count) {
            val dot = ImageView(requireContext())
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginStart = margin; lp.marginEnd = margin
            dot.layoutParams = lp
            dot.setImageResource(if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive)
            b.dots.addView(dot)
        }
    }

    private fun updateDots(active: Int) {
        for (i in 0 until b.dots.childCount) {
            (b.dots.getChildAt(i) as? ImageView)?.setImageResource(
                if (i == active) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
    }

    private fun startAutoSlide() {
        stopAutoSlide()
        autoSlide = object : Runnable {
            override fun run() {
                val bb = _b ?: return
                if (featuredCount > 0) {
                    val next = (bb.pager.currentItem + 1) % featuredCount
                    bb.pager.setCurrentItem(next, true)
                }
                handler.postDelayed(this, 4500)
            }
        }
        handler.postDelayed(autoSlide!!, 4500)
    }

    private fun stopAutoSlide() {
        autoSlide?.let { handler.removeCallbacks(it) }
    }

    private fun openDetail(item: ContentItem) {
        startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_ITEM, Gson().toJson(item))
        })
    }

    private fun show(label: View, rv: RecyclerView, items: List<ContentItem>) {
        if (_b == null || items.isEmpty()) return
        label.visibility = View.VISIBLE
        val adapter = PosterAdapter { item -> openDetail(item) }
        rv.adapter = adapter
        adapter.submit(items)
    }

    override fun onResume() {
        super.onResume()
        if (featuredCount > 0) startAutoSlide()
    }

    override fun onPause() {
        super.onPause()
        stopAutoSlide()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoSlide()
        _b = null
    }
}
