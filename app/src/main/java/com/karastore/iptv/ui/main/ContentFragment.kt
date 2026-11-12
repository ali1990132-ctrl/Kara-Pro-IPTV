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

        contentAdapter = ContentAdapter(
            onClick = { item -> onItemClick(item) },
            onLongClick = { item -> if (item.type != ContentType.SERIES) ItemActions.show(requireActivity(), item) },
            onDownload = { item -> ItemActions.download(requireActivity(), item) }
        )
        b.rvContent.layoutManager = GridLayoutManager(context, 3)
        b.rvContent.adapter = contentAdapter

        b.btnFilter.setOnClickListener { openFilter() }

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { contentAdapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        if (!loaded) loadAll()
    }

    private fun loadAll() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ContentCache.load(type)
                loaded = true
                applyCategory(null, getString(R.string.all))
            } catch (e: Exception) {
                setLoading(false)
                toast(getString(R.string.err_network))
            }
        }
    }

    private fun openFilter() {
        if (!ContentCache.isLoaded(type)) return
        val sheet = CategorySheet.newInstance(type)
        sheet.onPick = { catId ->
            val label = if (catId == null) getString(R.string.all)
            else ContentCache.categoriesWithCounts(type).firstOrNull { it.id == catId }?.name ?: ""
            applyCategory(catId, label)
        }
        sheet.onHiddenChanged = { applyCategory(currentCat, currentLabel) }
        sheet.show(childFragmentManager, "cats")
    }

    private fun applyCategory(categoryId: String?, label: String) {
        currentCat = categoryId
        currentLabel = label
        val hidden = HiddenCategoriesStore.get(requireContext(), type)
        val items = ContentCache.filter(type, categoryId, hidden)
        _b?.let {
            it.tvCatLabel.text = label
            it.etSearch.text?.clear()
            contentAdapter.submit(items)
            it.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
        setLoading(false)
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

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

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
