package com.guestexperience.shared.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64

/**
 * Reaches the Guest Welcome app on a Samsung (Tizen) TV through the TV's own multiscreen service
 * (port 8001). The app joins channel [CHANNEL_ID] as its host; the phone joins as a client and sends
 * each [WireMessage] as the JSON payload of an [EVENT] event addressed to the host.
 *
 * If the app isn't running, the phone asks the TV to start it (`POST /api/v2/applications/<id>`)
 * and waits for it to join the channel. Nothing here is secret: the messages are already
 * authenticated and encrypted by [SyncProtocol].
 */
class SamsungChannelDialer(
    private val host: String,
    private val phoneName: String,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = 5_000,
    private val hostWaitMs: Long = 15_000,
) : SyncDialer {

    override fun dial(): SyncConnection {
        val name = Base64.getEncoder().encodeToString(phoneName.take(40).toByteArray(Charsets.UTF_8))
        val path = "/api/v2/channels/$CHANNEL_ID?name=" + URLEncoder.encode(name, "UTF-8")
        val ws = MiniWebSocket.connect(host, port, path, connectTimeoutMs, SyncProtocol.SOCKET_TIMEOUT_MS)
        try {
            waitForHost(ws)
        } catch (e: Exception) {
            ws.close()
            throw e as? IOException ?: IOException(e)
        }
        return object : SyncConnection {
            override fun send(message: WireMessage) {
                val data = SyncProtocol.json.encodeToString(WireMessage.serializer(), message)
                val emit = buildJsonObject {
                    put("method", "ms.channel.emit")
                    put("params", buildJsonObject {
                        put("event", EVENT)
                        put("to", "host")
                        put("data", data)
                    })
                }
                ws.sendText(emit.toString())
            }

            override fun receive(): WireMessage {
                while (true) {
                    val event = parse(ws.receiveText())
                    when (event.name) {
                        EVENT -> {
                            val data = event.data as? JsonPrimitive ?: throw IOException("Unexpected reply")
                            return try {
                                SyncProtocol.json.decodeFromString(WireMessage.serializer(), data.content)
                            } catch (e: Exception) {
                                throw IOException("Unreadable reply", e)
                            }
                        }
                        "ms.channel.clientDisconnect" -> if (event.isHost) throw IOException("The TV app closed")
                        "ms.error" -> throw IOException("TV error: ${event.data}")
                    }
                }
            }

            override fun close() = ws.close()
        }
    }

    /** Reads channel events until the Guest Welcome app is on the channel, starting it if needed. */
    private fun waitForHost(ws: MiniWebSocket) {
        val first = parse(ws.receiveText())
        if (first.name == "ms.channel.unauthorized") throw IOException("The TV refused this phone")
        if (first.name != "ms.channel.connect") throw IOException("Unexpected first event ${first.name}")
        val clients = (first.data as? JsonObject)?.get("clients") as? JsonArray
        if (clients?.any { (it as? JsonObject)?.get("isHost")?.let { h -> (h as? JsonPrimitive)?.booleanOrNull } == true } == true) return

        startApp()
        val deadline = System.currentTimeMillis() + hostWaitMs
        while (System.currentTimeMillis() < deadline) {
            val event = parse(ws.receiveText())
            if (event.name == "ms.channel.ready" || (event.name == "ms.channel.clientConnect" && event.isHost)) return
        }
        throw IOException("Guest Welcome did not open on the TV")
    }

    /** Asks the TV to open Guest Welcome. A failure is fine: the app may already be opening. */
    private fun startApp() {
        runCatching { httpRequest(host, port, "POST", "/api/v2/applications/$APP_ID", "{}", connectTimeoutMs) }
    }

    private class Event(val name: String?, val data: Any?, val isHost: Boolean)

    private fun parse(text: String): Event = try {
        val obj = Json.parseToJsonElement(text).jsonObject
        val data = obj["data"]
        val isHost = (data as? JsonObject)?.get("isHost")?.let { (it as? JsonPrimitive)?.booleanOrNull } == true
        Event((obj["event"] as? JsonPrimitive)?.contentOrNull, data, isHost)
    } catch (e: Exception) {
        throw IOException("Unreadable channel message", e)
    }

    companion object {
        const val DEFAULT_PORT = 8001
        const val CHANNEL_ID = "com.guestexperience.welcome"
        const val APP_ID = "GuestExpTV.GuestWelcome"
        const val EVENT = "guesttv"

        /** DNS-SD service type Samsung TVs announce, in the form Android's NsdManager expects. */
        const val SERVICE_TYPE = "_samsungmsf._tcp"

        /**
         * Asks the device at [host] whether it is a Samsung smart TV (`GET /api/v2/`), for when DNS-SD
         * doesn't work on a network. Returns its id and name, or null if it isn't one or doesn't answer.
         */
        fun lookup(host: String, port: Int = DEFAULT_PORT, timeoutMs: Int = 4_000): SamsungTvInfo? = runCatching {
            val (status, body) = httpRequest(host, port, "GET", "/api/v2/", null, timeoutMs)
            if (status != 200) return null
            val obj = Json.parseToJsonElement(body).jsonObject
            val device = obj["device"] as? JsonObject
            fun str(o: JsonObject?, key: String) = (o?.get(key) as? JsonPrimitive)?.contentOrNull
            val id = str(device, "id") ?: str(obj, "id") ?: return null
            val name = (str(device, "name") ?: str(obj, "name") ?: "Samsung TV").removePrefix("[TV]").trim()
            SamsungTvInfo(id, name)
        }.getOrNull()

        /**
         * A tiny HTTP/1.1 exchange over a raw socket. Used instead of HttpURLConnection because Android
         * blocks cleartext `http://` there by default, and the TV's local service has no HTTPS on 8001.
         */
        internal fun httpRequest(host: String, port: Int, method: String, path: String, body: String?, timeoutMs: Int): Pair<Int, String> {
            java.net.Socket().use { s ->
                s.soTimeout = timeoutMs
                s.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                val bytes = body?.toByteArray(Charsets.UTF_8)
                val request = buildString {
                    append("$method $path HTTP/1.1\r\nHost: $host:$port\r\nConnection: close\r\nAccept: application/json\r\n")
                    if (bytes != null) append("Content-Type: application/json;charset=UTF-8\r\nContent-Length: ${bytes.size}\r\n")
                    append("\r\n")
                }
                s.getOutputStream().apply { write(request.toByteArray(Charsets.US_ASCII)); bytes?.let(::write); flush() }
                // Read to end of stream (Connection: close), capped. (readNBytes needs Android 13+.)
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8_192)
                val input = s.getInputStream()
                while (buffer.size() < 256 * 1024) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                }
                val response = buffer.toString(Charsets.UTF_8.name())
                val status = response.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                val headers = response.substringBefore("\r\n\r\n")
                var content = response.substringAfter("\r\n\r\n", "")
                if (headers.contains("transfer-encoding: chunked", ignoreCase = true)) content = dechunk(content)
                return status to content
            }
        }

        private fun dechunk(text: String): String {
            val out = StringBuilder()
            var rest = text
            while (true) {
                val size = rest.substringBefore("\r\n").trim().substringBefore(';').toIntOrNull(16) ?: break
                if (size == 0) break
                rest = rest.substringAfter("\r\n")
                out.append(rest.take(size))
                rest = rest.drop(size).removePrefix("\r\n")
            }
            return out.toString()
        }
    }
}

/** What a Samsung TV says about itself; [id] matches its DNS-SD record and the Guest Welcome app's id. */
data class SamsungTvInfo(val id: String, val name: String)
