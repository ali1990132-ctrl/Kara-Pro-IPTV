package com.asvpn.player.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.asvpn.player.R
import com.asvpn.player.data.ContentItem
import com.asvpn.player.data.ContentType
import com.asvpn.player.data.Link

object Nav {
    fun open(a: Activity, item: ContentItem, siblings: List<ContentItem>) {
        when (item.type) {
            ContentType.SERIES -> a.startActivity(
                Intent(a, SeriesActivity::class.java)
                    .putExtra("id", item.id).putExtra("name", item.name).putExtra("cover", item.logo)
            )
            else -> {
                val playable = siblings.filter { it.url != null }
                val idx = playable.indexOfFirst { it.id == item.id && it.type == item.type }.coerceAtLeast(0)
                if (playable.isEmpty()) return
                PlayerActivity.start(a, playable.map { Link(it.name, it.url!!) }, idx)
            }
        }
    }

    fun expiredDialog(a: Activity, title: String? = null, msg: String? = null) {
        val d = Dialog(a)
        val v = LayoutInflater.from(a).inflate(R.layout.dialog_expired, null)
        title?.let { v.findViewById<TextView>(R.id.dlgTitle).text = it }
        msg?.let { v.findViewById<TextView>(R.id.dlgMsg).text = it }
        v.findViewById<android.view.View>(R.id.dlgClose).setOnClickListener { d.dismiss() }
        d.setContentView(v)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val w = (a.resources.displayMetrics.widthPixels * 0.88f).toInt()
            .coerceAtMost((460 * a.resources.displayMetrics.density).toInt())
        d.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }
}
