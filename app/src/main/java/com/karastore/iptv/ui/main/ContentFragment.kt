package com.karastore.iptv.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.karastore.iptv.R
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.HiddenCategoriesStore
import com.karastore.iptv.data.LockedCategoriesStore
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.FragmentContentBinding
import com.karastore.iptv.ui.ItemActions
import com.karastore.iptv.ui.adapters.ContentAdapter
import com.karastore.iptv.ui.detail.DetailActivity
import kotlinx.coroutines.launch

class ContentFragment : Fragment() {

    private var _b: FragmentContentBinding? = null
    private val b get() = _b!!

    private lateinit var type: ContentType
    private lateinit var contentAdapter: ContentAdapter
    private var loaded = false
    private var currentCat: String? = null
    private var currentLabel: String = ""

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
        if (type == ContentType.MOVIE || type == ContentType.SERIES) {
            b.tvWatermark.text = com.karastore.iptv.util.Watermark.text(requireContext())
            b.tvWatermark.visibility = View.VISIBLE
        } else b.tvWatermark.visibility = View.GONE

        contentAdapter = ContentAdapter(
            onClick = { item -> onItemClick(item) },
            onLongClick = { item -> if (!item.directUrl.isNullOrBlank()) ItemActions.show(requireActivity(), item) },
            onDownload = { item -> ItemActions.download(requireActivity(), item) }
        )
        b.rvContent.layoutManager = GridLayoutManager(context, gridSpan())
        b.rvContent.setHasFixedSize(true)
        b.rvContent.adapter = contentAdapter

        b.btnFilter.setOnClickListener { openFilter() }

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { contentAdapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        if (!loaded) loadAll()
    }

    /** يحمّل كل المحتوى ثم يعرض "الكل" */
    private fun loadAll() {
        setLoading(true)
        val allLabel = getString(R.string.all)
        val errMsg = getString(R.string.err_network)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ContentCache.load(type)
                loaded = true
                selectCategory(null, allLabel)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_b == null) return@launch
                setLoading(false)
                toast(errMsg)
            }
        }
    }

    /** أعمدة الشبكة: أكثر على التلفزيون الأفقي */
    private fun gridSpan(): Int {
        val cfg = resources.configuration
        val isTv = cfg.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val landscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (isTv || landscape) 6 else 3
    }

    private fun openFilter() {
        if (!loaded) return
        val sheet = CategorySheet.newInstance(type)
        sheet.onPick = { catId ->
            val label = if (catId == null) getString(R.string.all)
            else ContentCache.categoriesWithCounts(type).firstOrNull { it.id == catId }?.name ?: ""
            selectCategory(catId, label)
        }
        sheet.onHiddenChanged = { /* يُطبَّق عند الاختيار التالي */ }
        sheet.show(childFragmentManager, "cats")
    }

    /** يحمّل عناصر باقة واحدة فقط (سريع) */
    private fun selectCategory(categoryId: String?, label: String) {
        currentCat = categoryId
        currentLabel = label
        setLoading(true)
        _b?.let {
            it.tvCatLabel.text = label
            it.etSearch.text?.clear()
        }
        val errMsg = getString(R.string.err_network)
        val excluded = context?.let {
            HiddenCategoriesStore.get(it, type) + LockedCategoriesStore.get(it, type)
        } ?: emptySet()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var list = ContentCache.loadCategory(type, categoryId)
                // في عرض "الكل": استثنِ الباقات المخفية/المقفلة وحدّ العدد
                if (categoryId == null) {
                    if (excluded.isNotEmpty()) list = list.filter { it.categoryId !in excluded }
                    if (list.size > 1500) list = list.take(1500)
                }
                val items = list
                _b?.let {
                    contentAdapter.submit(items)
                    it.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
                setLoading(false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_b == null) return@launch
                setLoading(false)
                toast(errMsg)
            }
        }
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
