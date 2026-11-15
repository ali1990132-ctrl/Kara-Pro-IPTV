package com.asvpn.player.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asvpn.player.R
import com.asvpn.player.data.Playlist
import com.asvpn.player.data.Store
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ListsFragment : Fragment() {

    private lateinit var adapter: RowAdapter
    private var lists: MutableList<Playlist> = mutableListOf()

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ctx = requireContext()
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }
            Importer.importM3uText(this, Ui.fileName(ctx, uri).substringBeforeLast('.'), text)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_lists, c, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        adapter = RowAdapter(
            onClick = { pos -> lists.getOrNull(pos)?.let { open(it) } },
            onDelete = { pos ->
                lists.getOrNull(pos)?.let { pl ->
                    Ui.confirm(requireContext(), "حذف قائمة \"${pl.name}\"؟") {
                        lists.removeAt(pos); Store.savePlaylists(lists); load()
                    }
                }
            },
            onLong = { pos ->
                lists.getOrNull(pos)?.let { pl ->
                    Ui.input(requireContext(), "إعادة تسمية", "اسم القائمة", pl.name) { n ->
                        if (n.isNotBlank()) { pl.name = n; Store.savePlaylists(lists); load() }
                    }
                }
            }
        )
        v.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ListsFragment.adapter
        }
        v.findViewById<View>(R.id.btnAdd).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("إضافة قائمة")
                .setItems(arrayOf("قائمة فارغة جديدة", "استيراد M3U من رابط", "استيراد ملف M3U من الجهاز")) { _, which ->
                    when (which) {
                        0 -> Ui.input(requireContext(), "قائمة جديدة", "اسم القائمة") { n ->
                            if (n.isNotBlank()) {
                                val pl = Playlist(System.currentTimeMillis(), n, mutableListOf())
                                lists.add(0, pl); Store.savePlaylists(lists); open(pl)
                            }
                        }
                        1 -> Importer.askM3u(this)
                        2 -> pickFile.launch(arrayOf("*/*"))
                    }
                }
                .show()
        }
    }

    private fun open(pl: Playlist) {
        startActivity(Intent(requireContext(), PlaylistActivity::class.java).putExtra("id", pl.id))
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val v = view ?: return
        lists = Store.playlists()
        adapter.rows = lists.map { Row(it.name, "${it.items?.size ?: 0} رابط", R.drawable.ic_list) }
        v.findViewById<TextView>(R.id.empty).visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
    }
}
