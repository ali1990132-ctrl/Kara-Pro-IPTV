package com.asvpn.player.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.asvpn.player.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class Row(val title: String, val subtitle: String, val icon: Int = R.drawable.ic_play)

class RowAdapter(
    private val onClick: (Int) -> Unit,
    private val onDelete: ((Int) -> Unit)? = null,
    private val onLong: ((Int) -> Unit)? = null,
) : RecyclerView.Adapter<RowAdapter.VH>() {

    var rows: List<Row> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.subtitle)
        val del: ImageView = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        h.title.text = r.title
        h.sub.text = r.subtitle
        h.sub.visibility = if (r.subtitle.isBlank()) View.GONE else View.VISIBLE
        h.icon.setImageResource(r.icon)
        h.itemView.setOnClickListener { onClick(h.bindingAdapterPosition) }
        h.itemView.setOnLongClickListener {
            onLong?.invoke(h.bindingAdapterPosition); onLong != null
        }
        if (onDelete != null) {
            h.del.visibility = View.VISIBLE
            h.del.setOnClickListener { onDelete.invoke(h.bindingAdapterPosition) }
        } else h.del.visibility = View.GONE
    }
}

object Ui {
    fun input(
        ctx: Context, title: String, hint: String, initial: String = "",
        multiLine: Boolean = false, ltr: Boolean = false, onOk: (String) -> Unit
    ) {
        val et = EditText(ctx).apply {
            this.hint = hint
            setText(initial)
            setSingleLine(!multiLine)
            if (multiLine) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 6
                textSize = 12f
            }
            if (ltr) textDirection = View.TEXT_DIRECTION_LTR
        }
        val box = FrameLayout(ctx).apply {
            val p = (20 * ctx.resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
            addView(et)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("موافق") { _, _ -> onOk(et.text.toString().trim()) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    fun confirm(ctx: Context, msg: String, onOk: () -> Unit) {
        MaterialAlertDialogBuilder(ctx)
            .setMessage(msg)
            .setPositiveButton("نعم") { _, _ -> onOk() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    fun clipboard(ctx: Context): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim() ?: ""
    }

    fun fileName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: ""
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "فيديو"
    }

    fun titleFromUrl(url: String): String {
        val last = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        return Uri.decode(last).ifBlank { url }
    }
}
