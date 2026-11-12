package com.karastore.iptv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    /** تحويل exp_date (unix بالثواني) إلى تاريخ مقروء */
    fun expDate(unix: String?): String? {
        val secs = unix?.toLongOrNull() ?: return null
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        return sdf.format(Date(secs * 1000))
    }

    fun isExpired(unix: String?): Boolean {
        val secs = unix?.toLongOrNull() ?: return false
        return secs * 1000 < System.currentTimeMillis()
    }
}
