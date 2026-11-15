package com.asvpn.player.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asvpn.player.R
import com.asvpn.player.data.Cache
import com.asvpn.player.data.ContentType
import com.asvpn.player.data.SessionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class DashboardFragment : Fragment() {

    private lateinit var moviesAdapter: PosterAdapter
    private lateinit var seriesAdapter: PosterAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_dashboard, c, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        bindAccount()
        v.findViewById<View>(R.id.btnLogout).setOnClickListener {
            Ui.confirm(requireContext(), "تسجيل الخروج من الحساب؟") {
                SessionStore.clear(requireContext())
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                activity?.finish()
            }
        }

        setupCard(v.findViewById(R.id.cardLive), "البث المباشر", R.drawable.ic_live_tv, R.drawable.bg_icon_live, 1)
        setupCard(v.findViewById(R.id.cardMovies), "الأفلام", R.drawable.ic_movie, R.drawable.bg_icon_movie, 2)
        setupCard(v.findViewById(R.id.cardSeries), "المسلسلات", R.drawable.ic_tv, R.drawable.bg_icon_series, 3)
        v.findViewById<View>(R.id.allMovies).setOnClickListener { (activity as? MainActivity)?.select(2) }
        v.findViewById<View>(R.id.allSeries).setOnClickListener { (activity as? MainActivity)?.select(3) }

        val w = (118 * resources.displayMetrics.density).toInt()
        moviesAdapter = PosterAdapter(true, w) { item, _ -> Nav.open(requireActivity(), item, moviesAdapter.items) }
        seriesAdapter = PosterAdapter(true, w) { item, _ -> Nav.open(requireActivity(), item, seriesAdapter.items) }
        v.findViewById<RecyclerView>(R.id.rowMovies).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = moviesAdapter
        }
        v.findViewById<RecyclerView>(R.id.rowSeries).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = seriesAdapter
        }
        loadContent()
    }

    fun bindAccount() {
        val v = view ?: return
        val s = Cache.session ?: return
        v.findViewById<TextView>(R.id.avatar).text = s.username.take(1).uppercase()
        v.findViewById<TextView>(R.id.hello).text = "أهلاً، ${s.username}"
        val status = v.findViewById<TextView>(R.id.status)
        val expiry = v.findViewById<TextView>(R.id.expiry)
        val ctx = requireContext()
        if (s.isExpired()) {
            status.text = "● الاشتراك منتهي"
            status.setBackgroundResource(R.drawable.bg_status_bad)
            status.setTextColor(ContextCompat.getColor(ctx, R.color.red))
        } else {
            status.text = "● الاشتراك فعّال"
            status.setBackgroundResource(R.drawable.bg_status_ok)
            status.setTextColor(ContextCompat.getColor(ctx, R.color.green))
        }
        val days = s.daysLeft()
        expiry.text = when {
            s.expiryText() == null -> "غير محدود"
            s.isExpired() -> "انتهى ${s.expiryText()}"
            days != null && days <= 3 -> "متبقي $days يوم"
            else -> "ينتهي ${s.expiryText()}"
        }
        expiry.setTextColor(
            ContextCompat.getColor(ctx, if (days != null && days <= 3) R.color.orange else R.color.text2)
        )
    }

    private fun setupCard(card: View, title: String, icon: Int, bg: Int, tab: Int) {
        card.findViewById<TextView>(R.id.secTitle).text = title
        card.findViewById<ImageView>(R.id.secIcon).apply { setImageResource(icon); setBackgroundResource(bg) }
        card.setOnClickListener { (activity as? MainActivity)?.select(tab) }
    }

    private fun count(id: Int, n: Int, unit: String) {
        val v = view ?: return
        v.findViewById<View>(id).findViewById<TextView>(R.id.secCount).text =
            NumberFormat.getInstance(Locale.US).format(n) + " " + unit
    }

    private fun loadContent() {
        val state = view?.findViewById<TextView>(R.id.loadState) ?: return
        if (Cache.session?.isExpired() == true) {
            state.text = "الاشتراك منتهي — جدّد الباقة لعرض المحتوى"
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val live = async { runCatching { Cache.load(ContentType.LIVE).second } }
            val movies = async { runCatching { Cache.load(ContentType.MOVIE).second } }
            val series = async { runCatching { Cache.load(ContentType.SERIES).second } }

            movies.await().getOrNull()?.let { list ->
                count(R.id.cardMovies, list.size, "فيلم")
                moviesAdapter.items = list.sortedByDescending { it.added ?: 0 }.take(20)
            }
            series.await().getOrNull()?.let { list ->
                count(R.id.cardSeries, list.size, "مسلسل")
                seriesAdapter.items = list.sortedByDescending { it.added ?: 0 }.take(20)
            }
            live.await().getOrNull()?.let { count(R.id.cardLive, it.size, "قناة") }

            val failed = listOf(live, movies, series).all { it.await().isFailure }
            state.visibility = if (failed) View.VISIBLE else View.GONE
            if (failed) state.text = "تعذّر تحميل المحتوى — تأكد من الإنترنت أو شغّل الـ VPN"
        }
    }
}
