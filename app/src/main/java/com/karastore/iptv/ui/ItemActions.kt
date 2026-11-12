package com.karastore.iptv.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.karastore.iptv.R
import com.karastore.iptv.data.Downloader
import com.karastore.iptv.data.FavoritesStore
import com.karastore.iptv.data.model.ContentItem
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.ui.player.PlayerActivity

/** قائمة الإجراءات عند الضغط المطوّل على عنصر: تشغيل / تحميل / مفضلة */
object ItemActions {

    fun play(activity: Activity, item: ContentItem) {
        val url = item.directUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(activity, R.string.player_error, Toast.LENGTH_SHORT).show(); return
        }
        activity.startActivity(Intent(activity, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, url)
            putExtra(PlayerActivity.EXTRA_TITLE, item.name)
            putExtra(PlayerActivity.EXTRA_ITEM, com.google.gson.Gson().toJson(item))
        })
    }

    fun show(activity: Activity, item: ContentItem) {
        val isVod = item.type == ContentType.MOVIE || item.type == ContentType.EPISODE
        val fav = FavoritesStore.isFav(activity, item.id)

        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        labels.add(activity.getString(R.string.action_play))
        actions.add { play(activity, item) }

        if (isVod) {
            labels.add(activity.getString(R.string.action_download))
            actions.add { download(activity, item) }
        }

        labels.add(activity.getString(if (fav) R.string.action_fav_remove else R.string.action_fav_add))
        actions.add {
            val nowFav = FavoritesStore.toggle(activity, item)
            Toast.makeText(
                activity,
                activity.getString(if (nowFav) R.string.action_fav_add else R.string.action_fav_remove),
                Toast.LENGTH_SHORT
            ).show()
        }

        AlertDialog.Builder(activity)
            .setTitle(item.name)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    fun download(activity: Activity, item: ContentItem) {
        Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show()
        Downloader.start(activity, item) { _, msg ->
            activity.runOnUiThread {
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
