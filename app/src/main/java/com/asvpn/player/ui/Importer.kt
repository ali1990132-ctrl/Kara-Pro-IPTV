package com.asvpn.player.ui

import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.asvpn.player.data.M3u
import com.asvpn.player.data.Net
import com.asvpn.player.data.Playlist
import com.asvpn.player.data.Store
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Importer {

    fun askM3u(f: Fragment) {
        Ui.input(f.requireContext(), "استيراد قائمة M3U", "https://…/playlist.m3u", Ui.clipboard(f.requireContext()).takeIf { it.startsWith("http") } ?: "", ltr = true) { url ->
            if (url.startsWith("http")) importM3uUrl(f, url) {}
        }
    }

    fun importM3uUrl(f: Fragment, url: String, done: (Playlist) -> Unit) {
        val ctx = f.requireContext().applicationContext
        Toast.makeText(ctx, "جاري تحميل القائمة…", Toast.LENGTH_SHORT).show()
        f.lifecycleScope.launch {
            try {
                val links = withContext(Dispatchers.IO) { M3u.parse(Net.get(url)) }
                if (links.isEmpty()) throw Exception("القائمة فارغة أو الصيغة غير مدعومة")
                val name = Ui.titleFromUrl(url).substringBeforeLast('.').ifBlank { "قائمة" }
                val pl = Playlist(System.currentTimeMillis(), name, links.toMutableList())
                val all = Store.playlists()
                all.add(0, pl)
                Store.savePlaylists(all)
                Toast.makeText(ctx, "تمت إضافة ${links.size} رابط", Toast.LENGTH_SHORT).show()
                done(pl)
                f.activity?.let { a -> a.startActivity(Intent(a, PlaylistActivity::class.java).putExtra("id", pl.id)) }
            } catch (e: Exception) {
                f.context?.let {
                    MaterialAlertDialogBuilder(it).setTitle("تعذّر الاستيراد").setMessage(e.message ?: "خطأ غير معروف")
                        .setPositiveButton("حسناً", null).show()
                }
            }
        }
    }

    fun importM3uText(f: Fragment, name: String, text: String) {
        val links = M3u.parse(text)
        if (links.isEmpty()) {
            Toast.makeText(f.requireContext(), "الملف لا يحتوي روابط", Toast.LENGTH_SHORT).show(); return
        }
        val pl = Playlist(System.currentTimeMillis(), name, links.toMutableList())
        val all = Store.playlists(); all.add(0, pl); Store.savePlaylists(all)
        f.startActivity(Intent(f.requireContext(), PlaylistActivity::class.java).putExtra("id", pl.id))
    }
}
