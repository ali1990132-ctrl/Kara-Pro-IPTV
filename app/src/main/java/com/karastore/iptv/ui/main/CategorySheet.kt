package com.karastore.iptv.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.karastore.iptv.R
import com.karastore.iptv.data.CategoryCount
import com.karastore.iptv.data.CategoryOrderStore
import com.karastore.iptv.data.ContentCache
import com.karastore.iptv.data.HiddenCategoriesStore
import com.karastore.iptv.data.LockedCategoriesStore
import com.karastore.iptv.data.Settings
import com.karastore.iptv.data.model.ContentType
import com.karastore.iptv.databinding.SheetCategoriesBinding

/** قائمة التصنيفات: عدّاد، بحث، إخفاء، قفل برمز، وترتيب بالسحب */
class CategorySheet : BottomSheetDialogFragment() {

    private var _b: SheetCategoriesBinding? = null
    private val b get() = _b!!

    private lateinit var type: ContentType
    var onPick: ((String?) -> Unit)? = null
    var onHiddenChanged: (() -> Unit)? = null

    private val items = mutableListOf<CategoryCount>()
    private var filtered = false
    private lateinit var adapter: CatAdapter
    private lateinit var touchHelper: ItemTouchHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = SheetCategoriesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        type = ContentType.valueOf(arguments?.getString("type") ?: ContentType.LIVE.name)
        rebuild()

        adapter = CatAdapter()
        b.rvCats.layoutManager = LinearLayoutManager(context)
        b.rvCats.adapter = adapter

        touchHelper = ItemTouchHelper(DragCallback())
        touchHelper.attachToRecyclerView(b.rvCats)

        b.etCatSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                filtered = q.isNotEmpty()
                if (q.isEmpty()) rebuild()
                else { items.clear(); items.addAll(ordered().filter { it.name.contains(q, true) }) }
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    /** التصنيفات مرتّبة حسب التفضيل (الكل أولاً) */
    private fun ordered(): List<CategoryCount> {
        val raw = ContentCache.categoriesWithCounts(type)
        val all = raw.firstOrNull { it.id == null }
        val rest = raw.filter { it.id != null }
        val order = CategoryOrderStore.get(requireContext(), type)
        val sorted = rest.sortedBy { c ->
            val i = order.indexOf(c.id); if (i < 0) Int.MAX_VALUE else i
        }
        return (listOfNotNull(all) + sorted)
    }

    private fun rebuild() {
        items.clear()
        items.addAll(ordered())
    }

    private fun persistOrder() {
        val ids = items.mapNotNull { it.id }
        CategoryOrderStore.set(requireContext(), type, ids)
    }

    private fun label(c: CategoryCount): String =
        if (c.id == null) getString(R.string.all) else c.name

    private fun promptPin(onSuccess: () -> Unit) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "••••"
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pin_enter)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (input.text.toString() == Settings.pin(requireContext())) onSuccess()
                else Toast.makeText(requireContext(), R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onPickRow(c: CategoryCount) {
        if (c.id != null && LockedCategoriesStore.isLocked(requireContext(), type, c.id)) {
            promptPin { onPick?.invoke(c.id); dismiss() }
        } else {
            onPick?.invoke(c.id); dismiss()
        }
    }

    private fun toggleLock(c: CategoryCount, pos: Int) {
        val id = c.id ?: return
        val locked = LockedCategoriesStore.isLocked(requireContext(), type, id)
        if (locked) {
            promptPin {
                LockedCategoriesStore.setLocked(requireContext(), type, id, false)
                adapter.notifyItemChanged(pos); onHiddenChanged?.invoke()
            }
        } else {
            LockedCategoriesStore.setLocked(requireContext(), type, id, true)
            adapter.notifyItemChanged(pos); onHiddenChanged?.invoke()
        }
    }

    inner class CatAdapter : RecyclerView.Adapter<CatAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.catName)
            val count: TextView = v.findViewById(R.id.catCount)
            val sw: androidx.appcompat.widget.SwitchCompat = v.findViewById(R.id.catSwitch)
            val lock: ImageView = v.findViewById(R.id.catLock)
            val drag: ImageView = v.findViewById(R.id.catDrag)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category_row, parent, false)
            return VH(v)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            holder.name.text = label(c)
            holder.count.text = c.count.toString()

            holder.sw.setOnCheckedChangeListener(null)
            if (c.id == null) {
                holder.sw.visibility = View.GONE
                holder.lock.visibility = View.GONE
                holder.drag.visibility = View.INVISIBLE
            } else {
                holder.sw.visibility = View.VISIBLE
                holder.sw.isChecked = !HiddenCategoriesStore.isHidden(requireContext(), type, c.id)
                holder.sw.setOnCheckedChangeListener { _, checked ->
                    HiddenCategoriesStore.setHidden(requireContext(), type, c.id, !checked)
                    onHiddenChanged?.invoke()
                }
                holder.lock.visibility = View.VISIBLE
                val locked = LockedCategoriesStore.isLocked(requireContext(), type, c.id)
                holder.lock.setImageResource(if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open)
                holder.lock.setOnClickListener { toggleLock(c, holder.bindingAdapterPosition) }
                holder.drag.visibility = if (filtered) View.INVISIBLE else View.VISIBLE
                holder.drag.setOnTouchListener { _, e ->
                    if (e.actionMasked == MotionEvent.ACTION_DOWN && !filtered) touchHelper.startDrag(holder)
                    false
                }
            }
            holder.itemView.setOnClickListener { onPickRow(c) }
        }

        override fun getItemCount() = items.size

        fun move(from: Int, to: Int) {
            if (from < 1 || to < 1 || from >= items.size || to >= items.size) return
            val m = items.removeAt(from)
            items.add(to, m)
            notifyItemMoved(from, to)
        }
    }

    inner class DragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
            if (filtered || vh.bindingAdapterPosition == 0) return 0
            return super.getMovementFlags(rv, vh)
        }
        override fun isLongPressDragEnabled() = false
        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            if (target.bindingAdapterPosition == 0) return false
            adapter.move(vh.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }
        override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            if (!filtered) persistOrder()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        fun newInstance(type: ContentType): CategorySheet {
            return CategorySheet().apply {
                arguments = Bundle().apply { putString("type", type.name) }
            }
        }
    }
}
