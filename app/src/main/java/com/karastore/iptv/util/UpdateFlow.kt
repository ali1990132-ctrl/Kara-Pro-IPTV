package com.karastore.iptv.util

import android.app.ProgressDialog
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.karastore.iptv.R
import kotlinx.coroutines.launch

/** تدفّق التحديث داخل التطبيق: فحص ثم حوار ثم تنزيل وتثبيت */
object UpdateFlow {

    fun check(activity: AppCompatActivity, manual: Boolean) {
        activity.lifecycleScope.launch {
            if (manual) toast(activity, activity.getString(R.string.update_checking))
            val info = Updater.check(activity)
            if (activity.isFinishing) return@launch
            if (info == null) {
                if (manual) toast(activity, activity.getString(R.string.update_none))
                return@launch
            }
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available, info.versionName))
                .setMessage(activity.getString(R.string.update_install_hint))
                .setCancelable(true)
                .setPositiveButton(activity.getString(R.string.update_now)) { _, _ ->
                    startDownload(activity, info.url)
                }
                .setNegativeButton(activity.getString(R.string.update_later), null)
                .show()
        }
    }

    private fun startDownload(activity: AppCompatActivity, url: String) {
        if (!Updater.canInstall(activity)) {
            toast(activity, activity.getString(R.string.update_install_hint))
            Updater.openInstallSettings(activity)
            return
        }
        @Suppress("DEPRECATION")
        val dlg = ProgressDialog(activity).apply {
            setMessage(activity.getString(R.string.update_downloading))
            setCancelable(false)
            isIndeterminate = true
            show()
        }
        activity.lifecycleScope.launch {
            val file = Updater.download(activity, url)
            if (dlg.isShowing) dlg.dismiss()
            if (activity.isFinishing) return@launch
            if (file != null) Updater.install(activity, file)
            else toast(activity, activity.getString(R.string.update_failed))
        }
    }

    private fun toast(activity: AppCompatActivity, msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }
}
