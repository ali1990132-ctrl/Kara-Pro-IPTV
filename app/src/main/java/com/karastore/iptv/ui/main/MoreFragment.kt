package com.karastore.iptv.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.karastore.iptv.R
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.SessionStore
import com.karastore.iptv.data.Settings
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.FragmentMoreBinding
import com.karastore.iptv.ui.list.ContentListActivity
import com.karastore.iptv.ui.login.LoginActivity
import kotlinx.coroutines.launch

class MoreFragment : Fragment() {

    private var _b: FragmentMoreBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentMoreBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val session = Repo.session
        b.tvName.text = session?.name?.ifBlank { session.username }?.ifBlank { getString(R.string.app_name) }
            ?: getString(R.string.app_name)
        b.tvRemaining.text = remainingText()

        b.tvLangValue.text = currentLangLabel()
        b.tvEngineValue.text = getString(
            if (Settings.compatMode(requireContext())) R.string.engine_compat else R.string.engine_auto
        )

        b.rowSupport.setOnClickListener { openSupport() }
        b.rowFavorites.setOnClickListener { openList(ContentListActivity.SRC_FAVORITES, getString(R.string.menu_favorites)) }
        b.rowHistory.setOnClickListener { openList(ContentListActivity.SRC_HISTORY, getString(R.string.menu_history)) }
        b.rowDownloads.setOnClickListener { openList(ContentListActivity.SRC_DOWNLOADS, getString(R.string.menu_downloads)) }
        b.rowUpdate.setOnClickListener { loadCounts() }
        b.rowLanguage.setOnClickListener { pickLanguage() }
        b.rowEngine.setOnClickListener { pickEngine() }
        b.rowLogout.setOnClickListener { confirmLogout() }
    }

    private fun openSupport() {
        val num = getString(R.string.support_wa)
        val url = "https://wa.me/$num"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), getString(R.string.support_number), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun openList(source: String, title: String) {
        startActivity(Intent(requireContext(), ContentListActivity::class.java).apply {
            putExtra(ContentListActivity.EXTRA_SOURCE, source)
            putExtra(ContentListActivity.EXTRA_TITLE, title)
        })
    }

    private fun loadCounts() {
        b.updateProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                com.karastore.iptv.data.ContentCache.clearAll()
                val live = com.karastore.iptv.data.ContentCache.load(ContentType.LIVE).size
                val movies = if (Repo.isXtream) com.karastore.iptv.data.ContentCache.load(ContentType.MOVIE).size else 0
                val series = if (Repo.isXtream) com.karastore.iptv.data.ContentCache.load(ContentType.SERIES).size else 0
                _b?.tvCounts?.text = getString(R.string.counts_fmt, live, movies, series)
            } catch (e: Exception) {
                _b?.tvCounts?.text = getString(R.string.err_network)
            } finally {
                _b?.updateProgress?.visibility = View.GONE
            }
        }
    }

    private fun pickLanguage() {
        val labels = arrayOf(
            getString(R.string.lang_device), getString(R.string.lang_ar), getString(R.string.lang_en)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_language)
            .setItems(labels) { _, which ->
                val locales = when (which) {
                    1 -> LocaleListCompat.forLanguageTags("ar")
                    2 -> LocaleListCompat.forLanguageTags("en")
                    else -> LocaleListCompat.getEmptyLocaleList()
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }
            .show()
    }

    private fun pickEngine() {
        val labels = arrayOf(getString(R.string.engine_auto), getString(R.string.engine_compat))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_engine)
            .setItems(labels) { _, which ->
                Settings.setCompatMode(requireContext(), which == 1)
                b.tvEngineValue.text = labels[which]
            }
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.logout)
            .setPositiveButton(R.string.logout) { _, _ -> doLogout() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doLogout() {
        SessionStore.clear(requireContext())
        Repo.logout()
        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        requireActivity().finish()
    }

    private fun currentLangLabel(): String {
        val tags = AppCompatDelegate.getApplicationLocales()
        return when (tags.toLanguageTags().lowercase().take(2)) {
            "ar" -> getString(R.string.lang_ar)
            "en" -> getString(R.string.lang_en)
            else -> getString(R.string.lang_device)
        }
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
