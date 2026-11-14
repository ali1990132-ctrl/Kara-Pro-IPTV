package com.karastore.iptv.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.karastore.iptv.R
import com.karastore.iptv.data.CategoryOrderStore
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.HiddenCategoriesStore
import com.karastore.iptv.data.LockedCategoriesStore
import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.FragmentContentBinding
import com.karastore.iptv.ui.ItemActions
import com.karastore.iptv.ui.adapters.CategorySectionAdapter
import com.karastore.iptv.ui.detail.DetailActivity
import com.karastore.iptv.ui.search.SearchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** شاشة الأفلام/المسلسلات/البث كصفوف أفقية حسب الباقة */
class ContentFragment : Fragment() {

    private var _b: FragmentContentBinding? = null
    private val b get() = _b!!

    private lateinit var type: ContentType
    private lateinit var sectionAdapter: CategorySectionAdapter
    private var loaded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentContentBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        type = ContentType.valueOf(arguments?.getString(ARG_TYPE) ?: ContentType.LIVE.name)

        b.tvSectionTitle.setText(
            when (type) {
                ContentType.MOVIE -> R.string.nav_movies
                ContentType.SERIES -> R.string.nav_series
                else -> R.string.nav_live
            }
        )
        b.tvWatermark.visibility = View.GONE

        sectionAdapter = CategorySectionAdapter(
            onItemClick = { item -> onItemClick(item) },
            loader = { catId, cb -> loadRow(catId, cb) }
        )
        b.rvRows.layoutManager = LinearLayoutManager(context)
        b.rvRows.adapter = sectionAdapter
        b.rvRows.setHasFixedSize(true)

        b.btnSearch.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        b.btnFilter.setOnClickListener { openFilter() }

        if (!loaded) loadCategories()
    }

    /** يحمّل قائمة الباقات (خفيفة) ثم يبني الصفوف */
    private fun loadCategories() {
        setLoading(true)
        val errMsg = getString(R.string.err_network)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ContentCache.ensureCategories(type)
                loaded = true
                buildRows()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_b == null) return@launch
                setLoading(false)
                toast(errMsg)
            }
        }
    }

    /** يرتّب الباقات (حسب التفضيل) ويستثني المخفية/المقفلة */
    private fun buildRows() {
        val ctx = context ?: return
        val hidden = HiddenCategoriesStore.get(ctx, type) + LockedCategoriesStore.get(ctx, type)
        val order = CategoryOrderStore.get(ctx, type)
        val visible: List<Category> = ContentCache.categoriesList(type)
            .filter { it.id !in hidden }
            .sortedBy { c -> val i = order.indexOf(c.id); if (i < 0) Int.MAX_VALUE else i }
        _b?.let {
            sectionAdapter.submit(visible)
            it.tvEmpty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        }
        setLoading(false)
    }

    /** يحمّل عناصر باقة واحدة (كسول) */
    private fun loadRow(categoryId: String?, cb: (List<ContentItem>) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = try {
                withContext(Dispatchers.IO) { ContentCache.loadCategory(type, categoryId) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { emptyList() }
            if (_b != null) cb(list)
        }
    }

    private fun openFilter() {
        if (!loaded) return
        val sheet = CategorySheet.newInstance(type)
        sheet.onPick = { _ -> buildRows() }
        sheet.onHiddenChanged = { buildRows() }
        sheet.show(childFragmentManager, "cats")
    }

    private fun onItemClick(item: ContentItem) {
        if (item.type == ContentType.LIVE) {
            ItemActions.play(requireActivity(), item)
        } else {
            startActivity(Intent(requireContext(), DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_ITEM, com.google.gson.Gson().toJson(item))
            })
        }
    }

    private fun setLoading(loading: Boolean) {
        _b ?: return
        b.progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) b.tvEmpty.visibility = View.GONE
    }

    private fun toast(msg: String) {
        val ctx = context ?: return
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_TYPE = "type"

        fun newInstance(type: ContentType): ContentFragment {
            return ContentFragment().apply {
                arguments = Bundle().apply { putString(ARG_TYPE, type.name) }
            }
        }
    }
}
