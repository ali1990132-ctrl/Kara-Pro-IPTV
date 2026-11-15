package com.asvpn.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asvpn.player.R
import com.asvpn.player.data.Cache
import com.asvpn.player.data.Category
import com.asvpn.player.data.ContentItem
import com.asvpn.player.data.ContentType
import kotlinx.coroutines.launch

class SectionFragment : Fragment() {

    companion object {
        fun of(t: ContentType) = SectionFragment().apply { arguments = Bundle().apply { putString("type", t.name) } }
    }

    private val type by lazy { ContentType.valueOf(requireArguments().getString("type") ?: ContentType.MOVIE.name) }
    private var cats: List<Category> = emptyList()
    private var all: List<ContentItem> = emptyList()
    private lateinit var chips: ChipAdapter
    private lateinit var posters: PosterAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_section, c, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        chips = ChipAdapter { pos -> filter(pos) }
        v.findViewById<RecyclerView>(R.id.cats).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = chips
        }
        posters = PosterAdapter { item, _ -> Nav.open(requireActivity(), item, posters.items) }
        val grid = v.findViewById<RecyclerView>(R.id.grid)
        grid.layoutManager = GridLayoutManager(requireContext(), Grid.span(v, if (type == ContentType.LIVE) 130 else 115))
        grid.adapter = posters
        v.findViewById<View>(R.id.btnRetry).setOnClickListener { load(true) }
        load(false)
    }

    private fun showState(loading: Boolean, msg: String?, retry: Boolean = false) {
        val v = view ?: return
        v.findViewById<View>(R.id.stateBox).visibility = if (loading || msg != null) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.stateText).apply {
            visibility = if (msg != null || loading) View.VISIBLE else View.GONE
            text = msg ?: "جاري التحميل…"
        }
        v.findViewById<View>(R.id.btnRetry).visibility = if (retry) View.VISIBLE else View.GONE
    }

    private fun load(force: Boolean) {
        if (Cache.session?.isExpired() == true) {
            showState(false, "الاشتراك منتهي — جدّد الباقة لعرض المحتوى"); return
        }
        showState(true, null)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (c, i) = Cache.load(type, force)
                all = i
                val used = i.mapNotNull { it.categoryId }.toSet()
                cats = c.filter { it.id in used }
                chips.labels = listOf("الكل") + cats.map { it.name }
                chips.selected = 0
                filter(0)
            } catch (e: Exception) {
                showState(false, "تعذّر التحميل — تأكد من الإنترنت أو شغّل الـ VPN", retry = true)
            }
        }
    }

    private fun filter(pos: Int) {
        val list = if (pos == 0) {
            if (type == ContentType.LIVE) all else all.sortedByDescending { it.added ?: 0 }
        } else {
            val id = cats.getOrNull(pos - 1)?.id
            all.filter { it.categoryId == id }
        }
        posters.items = list
        view?.findViewById<RecyclerView>(R.id.grid)?.scrollToPosition(0)
        if (list.isEmpty()) showState(false, "لا يوجد محتوى في هذا القسم") else showState(false, null)
    }
}
