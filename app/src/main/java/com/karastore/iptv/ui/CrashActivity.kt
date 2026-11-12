package com.karastore.iptv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.karastore.iptv.ui.login.LoginActivity

/** تعرض تفاصيل الخطأ عند الانهيار حتى يرسلها المستخدم */
class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace")
            ?: getSharedPreferences("crash", MODE_PRIVATE).getString("last", "") ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0E14.toInt())
            setPadding(32, 48, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "حدث خطأ — صوّر هذي الشاشة وأرسلها"
            setTextColor(0xFFF5B301.toInt())
            textSize = 16f
        })
        val copyBtn = Button(this).apply {
            text = "نسخ الخطأ"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
                Toast.makeText(this@CrashActivity, "تم النسخ", Toast.LENGTH_SHORT).show()
            }
        }
        val restartBtn = Button(this).apply {
            text = "إعادة التشغيل"
            setOnClickListener {
                startActivity(Intent(this@CrashActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }
        root.addView(copyBtn)
        root.addView(restartBtn)
        val sv = ScrollView(this)
        sv.addView(TextView(this).apply {
            text = trace
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, 24, 0, 0)
        })
        root.addView(sv)
        setContentView(root)
    }
}
