package com.asvpn.player.vpn

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.asvpn.player.data.Net
import com.asvpn.player.data.Store
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Vpn {
    enum class State { OFF, CONNECTING, ON, ERROR }

    var state: State = State.OFF
        private set
    var lastError: String? = null
        private set

    private val listeners = mutableSetOf<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var backend: GoBackend? = null

    private val tunnel = object : Tunnel {
        override fun getName(): String = "asvpn"
        override fun onStateChange(newState: Tunnel.State) {
            set(if (newState == Tunnel.State.UP) State.ON else State.OFF)
        }
    }

    fun addListener(l: (State) -> Unit) { listeners.add(l); l(state) }
    fun removeListener(l: (State) -> Unit) { listeners.remove(l) }

    private fun set(s: State, err: String? = null) {
        state = s
        lastError = err
        main.post { listeners.toList().forEach { it(s) } }
    }

    private fun backend(ctx: Context): GoBackend =
        backend ?: GoBackend(ctx.applicationContext).also { backend = it }

    fun refresh(ctx: Context) {
        try {
            val up = backend(ctx).getState(tunnel) == Tunnel.State.UP
            set(if (up) State.ON else State.OFF)
        } catch (_: Throwable) {
        }
    }

    suspend fun connect(ctx: Context) = withContext(Dispatchers.IO) {
        set(State.CONNECTING)
        try {
            val text = if (Store.vpnMode == 1) {
                Store.str("custom_conf").ifBlank { throw Exception("لم تضف سيرفرك الخاص بعد") }
            } else warpConfig()
            val config = Config.parse(BufferedReader(StringReader(text)))
            backend(ctx).setState(tunnel, Tunnel.State.UP, config)
            set(State.ON)
        } catch (e: Throwable) {
            try { backend(ctx).setState(tunnel, Tunnel.State.DOWN, null) } catch (_: Throwable) {}
            set(State.ERROR, e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun disconnect(ctx: Context) = withContext(Dispatchers.IO) {
        try { backend(ctx).setState(tunnel, Tunnel.State.DOWN, null) } catch (_: Throwable) {}
        set(State.OFF)
    }

    /** Validates a WireGuard config text, returns a short description or throws. */
    fun describe(text: String): String {
        val c = Config.parse(BufferedReader(StringReader(text)))
        val peer = c.peers.firstOrNull() ?: throw Exception("لا يوجد Peer في الملف")
        val ep = peer.endpoint.map { it.toString() }.orElse("—")
        return "السيرفر: $ep"
    }

    fun resetWarp() = Store.putStr("warp_conf", "")

    // ---------------- Cloudflare WARP ----------------

    private const val WARP_PEER_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

    private fun warpConfig(): String {
        val saved = Store.str("warp_conf")
        if (saved.isNotBlank()) return saved

        val kp = KeyPair()
        val reg = register(kp.publicKey.toBase64())
            ?: throw Exception("تعذّر الاتصال بـ WARP، تأكد من الإنترنت وحاول مجدداً")

        var peerKey = WARP_PEER_KEY
        var endpoint = "162.159.192.1:2408"
        var v4 = "172.16.0.2"
        var v6: String? = null

        val cfg = reg.getAsJsonObject("config")
        cfg?.getAsJsonArray("peers")?.firstOrNull()?.asJsonObject?.let { p ->
            p.get("public_key")?.asString?.takeIf { it.isNotBlank() }?.let { peerKey = it }
            p.getAsJsonObject("endpoint")?.get("v4")?.asString
                ?.substringBeforeLast(":")?.takeIf { it.isNotBlank() }
                ?.let { endpoint = "$it:2408" }
        }
        cfg?.getAsJsonObject("interface")?.getAsJsonObject("addresses")?.let { a ->
            a.get("v4")?.asString?.let { v4 = it }
            v6 = a.get("v6")?.asString
        }

        val addr = buildString {
            append("$v4/32")
            v6?.takeIf { it.isNotBlank() }?.let { append(", $it/128") }
        }
        val conf = """
            [Interface]
            PrivateKey = ${kp.privateKey.toBase64()}
            Address = $addr
            DNS = 1.1.1.1, 1.0.0.1
            MTU = 1280

            [Peer]
            PublicKey = $peerKey
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = $endpoint
            PersistentKeepalive = 25
        """.trimIndent()
        Store.putStr("warp_conf", conf)
        return conf
    }

    private fun register(publicKey: String): JsonObject? {
        val tos = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        val versions = listOf("v0a2158" to "a-6.10-2158", "v0a1922" to "a-6.3-1922")
        for ((v, cv) in versions) {
            try {
                val body = JsonObject().apply {
                    addProperty("key", publicKey)
                    addProperty("install_id", "")
                    addProperty("fcm_token", "")
                    addProperty("tos", tos)
                    addProperty("model", "PC")
                    addProperty("serial_number", "")
                    addProperty("locale", "en_US")
                }
                val req = Request.Builder()
                    .url("https://api.cloudflareclient.com/$v/reg")
                    .header("User-Agent", "okhttp/3.12.1")
                    .header("CF-Client-Version", cv)
                    .post(body.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .build()
                Net.client.newCall(req).execute().use { r ->
                    val txt = r.body?.string().orEmpty()
                    if (r.isSuccessful && txt.isNotBlank()) {
                        val j = JsonParser.parseString(txt).asJsonObject
                        if (j.has("config")) return j
                        val res = j.getAsJsonObject("result")
                        if (res != null && res.has("config")) return res
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
