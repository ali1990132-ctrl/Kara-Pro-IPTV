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

/** محوّل بطاقات أفقية (بوسترات) للصفوف */
class PosterAdapter(
    private val onClick: (ContentItem) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    private val items = mutableListOf<ContentItem>()

    fun submit(list: List<ContentItem>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.poster)
        val name: TextView = v.findViewById(R.id.name)
        val rating: TextView = v.findViewById(R.id.rating)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_poster, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        val r = item.rating?.toFloatOrNull()
        if (r != null && r > 0f) {
            holder.rating.text = "${item.rating} ★"
            holder.rating.visibility = View.VISIBLE
        } else holder.rating.visibility = View.GONE
        holder.poster.load(item.logo) {
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
            crossfade(true)
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
