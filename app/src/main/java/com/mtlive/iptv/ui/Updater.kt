package com.mtlive.iptv.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mtlive.iptv.BuildConfig
import com.mtlive.iptv.R
import com.mtlive.iptv.data.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import java.io.File

data class UpdateInfo(
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("notes") val notes: String? = null
)

/**
 * التحديث من داخل التطبيق:
 * كل ما ترفع تحديث على GitHub، يصير ملف version.json بالإصدار الجديد،
 * والتطبيق يقارنه بنسخته وينزّل الـ APK ويثبّته بدون متجر.
 */
object Updater {
    private const val VERSION_URL =
        "https://github.com/ali1990132-ctrl/Kara-Pro-IPTV/releases/download/mt-live-latest/version.json"

    var latest: UpdateInfo? = null; private set
    private var checkedThisRun = false

    val currentName: String get() = BuildConfig.VERSION_NAME

    suspend fun fetch(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(VERSION_URL + "?t=" + System.currentTimeMillis())
                .cacheControl(CacheControl.FORCE_NETWORK).build()
            XtreamApi.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val info = Gson().fromJson(r.body?.charStream(), UpdateInfo::class.java)
                latest = info
                info
            }
        } catch (e: Exception) { null }
    }

    fun hasUpdate(info: UpdateInfo?) = info != null && info.versionCode > BuildConfig.VERSION_CODE && !info.url.isNullOrBlank()

    /** فحص صامت مرة وحدة بكل تشغيل؛ إذا اكو تحديث يطلع رسالة */
    fun autoCheck(a: Activity, scope: LifecycleCoroutineScope, onResult: (UpdateInfo?) -> Unit = {}) {
        if (checkedThisRun) { onResult(latest); return }
        checkedThisRun = true
        scope.launch {
            val info = fetch()
            onResult(info)
            if (hasUpdate(info) && !a.isFinishing) askUpdate(a, scope, info!!)
        }
    }

    /** فحص يدوي (زر تحديث التطبيق) */
    fun manualCheck(a: Activity, scope: LifecycleCoroutineScope, onResult: (UpdateInfo?) -> Unit = {}) {
        Toast.makeText(a, R.string.update_checking, Toast.LENGTH_SHORT).show()
        scope.launch {
            val info = fetch()
            onResult(info)
            when {
                info == null -> Toast.makeText(a, a.getString(R.string.update_failed, "network"), Toast.LENGTH_LONG).show()
                hasUpdate(info) -> askUpdate(a, scope, info)
                else -> Toast.makeText(a, a.getString(R.string.update_latest, currentName), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun askUpdate(a: Activity, scope: LifecycleCoroutineScope, info: UpdateInfo) {
        AlertDialog.Builder(a)
            .setTitle(R.string.update_title)
            .setMessage(a.getString(R.string.update_msg, info.versionName ?: info.versionCode.toString(), info.notes ?: ""))
            .setPositiveButton(R.string.update_now) { _, _ -> download(a, scope, info) }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun download(a: Activity, scope: LifecycleCoroutineScope, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, R.string.update_permission, Toast.LENGTH_LONG).show()
            try {
                a.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
            } catch (e: Exception) { }
            return
        }
        val dialog = AlertDialog.Builder(a)
            .setTitle(R.string.update_title)
            .setMessage(a.getString(R.string.update_downloading, 0))
            .setCancelable(false)
            .show()
        scope.launch {
            try {
                val dir = File(a.getExternalFilesDir(null) ?: a.filesDir, "updates").apply { mkdirs() }
                val file = File(dir, "update.apk")
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(info.url!!).build()
                    XtreamApi.client.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
                        val body = r.body ?: throw RuntimeException("empty")
                        val total = body.contentLength()
                        var last = -1
                        body.byteStream().use { input ->
                            file.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        if (pct != last) {
                                            last = pct
                                            withContext(Dispatchers.Main) { dialog.setMessage(a.getString(R.string.update_downloading, pct)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                dialog.dismiss()
                install(a, file)
            } catch (e: Exception) {
                dialog.dismiss()
                Toast.makeText(a, a.getString(R.string.update_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun install(a: Activity, file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            FileProvider.getUriForFile(a, a.packageName + ".updates", file)
        else Uri.fromFile(file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }
}
