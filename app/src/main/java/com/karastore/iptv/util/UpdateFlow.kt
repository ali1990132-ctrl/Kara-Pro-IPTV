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
            showMandatory(activity, info)
        }
    }

    /** حوار تحديث إجباري: لا يُغلق ولا يوجد زر "لاحقاً" */
    private fun showMandatory(activity: AppCompatActivity, info: Updater.Info) {
        if (activity.isFinishing) return
        val dlg = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_available, info.versionName))
            .setMessage(activity.getString(R.string.update_mandatory))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.update_now), null)
            .create()
        dlg.setCanceledOnTouchOutside(false)
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dlg.dismiss()
                startDownload(activity, info)
            }
        }
        dlg.show()
    }

    private fun startDownload(activity: AppCompatActivity, info: Updater.Info) {
        val url = info.url
        if (!Updater.canInstall(activity)) {
            toast(activity, activity.getString(R.string.update_install_hint))
            Updater.openInstallSettings(activity)
            showMandatory(activity, info)
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
            if (file != null) {
                Updater.install(activity, file)
            } else {
                toast(activity, activity.getString(R.string.update_failed))
            }
            // يبقى الحوار حتى يكتمل التحديث
            showMandatory(activity, info)
        }
    }

    private fun toast(activity: AppCompatActivity, msg: String) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }
}
