package com.karastore.iptv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** تحديث التطبيق من داخله عبر ملف إصدار على GitHub (بدون متجر) */
object Updater {

    // ملف يصفه الـCI عند كل بناء، يحمل رقم الإصدار ورابط الـAPK
    private const val VERSION_URL =
        "https://github.com/ali1990132-ctrl/Kara-Pro-IPTV/releases/download/latest/version.json"
    private const val APK_URL =
        "https://github.com/ali1990132-ctrl/Kara-Pro-IPTV/releases/download/latest/A-S.apk"

    data class Info(val versionCode: Int, val versionName: String, val url: String)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun currentCode(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    } catch (e: Exception) { 0 }

    /** يرجّع معلومات التحديث إن وُجد إصدار أحدث، وإلا null */
    suspend fun check(ctx: Context): Info? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(VERSION_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val j = JSONObject(body)
                val code = j.optInt("versionCode", 0)
                val name = j.optString("versionName", "")
                val url = j.optString("url", APK_URL).ifBlank { APK_URL }
                if (code > currentCode(ctx)) Info(code, name, url) else null
            }
        } catch (e: Exception) { null }
    }

    /** ينزّل ملف الـAPK ويرجّع مساره */
    suspend fun download(ctx: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "as-update.apk")
                if (out.exists()) out.delete()
                resp.body?.byteStream()?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                out
            }
        } catch (e: Exception) { null }
    }

    /** هل يُسمح بتثبيت التطبيقات من مصدر غير معروف؟ */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** يفتح إعدادات السماح بالتثبيت */
    fun openInstallSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {}
        }
    }

    /** يشغّل مُثبّت النظام على ملف الـAPK */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}
