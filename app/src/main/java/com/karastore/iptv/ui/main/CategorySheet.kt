package com.karastore.iptv.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.karastore.iptv.R
import com.karastore.iptv.data.CategoryCount
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.HiddenCategoriesStore
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.SheetCategoriesBinding

/** قائمة التصنيفات بعدّاد وبحث */
class CategorySheet : BottomSheetDialogFragment() {

    private var _b: SheetCategoriesBinding? = null
    private val b get() = _b!!

    private lateinit var type: ContentType
    var onPick: ((String?) -> Unit)? = null
    var onHiddenChanged: (() -> Unit)? = null

    private val all = mutableListOf<CategoryCount>()
    private lateinit var adapter: CatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = SheetCategoriesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        type = ContentType.valueOf(arguments?.getString("type") ?: ContentType.LIVE.name)
        all.clear()
        all.addAll(ContentCache.categoriesWithCounts(type))

        adapter = CatAdapter()
        b.rvCats.layoutManager = LinearLayoutManager(context)
        b.rvCats.adapter = adapter
        adapter.submit(all)

        b.etCatSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                adapter.submit(if (q.isEmpty()) all else all.filter { it.name.contains(q, true) })
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun label(c: CategoryCount): String =
        if (c.id == null) getString(R.string.all) else c.name

    inner class CatAdapter : RecyclerView.Adapter<CatAdapter.VH>() {
        private val items = mutableListOf<CategoryCount>()
        fun submit(list: List<CategoryCount>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.catName)
            val count: TextView = v.findViewById(R.id.catCount)
            val sw: androidx.appcompat.widget.SwitchCompat = v.findViewById(R.id.catSwitch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category_row, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            holder.name.text = label(c)
            holder.count.text = c.count.toString()

            holder.sw.setOnCheckedChangeListener(null)
            if (c.id == null) {
                holder.sw.visibility = View.GONE
            } else {
                holder.sw.visibility = View.VISIBLE
                holder.sw.isChecked = !HiddenCategoriesStore.isHidden(requireContext(), type, c.id)
                holder.sw.setOnCheckedChangeListener { _, checked ->
                    HiddenCategoriesStore.setHidden(requireContext(), type, c.id, !checked)
                    onHiddenChanged?.invoke()
                }
            }

            holder.itemView.setOnClickListener {
                onPick?.invoke(c.id)
                dismiss()
            }
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        fun newInstance(type: ContentType): CategorySheet {
            return CategorySheet().apply {
                arguments = Bundle().apply { putString("type", type.name) }
            }
        }
    }
}
