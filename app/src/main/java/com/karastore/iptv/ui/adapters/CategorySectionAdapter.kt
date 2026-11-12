package com.karastore.iptv.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karastore.iptv.R
import com.karastore.iptv.data.model.Category
import com.karastore.iptv.data.model.ContentItem

/**
 * صفوف أفقية حسب الباقة: كل باقة لها عنوان وعدّاد وصف بوسترات،
 * وتُحمَّل عناصرها كسولاً عند ظهور الصف.
 */
class CategorySectionAdapter(
    private val onItemClick: (ContentItem) -> Unit,
    /** يحمّل عناصر باقة ويرجّعها عبر callback */
    private val loader: (categoryId: String?, cb: (List<ContentItem>) -> Unit) -> Unit
) : RecyclerView.Adapter<CategorySectionAdapter.VH>() {

    private val cats = mutableListOf<Category>()
    private val cache = HashMap<String, List<ContentItem>>()

    fun submit(list: List<Category>) {
        cats.clear(); cats.addAll(list); notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.secTitle)
        val count: TextView = v.findViewById(R.id.secCount)
        val row: RecyclerView = v.findViewById(R.id.secRow)
        val rowAdapter = PosterAdapter { onItemClick(it) }
        var boundId: String? = null

        init {
            row.layoutManager = LinearLayoutManager(v.context, LinearLayoutManager.HORIZONTAL, false)
            row.adapter = rowAdapter
            row.setHasFixedSize(true)
            row.setRecycledViewPool(sharedPool)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category_section, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = cats[position]
        holder.title.text = c.name
        holder.boundId = c.id

        val cached = cache[c.id]
        if (cached != null) {
            holder.count.text = cached.size.toString()
            holder.rowAdapter.submit(cached)
        } else {
            holder.count.text = ""
            holder.rowAdapter.submit(emptyList())
            val wanted = c.id
            loader(c.id) { list ->
                cache[c.id] = list
                // تأكّد أن الصف ما زال لنفس الباقة (بسبب إعادة الاستخدام)
                if (holder.boundId == wanted) {
                    holder.count.text = list.size.toString()
                    holder.rowAdapter.submit(list)
                }
            }
        }
    }

    override fun getItemCount() = cats.size

    companion object {
        private val sharedPool = RecyclerView.RecycledViewPool()
    }
}
