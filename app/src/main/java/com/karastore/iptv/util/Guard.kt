package com.karastore.iptv.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * حماية ضد التعديل: يتحقق أن التطبيق موقّع بتوقيع المطوّر الأصلي.
 * أي نسخة مُعدّلة وأُعيد توقيعها بمفتاح آخر يختلف بصمتها فيُرفض تشغيلها.
 */
object Guard {

    // بصمة SHA-256 لشهادة توقيع المطوّر الأصلية
    private const val EXPECTED =
        "6FB9009DE1985FE959545ED72A12564E6C79355557C36B5B6E830011880FF0CD"

    fun isOriginal(ctx: Context): Boolean {
        return try {
            val sigs = signatures(ctx)
            if (sigs.isEmpty()) return true // تعذّر القراءة: لا نُعطّل التطبيق
            val md = MessageDigest.getInstance("SHA-256")
            sigs.any { bytes ->
                val hex = md.digest(bytes).joinToString("") { "%02X".format(it) }
                hex.equals(EXPECTED, ignoreCase = true)
            }
        } catch (e: Throwable) {
            true // عند أي خطأ غير متوقع لا نمنع المستخدم الأصلي
        }
    }

    @Suppress("DEPRECATION")
    private fun signatures(ctx: Context): List<ByteArray> {
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        return if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            val arr = if (info != null && info.hasMultipleSigners())
                info.apkContentsSigners else info?.signingCertificateHistory
            arr?.map { it.toByteArray() } ?: emptyList()
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures?.map { it.toByteArray() }
                ?: emptyList()
        }
    }
}
