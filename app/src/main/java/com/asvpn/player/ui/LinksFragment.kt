package com.asvpn.player.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asvpn.player.R
import com.asvpn.player.data.Link
import com.asvpn.player.data.Store

class LinksFragment : Fragment() {

    private lateinit var adapter: RowAdapter
    private var history: List<Link> = emptyList()

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            PlayerActivity.start(requireContext(), Ui.fileName(requireContext(), uri), uri.toString())
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_links, c, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val input = v.findViewById<EditText>(R.id.urlInput)
        val play = {
            val url = input.text.toString().trim()
            if (url.isBlank()) Toast.makeText(requireContext(), "اكتب أو الصق رابطاً أولاً", Toast.LENGTH_SHORT).show()
            else if (url.substringBefore('?').endsWith(".m3u", true) || url.contains("type=m3u", true)) {
                Importer.importM3uUrl(this, url) { }
            } else PlayerActivity.start(requireContext(), Ui.titleFromUrl(url), url)
        }
        v.findViewById<View>(R.id.btnPlay).setOnClickListener { play() }
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { play(); true } else false
        }
        v.findViewById<View>(R.id.btnPaste).setOnClickListener {
            val t = Ui.clipboard(requireContext())
            if (t.isBlank()) Toast.makeText(requireContext(), "الحافظة فارغة", Toast.LENGTH_SHORT).show()
            else input.setText(t)
        }
        v.findViewById<View>(R.id.cardFile).setOnClickListener { pickVideo.launch(arrayOf("video/*")) }
        v.findViewById<View>(R.id.cardM3u).setOnClickListener { Importer.askM3u(this) }
        v.findViewById<View>(R.id.btnClearHistory).setOnClickListener {
            Ui.confirm(requireContext(), "مسح سجل المشاهدات؟") { Store.clearHistory(); load() }
        }

        adapter = RowAdapter(
            onClick = { pos -> history.getOrNull(pos)?.let { PlayerActivity.start(requireContext(), it.title, it.url) } },
        )
        v.findViewById<RecyclerView>(R.id.historyList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LinksFragment.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val v = view ?: return
        history = Store.history()
        adapter.rows = history.map { Row(it.title, it.url, R.drawable.ic_history) }
        v.findViewById<TextView>(R.id.emptyHistory).visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.btnClearHistory).visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
    }
}
