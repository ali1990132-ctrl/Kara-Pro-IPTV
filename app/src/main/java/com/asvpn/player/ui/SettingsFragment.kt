package com.asvpn.player.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.asvpn.player.R
import com.asvpn.player.data.SessionStore
import com.asvpn.player.data.Store
import android.content.Intent
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment() {

    private val colors = intArrayOf(
        Color.WHITE, Color.parseColor("#FFEB3B"), Color.parseColor("#00D1FF"),
        Color.parseColor("#22C55E"), Color.parseColor("#FF9800"), Color.parseColor("#FF4081")
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_settings, c, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val sizeLabel = v.findViewById<TextView>(R.id.sizeLabel)
        val bar = v.findViewById<SeekBar>(R.id.sizeBar)
        val preview = v.findViewById<TextView>(R.id.preview)
        val bg = v.findViewById<MaterialSwitch>(R.id.bgSwitch)
        val row = v.findViewById<LinearLayout>(R.id.colorRow)
        val d = resources.displayMetrics.density

        fun refresh() {
            sizeLabel.text = "حجم الخط: ${Store.subSize}"
            preview.textSize = Store.subSize.toFloat()
            preview.setTextColor(Store.subColor)
            preview.setShadowLayer(4f, 0f, 0f, Color.BLACK)
            for (i in 0 until row.childCount) {
                val dot = row.getChildAt(i)
                (dot.background as? GradientDrawable)?.setStroke(
                    (3 * d).toInt(), if (colors[i] == Store.subColor) Color.parseColor("#7C5CFF") else Color.TRANSPARENT
                )
            }
        }

        bar.progress = Store.subSize - 12
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { Store.subSize = p + 12; refresh() }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        colors.forEach { c ->
            val size = (36 * d).toInt()
            val dot = View(requireContext()).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = (10 * d).toInt() }
                setOnClickListener { Store.subColor = c; refresh() }
            }
            row.addView(dot)
        }

        bg.isChecked = Store.subBg
        bg.setOnCheckedChangeListener { _, b -> Store.subBg = b }

        v.findViewById<MaterialSwitch>(R.id.resumeSwitch).apply {
            isChecked = Store.resume
            setOnCheckedChangeListener { _, b -> Store.resume = b }
        }

        val ver = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (_: Exception) { "" }
        v.findViewById<TextView>(R.id.version).text = "الإصدار $ver"
        refresh()

        v.findViewById<View>(R.id.rowVpn).setOnClickListener { HostActivity.open(requireContext(), HostActivity.VPN) }
        v.findViewById<View>(R.id.rowLinks).setOnClickListener { HostActivity.open(requireContext(), HostActivity.LINKS) }
        v.findViewById<View>(R.id.rowLists).setOnClickListener { HostActivity.open(requireContext(), HostActivity.LISTS) }
        val logout = v.findViewById<View>(R.id.rowLogout)
        logout.visibility = if (SessionStore.load(requireContext()) != null) View.VISIBLE else View.GONE
        logout.setOnClickListener {
            Ui.confirm(requireContext(), "تسجيل الخروج من الحساب؟") {
                SessionStore.clear(requireContext())
                startActivity(Intent(requireContext(), LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        }
    }
}
