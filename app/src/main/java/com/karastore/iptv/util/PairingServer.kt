package com.karastore.iptv.util

import com.karastore.iptv.data.LoginMode
import com.karastore.iptv.data.Session
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * سيرفر محلي صغير: التلفزيون يعرض باركود برابطه، الموبايل يفتح الصفحة
 * ويعبّي بيانات الاشتراك، فتصل للتلفزيون مباشرة عبر نفس شبكة الـWi-Fi.
 */
class PairingServer(
    port: Int,
    private val onReceive: (Session) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method == Method.POST && session.uri == "/submit") {
                val body = HashMap<String, String>()
                session.parseBody(body)
                val p = session.parameters
                fun v(k: String) = p[k]?.firstOrNull()?.trim().orEmpty()
                val name = v("n")
                val s = if (v("m") == "u") {
                    Session(LoginMode.M3U, m3uUrl = v("url"), name = name)
                } else {
                    Session(LoginMode.XTREAM, server = v("s"), username = v("u"), password = v("p"), name = name)
                }
                val ok = (s.mode == LoginMode.M3U && s.m3uUrl.isNotEmpty()) ||
                    (s.mode == LoginMode.XTREAM && s.server.isNotEmpty() && s.username.isNotEmpty() && s.password.isNotEmpty())
                if (ok) {
                    onReceive(s)
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", DONE_HTML)
                } else {
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", FORM_HTML)
                }
            } else {
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", FORM_HTML)
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "error")
        }
    }

    companion object {
        const val PORT = 8808

        /** عنوان الجهاز على شبكة الـWi-Fi المحلية */
        fun localIp(): String? {
            try {
                val nifs = NetworkInterface.getNetworkInterfaces() ?: return null
                for (nif in nifs) {
                    if (!nif.isUp || nif.isLoopback) continue
                    for (addr in nif.inetAddresses) {
                        if (addr.isLoopbackAddress || addr !is Inet4Address) continue
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") ||
                            Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.").containsMatchIn(ip)
                        ) return ip
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        private val FORM_HTML = """
<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>A&amp;S IPTV — إدخال الاشتراك</title>
<style>
*{box-sizing:border-box;font-family:-apple-system,Roboto,Arial,sans-serif}
body{margin:0;background:#0B1220;color:#EAF2FF;padding:22px}
.card{max-width:460px;margin:0 auto;background:#111C30;border:1px solid #1E9BFF33;border-radius:16px;padding:20px}
h2{margin:0 0 4px;font-size:20px}
p.sub{margin:0 0 16px;color:#9FB6D4;font-size:13px}
label{display:block;margin:12px 0 6px;font-size:13px;color:#CFE4FF}
input,select{width:100%;padding:13px;border-radius:10px;border:1px solid #294066;background:#0C1626;color:#fff;font-size:15px}
.tabs{display:flex;gap:8px;margin-bottom:6px}
.tabs button{flex:1;padding:10px;border-radius:10px;border:1px solid #294066;background:#0C1626;color:#CFE4FF;font-size:14px;cursor:pointer}
.tabs button.on{background:#1E9BFF;border-color:#1E9BFF;color:#fff;font-weight:bold}
.submit{margin-top:18px;width:100%;padding:14px;border:0;border-radius:12px;background:#1E9BFF;color:#fff;font-size:16px;font-weight:bold;cursor:pointer}
.hide{display:none}
</style></head><body>
<div class="card">
<h2>إدخال بيانات الاشتراك</h2>
<p class="sub">عبّي البيانات وانقر إرسال — راح توصل للشاشة مباشرة</p>
<form method="POST" action="/submit">
<div class="tabs">
<button type="button" id="tx" class="on" onclick="setMode('x')">Xtream</button>
<button type="button" id="tu" onclick="setMode('u')">رابط M3U</button>
</div>
<input type="hidden" name="m" id="m" value="x">
<div id="gx">
<label>السيرفر</label><input name="s" placeholder="http://host:port" autocapitalize="off">
<label>اسم المستخدم</label><input name="u" autocapitalize="off">
<label>كلمة المرور</label><input name="p" autocapitalize="off">
</div>
<div id="gu" class="hide">
<label>رابط M3U</label><input name="url" placeholder="http://.../get.php?..." autocapitalize="off">
</div>
<label>اسم الاشتراك (اختياري)</label><input name="n">
<button class="submit" type="submit">إرسال للشاشة</button>
</form></div>
<script>
function setMode(x){document.getElementById('m').value=x;
document.getElementById('tx').className=x=='x'?'on':'';
document.getElementById('tu').className=x=='u'?'on':'';
document.getElementById('gx').className=x=='x'?'':'hide';
document.getElementById('gu').className=x=='u'?'':'hide';}
</script></body></html>
""".trimIndent()

        private val DONE_HTML = """
<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>تم</title><style>
body{margin:0;background:#0B1220;color:#EAF2FF;font-family:-apple-system,Roboto,Arial,sans-serif;
display:flex;align-items:center;justify-content:center;height:100vh;text-align:center}
.box{padding:24px}.c{font-size:56px;color:#28C76F}
h2{margin:14px 0 6px}p{color:#9FB6D4}
</style></head><body><div class="box">
<div class="c">✓</div><h2>تم الإرسال بنجاح</h2>
<p>ارجع للشاشة — جارٍ تسجيل الدخول</p>
</div></body></html>
""".trimIndent()
    }
}
