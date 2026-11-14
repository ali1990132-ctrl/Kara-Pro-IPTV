package com.mtlive.iptv.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.mtlive.iptv.R
import com.mtlive.iptv.data.XtreamApi
import com.mtlive.iptv.databinding.ActivityUpdateBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.Locale

/** شاشة التحديث الإجباري — ما تنسد إلا بالتحديث */
class UpdateActivity : AppCompatActivity() {

    private lateinit var b: ActivityUpdateBinding
    private var info: UpdateInfo? = null
    private var downloading = false
    private var file: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(b.root)
        fullscreen()

        // زر الرجوع يطلع من التطبيق كله (التحديث إجباري)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finishAffinity() }
        })

        info = Updater.latest
        showInfo()
        b.btnUpdate.setOnClickListener { onUpdateClick() }
        b.btnUpdate.requestFocus()

        if (info == null) lifecycleScope.launch { info = Updater.fetch(); showInfo() }
    }

    private fun showInfo() {
        val i = info
        b.tvMsg.text = getString(R.string.update_required, i?.versionName ?: "")
        if (i != null && !i.notes.isNullOrBlank()) b.tvMsg.append("\n\n" + i.notes)
    }

    private fun onUpdateClick() {
        if (downloading) return
        val f = file
        if (f != null && f.exists()) { install(f); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            b.tvMsg.text = getString(R.string.update_permission)
            try {
                startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (e: Exception) { }
            return
        }
        val url = info?.url ?: return
        download(url)
    }

    private fun download(url: String) {
        downloading = true
        b.btnUpdate.visibility = View.GONE
        b.progressBar.visibility = View.VISIBLE
        b.progressBar.isIndeterminate = true
        b.tvMsg.text = getString(R.string.update_downloading, 0)
        lifecycleScope.launch {
            try {
                val dir = File(getExternalFilesDir(null) ?: filesDir, "updates").apply { mkdirs() }
                val out = File(dir, "update.apk")
                withContext(Dispatchers.IO) {
                    XtreamApi.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
                        val body = r.body ?: throw RuntimeException("empty")
                        val total = body.contentLength()
                        var lastPct = -1
                        var lastMb = -1L
                        body.byteStream().use { input ->
                            out.outputStream().use { o ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    o.write(buf, 0, n)
                                    done += n
                                    if (total > 0) {
                                        val pct = (done * 100 / total).toInt()
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            withContext(Dispatchers.Main) {
                                                b.progressBar.isIndeterminate = false
                                                b.progressBar.progress = pct
                                                b.tvMsg.text = getString(R.string.update_downloading, pct)
                                            }
                                        }
                                    } else {
                                        val mb = done / (1024 * 1024)
                                        if (mb != lastMb) {
                                            lastMb = mb
                                            withContext(Dispatchers.Main) {
                                                b.tvMsg.text = getString(R.string.update_downloading_mb, String.format(Locale.ENGLISH, "%d MB", mb))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                file = out
                downloading = false
                b.progressBar.isIndeterminate = false
                b.progressBar.progress = 100
                b.tvMsg.text = getString(R.string.update_installing)
                b.btnUpdate.text = getString(R.string.install_again)
                b.btnUpdate.visibility = View.VISIBLE
                install(out)
            } catch (e: Exception) {
                downloading = false
                b.progressBar.visibility = View.GONE
                b.btnUpdate.visibility = View.VISIBLE
                b.btnUpdate.text = getString(R.string.retry)
                b.tvMsg.text = getString(R.string.update_failed, e.message ?: "")
            }
        }
    }

    private fun install(f: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            FileProvider.getUriForFile(this, "$packageName.updates", f)
        else Uri.fromFile(f)
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            b.tvMsg.text = getString(R.string.update_failed, e.message ?: "")
        }
    }
}
