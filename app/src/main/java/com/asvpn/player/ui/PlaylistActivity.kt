package com.asvpn.player.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asvpn.player.R
import com.asvpn.player.data.Link
import com.asvpn.player.data.Playlist
import com.asvpn.player.data.Store

class PlaylistActivity : AppCompatActivity() {

    private var all: MutableList<Playlist> = mutableListOf()
    private var pl: Playlist? = null
    private var shown: List<Link> = emptyList()
    private lateinit var adapter: RowAdapter
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)
        val id = intent.getLongExtra("id", 0)
        all = Store.playlists()
        pl = all.firstOrNull { it.id == id }
        val p = pl ?: run { finish(); return }
        if (p.items == null) p.items = mutableListOf()

        findViewById<TextView>(R.id.title).text = p.name
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAdd).setOnClickListener {
            Ui.input(this, "إضافة رابط", "https://…", Ui.clipboard(this).takeIf { it.startsWith("http") } ?: "", ltr = true) { url ->
                if (url.isBlank()) return@input
                Ui.input(this, "اسم الرابط", "مثال: فيلم", Ui.titleFromUrl(url)) { name ->
                    p.items!!.add(Link(name.ifBlank { Ui.titleFromUrl(url) }, url))
                    Store.savePlaylists(all); load()
                }
            }
        }
        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; load() }
        })

        adapter = RowAdapter(
            onClick = { pos ->
                val items = shown
                if (pos in items.indices) PlayerActivity.start(this, items, pos)
            },
            onDelete = { pos ->
                shown.getOrNull(pos)?.let { link ->
                    Ui.confirm(this, "حذف \"${link.title}\"؟") {
                        p.items!!.remove(link); Store.savePlaylists(all); load()
                    }
                }
            }
        )
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = this@PlaylistActivity.adapter
        }
        load()
    }

    private fun load() {
        val items = pl?.items ?: mutableListOf()
        shown = if (query.isBlank()) items.toList()
        else items.filter { it.title.contains(query, true) || it.group.contains(query, true) }
        adapter.rows = shown.map { Row(it.title, it.group.ifBlank { it.url }) }
        findViewById<View>(R.id.empty).visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }
}
