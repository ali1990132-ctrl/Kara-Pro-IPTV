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
        b.tvAutoUpdate.text = autoUpdateLabels()[Settings.autoUpdate(requireContext())]
        b.rowAutoUpdate.setOnClickListener { pickAutoUpdate() }
        b.rowLanguage.setOnClickListener { pickLanguage() }
        b.rowEngine.setOnClickListener { pickEngine() }
        b.rowChangePin.setOnClickListener { changePin() }
        b.rowPlaylists.setOnClickListener { PlaylistSheet().show(childFragmentManager, "playlists") }
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
        val errMsg = getString(R.string.err_network)
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // تحديث خفيف: نُفرغ الكاش ونعيد جلب القوائم فقط (بدون الكتالوج الضخم)
                com.karastore.iptv.data.ContentCache.clearAll()
                val live = com.karastore.iptv.data.ContentCache.load(ContentType.LIVE).size
                val movieCats = if (Repo.isXtream) com.karastore.iptv.data.ContentCache.ensureCategories(ContentType.MOVIE).size else 0
                val seriesCats = if (Repo.isXtream) com.karastore.iptv.data.ContentCache.ensureCategories(ContentType.SERIES).size else 0
                _b?.tvCounts?.text = ctx.getString(R.string.counts_fmt, live, movieCats, seriesCats)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _b?.tvCounts?.text = errMsg
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

    private fun autoUpdateLabels() = arrayOf(
        getString(R.string.au_manual), getString(R.string.au_on_open), getString(R.string.au_6h),
        getString(R.string.au_12h), getString(R.string.au_daily), getString(R.string.au_weekly)
    )

    private fun pickAutoUpdate() {
        val labels = autoUpdateLabels()
        val current = Settings.autoUpdate(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.auto_update)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                Settings.setAutoUpdate(requireContext(), which)
                b.tvAutoUpdate.text = labels[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun changePin() {
        val ctx = requireContext()
        val cur = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.pin_current)
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.pin_current)
            .setView(cur)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (cur.text.toString() != Settings.pin(ctx)) {
                    android.widget.Toast.makeText(ctx, R.string.pin_wrong, android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val neu = android.widget.EditText(ctx).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    hint = getString(R.string.pin_new)
                }
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.pin_new)
                    .setView(neu)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val v = neu.text.toString().trim()
                        if (v.length in 3..8) {
                            Settings.setPin(ctx, v)
                            android.widget.Toast.makeText(ctx, R.string.pin_changed, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
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
