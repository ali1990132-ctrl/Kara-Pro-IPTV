package com.mtlive.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mtlive.iptv.R
import com.mtlive.iptv.data.ContentItem
import com.mtlive.iptv.databinding.ItemCategoryBinding
import com.mtlive.iptv.databinding.ItemChannelBinding
import com.mtlive.iptv.databinding.ItemEpisodeBinding
import com.mtlive.iptv.databinding.ItemPosterBinding
import com.mtlive.iptv.databinding.ItemSeasonBinding

data class CatRow(val key: String, val name: String, val count: Int)

class CategoryAdapter(private val onClick: (CatRow) -> Unit) : RecyclerView.Adapter<CategoryAdapter.VH>() {
    var rows: List<CatRow> = emptyList(); private set
    var selectedKey: String = ""

    fun submit(list: List<CatRow>) { rows = list; notifyDataSetChanged() }

    class VH(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemCategoryBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun getItemCount() = rows.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val r = rows[pos]
        h.b.tvName.text = r.name
        h.b.tvCount.text = r.count.toString()
        val sel = r.key == selectedKey
        h.b.root.isSelected = sel
        val ctx = h.b.root.context
        val c = ContextCompat.getColor(ctx, if (sel) R.color.red else R.color.white)
        h.b.tvName.setTextColor(c)
        h.b.tvCount.setTextColor(if (sel) c else ContextCompat.getColor(ctx, R.color.text_secondary))
        h.b.root.setOnClickListener {
            val old = rows.indexOfFirst { it.key == selectedKey }
            selectedKey = r.key
            if (old >= 0) notifyItemChanged(old)
            notifyItemChanged(h.bindingAdapterPosition)
            onClick(r)
        }
    }
}

class PosterAdapter(
    private val posterHeightPx: Int,
    private val onClick: (ContentItem) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {
    private var items: List<ContentItem> = emptyList()
    fun submit(list: List<ContentItem>) { items = list; notifyDataSetChanged() }

    class VH(val b: ItemPosterBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val vh = VH(ItemPosterBinding.inflate(LayoutInflater.from(p.context), p, false))
        vh.b.img.layoutParams.height = posterHeightPx
        return vh
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.b.tvName.text = item.name
        h.b.img.load(item.logo?.ifBlank { null }) {
            placeholder(R.drawable.poster_placeholder)
            error(R.drawable.poster_placeholder)
            fallback(R.drawable.poster_placeholder)
            crossfade(true)
        }
        val r = item.rating?.toDoubleOrNull()
        if (r != null && r > 0) {
            h.b.tvRating.visibility = View.VISIBLE
            h.b.tvRating.text = "★ " + String.format(java.util.Locale.ENGLISH, "%.1f", r)
        } else h.b.tvRating.visibility = View.GONE
        h.b.root.setOnClickListener { _ -> onClick(item) }
    }
}

class ChannelAdapter(
    private val isFav: (String) -> Boolean,
    private val onClick: (Int, ContentItem) -> Unit,
    private val onLongClick: (ContentItem) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    var items: List<ContentItem> = emptyList(); private set
    fun submit(list: List<ContentItem>) { items = list; notifyDataSetChanged() }

    class VH(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemChannelBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.b.tvNum.text = (pos + 1).toString()
        h.b.tvName.text = item.name
        h.b.imgFav.visibility = if (isFav(item.id)) View.VISIBLE else View.GONE
        h.b.img.load(item.logo?.ifBlank { null }) {
            placeholder(R.drawable.channel_placeholder)
            error(R.drawable.channel_placeholder)
            fallback(R.drawable.channel_placeholder)
        }
        h.b.root.setOnClickListener { _ -> onClick(h.bindingAdapterPosition, item) }
        h.b.root.setOnLongClickListener { _ -> onLongClick(item); notifyItemChanged(h.bindingAdapterPosition); true }
    }
}

class SeasonAdapter(private val onClick: (String) -> Unit) : RecyclerView.Adapter<SeasonAdapter.VH>() {
    private var seasons: List<String> = emptyList()
    var selected: String = ""
    fun submit(list: List<String>) { seasons = list; notifyDataSetChanged() }

    class VH(val b: ItemSeasonBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemSeasonBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun getItemCount() = seasons.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = seasons[pos]
        h.b.tvSeason.text = "الموسم $s"
        h.b.tvSeason.isSelected = s == selected
        h.b.tvSeason.setOnClickListener {
            selected = s
            notifyDataSetChanged()
            onClick(s)
        }
    }
}

class EpisodeAdapter(private val onClick: (Int, ContentItem) -> Unit) : RecyclerView.Adapter<EpisodeAdapter.VH>() {
    var items: List<ContentItem> = emptyList(); private set
    fun submit(list: List<ContentItem>) { items = list; notifyDataSetChanged() }

    class VH(val b: ItemEpisodeBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemEpisodeBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.b.tvName.text = "${pos + 1}. ${item.name}"
        h.b.img.load(item.logo?.ifBlank { null }) {
            placeholder(R.drawable.channel_placeholder)
            error(R.drawable.channel_placeholder)
            fallback(R.drawable.channel_placeholder)
        }
        h.b.root.setOnClickListener { _ -> onClick(h.bindingAdapterPosition, item) }
    }
}
