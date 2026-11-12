package com.karastore.iptv.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.karastore.iptv.R
import com.karastore.iptv.data.Repo
import com.karastore.iptv.databinding.FragmentHomeBinding

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
    }

    private fun remainingText(): String {
        val exp = Repo.userInfo?.expDate?.toLongOrNull()
            ?: return getString(R.string.unlimited)
        val diffMs = exp * 1000 - System.currentTimeMillis()
        val days = Math.ceil(diffMs / 86_400_000.0).toInt()
        return "$days ${getString(R.string.unit_days)}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
