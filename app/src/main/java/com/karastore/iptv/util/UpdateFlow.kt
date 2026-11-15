package com.karastore.iptv.util

import android.app.Dialog
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.karastore.iptv.R
import kotlinx.coroutines.launch

/** تدفّق التحديث الإجباري: شاشة بشعار التطبيق ← تنزيل بنسبة مئوية ← تثبيت */
object UpdateFlow {

    private var current: Dialog? = null

    fun check(activity: AppCompatActivity, manual: Boolean) {
        activity.lifecycleScope.launch {
            if (manual) toast(activity, activity.getString(R.string.update_checking))
            val info = Updater.check(activity)
            if (activity.isFinishing) return@launch
            if (info == null) {
                if (manual) toast(activity, activity.getString(R.string.update_none))
                return@launch
            }
            show(activity, info)
        }
    }

    /** يعرض شاشة التحديث (لا تُغلق حتى يتم التحديث) */
    private fun show(activity: AppCompatActivity, info: Updater.Info) {
        if (activity.isFinishing) return
        current?.let { if (it.isShowing && it.ownerActivity == activity) return }

        val dlg = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dlg.setOwnerActivity(activity)
        dlg.setContentView(R.layout.dialog_update)
        dlg.setCancelable(false)
        dlg.setCanceledOnTouchOutside(false)
        dlg.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val msg = dlg.findViewById<TextView>(R.id.updMsg)
        val bar = dlg.findViewById<ProgressBar>(R.id.updProgress)
        val btn = dlg.findViewById<TextView>(R.id.updBtn)

        fun promptState() {
            msg.text = activity.getString(R.string.update_available, info.versionName) +
                "\n" + activity.getString(R.string.update_mandatory)
            bar.visibility = View.GONE
            btn.visibility = View.VISIBLE
            btn.requestFocus()
        }

        btn.setOnClickListener {
            if (!Updater.canInstall(activity)) {
                toast(activity, activity.getString(R.string.update_install_hint))
                Updater.openInstallSettings(activity)
                return@setOnClickListener
            }
            // حالة التنزيل: نسبة مئوية + شريط
            btn.visibility = View.GONE
            bar.visibility = View.VISIBLE
            bar.progress = 0
            msg.text = activity.getString(R.string.update_downloading_pct, 0)

            activity.lifecycleScope.launch {
                val file = Updater.download(activity, info.url) { pct ->
                    activity.runOnUiThread {
                        bar.progress = pct
                        msg.text = activity.getString(R.string.update_downloading_pct, pct)
                    }
                }
                if (activity.isFinishing) return@launch
                if (file != null) {
                    Updater.install(activity, file)
                } else {
                    toast(activity, activity.getString(R.string.update_failed))
                }
                // تبقى الشاشة حتى يكتمل التثبيت
                promptState()
            }
        }

        promptState()
        current = dlg
        dlg.show()
    }

    private fun toast(activity: AppCompatActivity, msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }
}
