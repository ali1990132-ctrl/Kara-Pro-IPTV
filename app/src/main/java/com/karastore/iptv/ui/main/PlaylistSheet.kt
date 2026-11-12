package com.karastore.iptv.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.karastore.iptv.R
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.PlaylistStore
import com.karastore.iptv.data.Repo
import com.karastore.iptv.data.Session
import com.karastore.iptv.data.SessionStore
import com.karastore.iptv.databinding.SheetPlaylistsBinding
import com.karastore.iptv.ui.login.LoginActivity
import kotlinx.coroutines.launch

/** قائمة قوائم التشغيل: تبديل / إضافة / حذف */
class PlaylistSheet : BottomSheetDialogFragment() {

    private var _b: SheetPlaylistsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: PlAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = SheetPlaylistsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PlAdapter()
        b.rvPlaylists.layoutManager = LinearLayoutManager(context)
        b.rvPlaylists.adapter = adapter
        refresh()

        b.btnAdd.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_ADD, true)
            })
            dismiss()
        }
    }

    private fun refresh() {
        adapter.submit(PlaylistStore.all(requireContext()))
    }

    private fun switchTo(session: Session) {
        if (PlaylistStore.isActive(Repo.session, session)) { dismiss(); return }
        val progress = AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.switching))
            .setCancelable(false).create()
        progress.show()
        Repo.init(session)
        lifecycleScope.launch {
            try {
                Repo.connect()
                SessionStore.save(requireContext(), session, true)
                ContentCache.clearAll()
                progress.dismiss()
                startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(requireContext(), e.message ?: getString(R.string.err_login), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete(session: Session) {
        AlertDialog.Builder(requireContext())
            .setMessage("${getString(R.string.action_delete)}: ${session.name.ifBlank { session.username.ifBlank { session.m3uUrl } }}")
            .setPositiveButton(R.string.action_delete) { _, _ ->
                PlaylistStore.remove(requireContext(), session)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    inner class PlAdapter : RecyclerView.Adapter<PlAdapter.VH>() {
        private val items = mutableListOf<Session>()
        fun submit(list: List<Session>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.plName)
            val active: TextView = v.findViewById(R.id.plActive)
            val del: ImageView = v.findViewById(R.id.plDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.name.text = s.name.ifBlank { s.username.ifBlank { s.m3uUrl } }
            holder.active.visibility = if (PlaylistStore.isActive(Repo.session, s)) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { switchTo(s) }
            holder.del.setOnClickListener { confirmDelete(s) }
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
