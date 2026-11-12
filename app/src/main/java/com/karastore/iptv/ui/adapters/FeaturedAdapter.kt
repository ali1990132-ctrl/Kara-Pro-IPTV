package com.karastore.iptv.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.karastore.iptv.R
import com.karastore.iptv.data.FavoritesStore
import com.karastore.iptv.data.model.ContentItem

/** محوّل الكاروسيل المميّز بالرئيسية */
class FeaturedAdapter(
    private val onOpen: (ContentItem) -> Unit
) : RecyclerView.Adapter<FeaturedAdapter.VH>() {

    private val items = mutableListOf<ContentItem>()

    fun submit(list: List<ContentItem>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val backdrop: ImageView = v.findViewById(R.id.backdrop)
        val title: TextView = v.findViewById(R.id.title)
        val rating: TextView = v.findViewById(R.id.rating)
        val btnPlay: TextView = v.findViewById(R.id.btnPlay)
        val btnFav: ImageView = v.findViewById(R.id.btnFav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_featured, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        holder.title.text = item.name
        holder.backdrop.load(item.logo) { error(R.drawable.ic_placeholder) }
        val r = item.rating?.toFloatOrNull()
        if (r != null && r > 0f) {
            holder.rating.text = "${item.rating} ★"
            holder.rating.visibility = View.VISIBLE
        } else holder.rating.visibility = View.GONE

        fun refreshFav() {
            val fav = FavoritesStore.isFav(ctx, item.id)
            holder.btnFav.setImageResource(if (fav) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        }
        refreshFav()

        holder.btnPlay.setOnClickListener { onOpen(item) }
        holder.backdrop.setOnClickListener { onOpen(item) }
        holder.btnFav.setOnClickListener {
            FavoritesStore.toggle(ctx, item)
            refreshFav()
        }
    }

    override fun getItemCount() = items.size
}
