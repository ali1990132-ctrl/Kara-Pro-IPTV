package com.karastore.iptv.util

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.karastore.iptv.R

/** نص عبارة التفعيل مع رقم أكبر وأوضح */
object Watermark {
    fun text(ctx: Context): CharSequence {
        val label = ctx.getString(R.string.activation_label)
        val number = ctx.getString(R.string.support_number)
        val cyan = ContextCompat.getColor(ctx, R.color.cyan)
        val sb = SpannableStringBuilder()
        sb.append(label).append("  ")
        val start = sb.length
        sb.append(number)
        val end = sb.length
        sb.setSpan(RelativeSizeSpan(1.45f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(cyan), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }
}
