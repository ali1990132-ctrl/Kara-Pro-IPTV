package com.mtlive.iptv.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * حماية ضد التعديل:
 * التطبيق يتأكد من بصمة شهادة التوقيع الخاصة به. لو أي أحد فك التطبيق وعدّله
 * لازم يعيد توقيعه بمفتاح ثاني (ما عنده مفتاحي)، فتتغيّر البصمة ويرفض التطبيق الاشتغال.
 */
internal object SecurityGuard {

    // بصمة SHA-256 لشهادة التوقيع، مخزونة مشفّرة (XOR) حتى ما تبين بفحص النصوص
    private val K = 0x5A
    private val ENC = byteArrayOf(
        -51, 64, -51, 61, -19, -18, -17, 53, 126, -12, 21, -7, -35, -49, 107, -25,
        15, 73, -13, -59, 120, 7, -32, 98, -14, -124, -3, 47, -54, -19, -26, -73
    )

    private fun expected(): ByteArray = ByteArray(ENC.size) { (ENC[it].toInt() xor K).toByte() }

    /** يرجّع true لو التوقيع سليم */
    fun verify(ctx: Context): Boolean {
        return try {
            val certs = signingCerts(ctx)
            val want = expected()
            certs.any { MessageDigest.getInstance("SHA-256").digest(it).contentEquals(want) }
        } catch (e: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION", "PackageManagerGetSignatures")
    private fun signingCerts(ctx: Context): List<ByteArray> {
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val si = info.signingInfo ?: return emptyList()
            val arr = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            arr?.map { it.toByteArray() } ?: emptyList()
        } else {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
    }
}
