package com.karastore.iptv.ui.main

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.karastore.iptv.R
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.FragmentContentBinding
import com.karastore.iptv.ui.ItemActions
import com.karastore.iptv.ui.adapters.CategoryAdapter
import com.karastore.iptv.ui.adapters.ContentAdapter
import com.karastore.iptv.ui.list.ContentListActivity
import kotlinx.coroutines.launch

class ContentFragment : Fragment() {

    private var _b: FragmentContentBinding? = null
    private val b get() = _b!!

    private lateinit var type: ContentType
    private lateinit var catAdapter: CategoryAdapter
    private lateinit var contentAdapter: ContentAdapter
    private var categories: List<Category> = emptyList()
    private var loaded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentContentBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        type = ContentType.valueOf(arguments?.getString(ARG_TYPE) ?: ContentType.LIVE.name)

        catAdapter = CategoryAdapter { pos -> onCategorySelected(pos) }
        b.rvCategories.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        b.rvCategories.adapter = catAdapter

        contentAdapter = ContentAdapter(
            onClick = { item -> onItemClick(item) },
            onLongClick = { item -> if (item.type != ContentType.SERIES) ItemActions.show(requireActivity(), item) }
        )
        b.rvContent.layoutManager = GridLayoutManager(context, 3)
        b.rvContent.adapter = contentAdapter

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { contentAdapter.filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        if (!loaded) loadCategories()
    }

    private fun loadCategories() {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = Repo.categories(type)
                categories = listOf(Category(ALL_ID, getString(R.string.all))) + list
                catAdapter.submit(categories)
                loadContent(null)
                loaded = true
            } catch (e: Exception) {
                setLoading(false)
                toast(getString(R.string.err_network))
            }
        }
    }

    private fun onCategorySelected(pos: Int) {
        val cat = categories.getOrNull(pos) ?: return
        loadContent(if (cat.id == ALL_ID) null else cat.id)
    }

    private fun loadContent(categoryId: String?) {
        setLoading(true)
        b.etSearch.text?.clear()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = Repo.content(type, categoryId)
                contentAdapter.submit(items)
                setLoading(false)
                b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                setLoading(false)
                toast(getString(R.string.err_network))
            }
        }
    }

    private fun onItemClick(item: ContentItem) {
        if (item.type == ContentType.SERIES) {
            startActivity(android.content.Intent(requireContext(), ContentListActivity::class.java).apply {
                putExtra(ContentListActivity.EXTRA_TYPE, ContentType.SERIES.name)
                putExtra(ContentListActivity.EXTRA_TITLE, item.name)
                putExtra(ContentListActivity.EXTRA_SERIES_ID, item.id)
            })
        } else {
            ItemActions.play(requireActivity(), item)
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
        private const val ALL_ID = "__all__"

        fun newInstance(type: ContentType): ContentFragment {
            return ContentFragment().apply {
                arguments = Bundle().apply { putString(ARG_TYPE, type.name) }
            }
        }
    }
}
