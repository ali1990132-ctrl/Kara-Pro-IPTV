package com.asvpn.player.ui

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.asvpn.player.R
import com.asvpn.player.data.Store
import com.asvpn.player.vpn.Vpn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class VpnFragment : Fragment() {

    private val listener: (Vpn.State) -> Unit = { render(it) }

    private val askPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) doConnect()
        else Toast.makeText(requireContext(), "يجب السماح بإنشاء اتصال VPN", Toast.LENGTH_SHORT).show()
    }

    private val pickConf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try {
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }
            saveCustom(text)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_vpn, c, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        v.findViewById<View>(R.id.powerBtn).setOnClickListener {
            when (Vpn.state) {
                Vpn.State.ON -> lifecycleScope.launch { Vpn.disconnect(requireContext()) }
                Vpn.State.CONNECTING -> Unit
                else -> {
                    val intent = VpnService.prepare(requireContext())
                    if (intent != null) askPermission.launch(intent) else doConnect()
                }
            }
        }
        val group = v.findViewById<RadioGroup>(R.id.modeGroup)
        group.check(if (Store.vpnMode == 1) R.id.modeCustom else R.id.modeWarp)
        group.setOnCheckedChangeListener { _, id ->
            Store.vpnMode = if (id == R.id.modeCustom) 1 else 0
            updateCustomInfo()
            if (Vpn.state == Vpn.State.ON) Toast.makeText(requireContext(), "أعد الاتصال لتطبيق التغيير", Toast.LENGTH_SHORT).show()
        }
        v.findViewById<View>(R.id.btnImportFile).setOnClickListener { pickConf.launch(arrayOf("*/*")) }
        v.findViewById<View>(R.id.btnPasteConf).setOnClickListener {
            Ui.input(requireContext(), "إعدادات WireGuard", "[Interface]\nPrivateKey = …", Store.str("custom_conf"), multiLine = true, ltr = true) {
                saveCustom(it)
            }
        }
        v.findViewById<View>(R.id.btnResetWarp).setOnClickListener {
            Ui.confirm(requireContext(), "إنشاء حساب WARP جديد؟ سيتم قطع الاتصال الحالي.") {
                lifecycleScope.launch {
                    Vpn.disconnect(requireContext())
                    Vpn.resetWarp()
                    Toast.makeText(requireContext(), "تم. اضغط زر الاتصال", Toast.LENGTH_SHORT).show()
                }
            }
        }
        updateCustomInfo()
    }

    private fun saveCustom(text: String) {
        if (text.isBlank()) return
        try {
            val info = Vpn.describe(text)
            Store.putStr("custom_conf", text)
            Store.vpnMode = 1
            view?.findViewById<RadioGroup>(R.id.modeGroup)?.check(R.id.modeCustom)
            updateCustomInfo()
            Toast.makeText(requireContext(), "تم حفظ السيرفر ✓\n$info", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(requireContext()).setTitle("ملف غير صالح")
                .setMessage("تأكد أنه ملف إعدادات WireGuard (.conf)\n\n${e.message ?: ""}")
                .setPositiveButton("حسناً", null).show()
        }
    }

    private fun updateCustomInfo() {
        val tv = view?.findViewById<TextView>(R.id.customInfo) ?: return
        val conf = Store.str("custom_conf")
        tv.text = if (conf.isBlank()) "لم تضف سيرفراً خاصاً بعد"
        else try { Vpn.describe(conf) } catch (_: Exception) { "إعدادات غير صالحة" }
    }

    private fun doConnect() {
        lifecycleScope.launch { Vpn.connect(requireContext().applicationContext) }
    }

    override fun onStart() {
        super.onStart()
        Vpn.addListener(listener)
    }

    override fun onStop() {
        super.onStop()
        Vpn.removeListener(listener)
    }

    private fun render(s: Vpn.State) {
        val v = view ?: return
        val btn = v.findViewById<View>(R.id.powerBtn)
        val icon = v.findViewById<ImageView>(R.id.powerIcon)
        val status = v.findViewById<TextView>(R.id.status)
        val sub = v.findViewById<TextView>(R.id.statusSub)
        val ctx = requireContext()
        val server = if (Store.vpnMode == 1) "سيرفرك الخاص" else "Cloudflare WARP"
        when (s) {
            Vpn.State.ON -> {
                btn.setBackgroundResource(R.drawable.bg_power_on)
                icon.setColorFilter(ContextCompat.getColor(ctx, android.R.color.white))
                status.text = "متصل"; status.setTextColor(ContextCompat.getColor(ctx, R.color.green))
                sub.text = "اتصالك محمي عبر $server"
            }
            Vpn.State.CONNECTING -> {
                btn.setBackgroundResource(R.drawable.bg_power_wait)
                icon.setColorFilter(ContextCompat.getColor(ctx, android.R.color.white))
                status.text = "جاري الاتصال…"; status.setTextColor(ContextCompat.getColor(ctx, R.color.orange))
                sub.text = server
            }
            Vpn.State.ERROR -> {
                btn.setBackgroundResource(R.drawable.bg_power_off)
                icon.setColorFilter(ContextCompat.getColor(ctx, R.color.red))
                status.text = "فشل الاتصال"; status.setTextColor(ContextCompat.getColor(ctx, R.color.red))
                sub.text = Vpn.lastError ?: "حاول مرة أخرى"
            }
            Vpn.State.OFF -> {
                btn.setBackgroundResource(R.drawable.bg_power_off)
                icon.setColorFilter(ContextCompat.getColor(ctx, R.color.text2))
                status.text = "غير متصل"; status.setTextColor(ContextCompat.getColor(ctx, R.color.text))
                sub.text = "اضغط الزر للاتصال عبر $server"
            }
        }
    }
}
