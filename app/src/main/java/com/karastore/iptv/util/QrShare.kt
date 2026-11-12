package com.karastore.iptv.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.karastore.iptv.data.LoginMode
import com.karastore.iptv.data.Session

/** توليد/قراءة رابط نقل الاشتراك عبر QR */
object QrShare {

    const val SCHEME = "asiptv"
    const val HOST = "login"

    /** يبني رابطاً يحمل بيانات الاشتراك */
    fun buildLink(session: Session): String {
        val b = Uri.Builder().scheme(SCHEME).authority(HOST)
        if (session.mode == LoginMode.XTREAM) {
            b.appendQueryParameter("m", "x")
            b.appendQueryParameter("s", session.server)
            b.appendQueryParameter("u", session.username)
            b.appendQueryParameter("p", session.password)
        } else {
            b.appendQueryParameter("m", "u")
            b.appendQueryParameter("url", session.m3uUrl)
        }
        if (session.name.isNotBlank()) b.appendQueryParameter("n", session.name)
        return b.build().toString()
    }

    /** يحوّل رابطاً ممسوحاً إلى جلسة، أو null إن لم يكن رابطنا */
    fun parse(uri: Uri?): Session? {
        if (uri == null || uri.scheme != SCHEME) return null
        val name = uri.getQueryParameter("n") ?: ""
        return when (uri.getQueryParameter("m")) {
            "x" -> {
                val s = uri.getQueryParameter("s") ?: return null
                val u = uri.getQueryParameter("u") ?: return null
                val p = uri.getQueryParameter("p") ?: return null
                Session(LoginMode.XTREAM, server = s, username = u, password = p, name = name)
            }
            "u" -> {
                val url = uri.getQueryParameter("url") ?: return null
                Session(LoginMode.M3U, m3uUrl = url, name = name)
            }
            else -> null
        }
    }

    /** يولّد صورة QR من نص */
    fun bitmap(text: String, size: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
