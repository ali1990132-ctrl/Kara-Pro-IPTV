package com.asvpn.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.asvpn.player.R
import com.asvpn.player.data.ContentItem
import com.asvpn.player.data.ContentType

class PosterAdapter(
    private val horizontal: Boolean = false,
    private val itemWidthPx: Int = 0,
    private val onClick: (ContentItem, Int) -> Unit,
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    var items: List<ContentItem> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.img)
        val name: TextView = v.findViewById(R.id.name)
        val rating: TextView = v.findViewById(R.id.rating)
    }

    override fun getItemViewType(position: Int) = if (items[position].type == ContentType.LIVE) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 1) R.layout.item_channel else R.layout.item_poster
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        if (horizontal && itemWidthPx > 0) v.layoutParams = RecyclerView.LayoutParams(itemWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val it = items[position]
        h.name.text = it.name
        val r = it.rating?.toDoubleOrNull()
        if (r != null && r > 0) {
            h.rating.visibility = View.VISIBLE
            h.rating.text = "★ " + String.format(java.util.Locale.US, "%.1f", r)
        } else h.rating.visibility = View.GONE
        h.img.load(it.logo?.takeIf { s -> s.startsWith("http") }) {
            crossfade(true)
            placeholder(if (it.type == ContentType.LIVE) R.drawable.ic_live_tv else R.drawable.ic_movie)
            error(if (it.type == ContentType.LIVE) R.drawable.ic_live_tv else R.drawable.ic_movie)
        }
        h.itemView.setOnClickListener { _ -> onClick(it, h.bindingAdapterPosition) }
    }
}

class ChipAdapter(private val onPick: (Int) -> Unit) : RecyclerView.Adapter<ChipAdapter.VH>() {
    var labels: List<String> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }
    var selected = 0
        set(value) { val old = field; field = value; notifyItemChanged(old); notifyItemChanged(value) }

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_chip, parent, false) as TextView)

    override fun getItemCount() = labels.size

    override fun onBindViewHolder(h: VH, position: Int) {
        h.tv.text = labels[position]
        h.tv.isSelected = position == selected
        h.tv.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p >= 0) { selected = p; onPick(p) }
        }
    }
}

object Grid {
    /** number of columns that fits the screen */
    fun span(v: View, minDp: Int): Int {
        val dm = v.resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        return (widthDp / minDp).toInt().coerceAtLeast(3)
    }
}
