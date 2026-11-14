package com.mtlive.iptv.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mtlive.iptv.R
import com.mtlive.iptv.data.AccountsStore
import com.mtlive.iptv.data.Cache
import com.mtlive.iptv.data.ContentItem
import com.mtlive.iptv.data.ContentType
import com.mtlive.iptv.data.DownloadRec
import com.mtlive.iptv.data.Downloads
import com.mtlive.iptv.data.HistoryStore
import com.mtlive.iptv.data.SavedAccount
import com.mtlive.iptv.data.Session
import com.mtlive.iptv.data.SessionStore
import com.mtlive.iptv.data.XtreamApi
import com.mtlive.iptv.databinding.ActivityGridBinding
import com.mtlive.iptv.databinding.ItemCardBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** شاشة شبكة بنفس ترتيب الأفلام: سجل المشاهدة / الحسابات المحفوظة / التنزيلات */
class GridActivity : AppCompatActivity() {

    enum class Mode { HISTORY, ACCOUNTS, DOWNLOADS }

    data class Card(
        val key: String,
        val title: String,
        val image: Any?,
        val badge: String? = null,
        val sub: String? = null,
        val progress: Int? = null,
        val placeholder: Int = R.drawable.poster_placeholder
    )

    private lateinit var b: ActivityGridBinding
    private lateinit var mode: Mode
    private val adapter = CardAdapter()

    private var history: List<ContentItem> = emptyList()
    private var accounts: List<SavedAccount> = emptyList()
    private var downloads: List<DownloadRec> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        b = ActivityGridBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()
        setupTopbar(b.topbar, Tabs.SETTINGS)

        mode = Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.HISTORY.name)
        b.btnBack.setOnClickListener { finish() }

        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density - 20
        val span = (widthDp / 150).toInt().coerceAtLeast(3)
        val itemW = (dm.widthPixels - 20 * dm.density) / span
        adapter.imageH = ((itemW - 12 * dm.density) * 1.45f).toInt()
        b.rv.layoutManager = GridLayoutManager(this, span)
        b.rv.adapter = adapter

        when (mode) {
            Mode.HISTORY -> {
                b.tvTitle.setText(R.string.history)
                b.btnAction.visibility = View.VISIBLE
                b.btnAction.setText(R.string.clear_history)
                b.btnAction.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_delete, 0, 0, 0)
                b.btnAction.setOnClickListener {
                    AlertDialog.Builder(this).setMessage(R.string.clear_confirm)
                        .setPositiveButton(R.string.yes) { _, _ -> HistoryStore.clear(this); reload() }
                        .setNegativeButton(R.string.no, null).show()
                }
            }
            Mode.ACCOUNTS -> {
                b.tvTitle.setText(R.string.accounts)
                b.btnAction.visibility = View.VISIBLE
                b.btnAction.setText(R.string.add_account)
                b.btnAction.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_accounts, 0, 0, 0)
                b.btnAction.setOnClickListener { addAccount() }
            }
            Mode.DOWNLOADS -> {
                b.tvTitle.setText(R.string.downloads)
            }
        }
        reload()
        if (mode == Mode.DOWNLOADS) pollDownloads()
    }

    override fun onResume() {
        super.onResume()
        if (::b.isInitialized) reload()
    }

    private fun reload() {
        val cards: List<Card> = when (mode) {
            Mode.HISTORY -> {
                history = HistoryStore.all(this)
                history.map { it.toCard() }
            }
            Mode.ACCOUNTS -> {
                val cur = Cache.session
                accounts = AccountsStore.all(this).filter { XtreamApi.isAllowed(it.host) }
                accounts.map { a ->
                    val isCur = cur != null && cur.host == a.host && cur.username == a.username
                    Card(
                        key = a.host + "|" + a.username, title = a.username, image = R.drawable.logo,
                        badge = if (isCur) "✓" else null,
                        sub = XtreamApi.serverLabel(a.host) + if (isCur) " • " + getString(R.string.current_account) else "",
                        placeholder = R.drawable.channel_placeholder
                    )
                }
            }
            Mode.DOWNLOADS -> {
                downloads = Downloads.all(this)
                downloads.map { it.toCard() }
            }
        }
        adapter.submit(cards)
        b.tvEmpty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
        b.tvEmpty.setText(when (mode) {
            Mode.HISTORY -> R.string.empty_history
            Mode.ACCOUNTS -> R.string.empty_accounts
            Mode.DOWNLOADS -> R.string.empty_downloads
        })
    }

    private fun ContentItem.toCard(): Card {
        val r = rating?.toDoubleOrNull()
        val tag = when (type) {
            ContentType.LIVE -> getString(R.string.live)
            ContentType.MOVIE -> getString(R.string.movies)
            else -> getString(R.string.series)
        }
        return Card(
            key = type.name + "_" + id, title = name, image = logo?.ifBlank { null },
            badge = if (r != null && r > 0) "★ " + String.format(Locale.ENGLISH, "%.1f", r) else null,
            sub = tag,
            placeholder = if (type == ContentType.LIVE) R.drawable.channel_placeholder else R.drawable.poster_placeholder
        )
    }

    private fun DownloadRec.toCard(): Card {
        val st = Downloads.state(this@GridActivity, this)
        val (label, prog) = when (st.status) {
            DownloadManager.STATUS_SUCCESSFUL -> getString(R.string.dl_done) to null
            DownloadManager.STATUS_FAILED -> getString(R.string.dl_failed) to null
            DownloadManager.STATUS_PAUSED -> getString(R.string.dl_paused) to st.percent
            DownloadManager.STATUS_RUNNING -> getString(R.string.dl_running, st.percent) to st.percent
            else -> getString(R.string.dl_pending) to 0
        }
        return Card(
            key = key, title = title, image = image?.ifBlank { null },
            badge = if (st.status == DownloadManager.STATUS_SUCCESSFUL) "✓" else if (prog != null) "${st.percent}%" else "!",
            sub = label, progress = prog
        )
    }

    /** تحديث نسبة التنزيل كل ثانية */
    private fun pollDownloads() {
        lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                if (mode == Mode.DOWNLOADS && downloads.isNotEmpty()) {
                    adapter.submit(downloads.map { it.toCard() })
                }
            }
        }
    }

    // --------------------- الضغط ---------------------

    private fun onCardClick(pos: Int) {
        when (mode) {
            Mode.HISTORY -> history.getOrNull(pos)?.let { play(it) }
            Mode.ACCOUNTS -> accounts.getOrNull(pos)?.let { switchAccount(it) }
            Mode.DOWNLOADS -> downloads.getOrNull(pos)?.let { rec ->
                val st = Downloads.state(this, rec)
                when (st.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val local = Uri.fromFile(File(rec.path)).toString()
                        play(ContentItem(id = rec.key, name = rec.title, logo = rec.image, type = rec.type, url = local))
                    }
                    DownloadManager.STATUS_FAILED -> {
                        Downloads.retry(this, rec); reload()
                    }
                    else -> Toast.makeText(this, getString(R.string.dl_running, st.percent), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onCardLongClick(pos: Int) {
        val title: String
        val action: () -> Unit
        when (mode) {
            Mode.HISTORY -> {
                val it = history.getOrNull(pos) ?: return
                title = it.name
                action = { HistoryStore.remove(this, it); reload() }
            }
            Mode.ACCOUNTS -> {
                val a = accounts.getOrNull(pos) ?: return
                title = a.username
                action = { AccountsStore.remove(this, a); reload() }
            }
            Mode.DOWNLOADS -> {
                val d = downloads.getOrNull(pos) ?: return
                title = d.title
                action = { Downloads.remove(this, d); reload() }
            }
        }
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_confirm, title))
            .setPositiveButton(R.string.delete) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun play(item: ContentItem) {
        PlayerQueue.set(listOf(item), 0)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun addAccount() {
        SessionStore.clear(this)
        Cache.session = null
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }

    private fun switchAccount(a: SavedAccount) {
        val cur = Cache.session
        if (cur != null && cur.host == a.host && cur.username == a.username) return
        lifecycleScope.launch {
            try {
                val info = XtreamApi(a.host, a.username, a.password).login()
                val s = Session(a.host, a.username, a.password, info.expDate, info.activeCons, info.maxConnections)
                SessionStore.save(this@GridActivity, s)
                SessionStore.rememberLast(this@GridActivity, a.host, a.username)
                Cache.clear()
                Cache.session = s
                startActivity(Intent(this@GridActivity, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@GridActivity, e.message ?: "خطأ بالاتصال", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --------------------- الأدابتر ---------------------

    inner class CardAdapter : RecyclerView.Adapter<CardAdapter.VH>() {
        var imageH = 300
        private var items: List<Card> = emptyList()

        fun submit(list: List<Card>) {
            val sameKeys = list.size == items.size && list.indices.all { list[it].key == items[it].key }
            val old = items
            items = list
            if (sameKeys) {
                list.indices.forEach { if (list[it] != old[it]) notifyItemChanged(it, "p") }
            } else notifyDataSetChanged()
        }

        inner class VH(val b: ItemCardBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val vh = VH(ItemCardBinding.inflate(LayoutInflater.from(p.context), p, false))
            vh.b.img.layoutParams.height = imageH
            return vh
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int, payloads: MutableList<Any>) {
            if (payloads.isNotEmpty()) bindText(h, items[pos]) else onBindViewHolder(h, pos)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val c = items[pos]
            if (c.image is Int) {
                h.b.img.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                h.b.img.setImageResource(c.image)
            } else {
                h.b.img.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                h.b.img.load(c.image as String?) {
                    placeholder(c.placeholder); error(c.placeholder); fallback(c.placeholder)
                }
            }
            bindText(h, c)
            h.b.root.setOnClickListener { onCardClick(h.bindingAdapterPosition) }
            h.b.root.setOnLongClickListener { onCardLongClick(h.bindingAdapterPosition); true }
        }

        private fun bindText(h: VH, c: Card) {
            h.b.tvName.text = c.title
            h.b.tvSub.visibility = if (c.sub.isNullOrBlank()) View.GONE else View.VISIBLE
            h.b.tvSub.text = c.sub
            h.b.tvBadge.visibility = if (c.badge == null) View.GONE else View.VISIBLE
            h.b.tvBadge.text = c.badge
            h.b.progress.visibility = if (c.progress == null) View.GONE else View.VISIBLE
            h.b.progress.progress = c.progress ?: 0
        }
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        fun intent(ctx: Context, mode: Mode) = Intent(ctx, GridActivity::class.java).putExtra(EXTRA_MODE, mode.name)
    }
}
