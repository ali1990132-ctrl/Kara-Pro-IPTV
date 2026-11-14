package com.mtlive.iptv.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
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
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("notes") val notes: String? = null
)

/**
 * التحديث الإجباري من داخل التطبيق:
 * كل تحديث ينرفع على GitHub يطلع بملف version.json، والتطبيق يقارنه بنسخته،
 * وإذا أكو أحدث يفتح شاشة التحديث وما يخلّي المشترك يكمل إلا بعد التحديث.
 */
object Updater {
    private const val VERSION_URL =
        "https://github.com/ali1990132-ctrl/Kara-Pro-IPTV/releases/download/mt-live-latest/version.json"

    var latest: UpdateInfo? = null; private set

    val currentName: String get() = BuildConfig.VERSION_NAME

    private val quick by lazy {
        XtreamApi.client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetch(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(VERSION_URL + "?t=" + System.currentTimeMillis())
                .cacheControl(CacheControl.FORCE_NETWORK).build()
            quick.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val info = Gson().fromJson(r.body?.charStream(), UpdateInfo::class.java)
                latest = info
                info
            }
        } catch (e: Exception) { null }
    }

    fun hasUpdate(info: UpdateInfo?) = info != null && info.versionCode > BuildConfig.VERSION_CODE && !info.url.isNullOrBlank()

    fun openGate(a: Activity) {
        a.startActivity(Intent(a, UpdateActivity::class.java))
    }

    /** فحص عند فتح الشاشات: إذا أكو تحديث يفتح شاشة التحديث الإجبارية */
    fun enforce(a: Activity, scope: LifecycleCoroutineScope, onResult: (UpdateInfo?) -> Unit = {}) {
        if (hasUpdate(latest)) { onResult(latest); openGate(a); return }
        scope.launch {
            val info = fetch()
            onResult(info)
            if (hasUpdate(info) && !a.isFinishing) openGate(a)
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
                hasUpdate(info) -> openGate(a)
                else -> Toast.makeText(a, a.getString(R.string.update_latest, currentName), Toast.LENGTH_LONG).show()
            }
        }
    }
}
