package com.karastore.iptv.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.karastore.iptv.R
import com.karastore.iptv.data.model.Category

class CategoryAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private val items = mutableListOf<Category>()
    private var selected = 0

    fun submit(list: List<Category>) {
        items.clear()
        items.addAll(list)
        selected = 0
        notifyDataSetChanged()
    }

    fun setSelected(pos: Int) {
        val old = selected
        selected = pos
        notifyItemChanged(old)
        notifyItemChanged(pos)
    }

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false) as TextView
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.tv.text = c.name
        holder.tv.isSelected = position == selected
        holder.tv.setOnClickListener {
            if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            setSelected(holder.bindingAdapterPosition)
            onClick(holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount() = items.size

    fun itemAt(pos: Int): Category? = items.getOrNull(pos)
}
