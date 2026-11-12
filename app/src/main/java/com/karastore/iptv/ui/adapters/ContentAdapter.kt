package com.karastore.iptv.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.karastore.iptv.R
import com.karastore.iptv.data.model.ContentItem

class ContentAdapter(
    private val onClick: (ContentItem) -> Unit,
    private val onLongClick: ((ContentItem) -> Unit)? = null
) : RecyclerView.Adapter<ContentAdapter.VH>() {

    private val all = mutableListOf<ContentItem>()
    private val shown = mutableListOf<ContentItem>()

    fun submit(list: List<ContentItem>) {
        all.clear(); all.addAll(list)
        shown.clear(); shown.addAll(list)
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        shown.clear()
        if (query.isBlank()) shown.addAll(all)
        else shown.addAll(all.filter { it.name.contains(query, ignoreCase = true) })
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.poster)
        val name: TextView = v.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_content, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = shown[position]
        holder.name.text = item.name
        holder.poster.load(item.logo) {
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
            crossfade(true)
        }
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            onLongClick != null
        }
    }

    override fun getItemCount() = shown.size
}
