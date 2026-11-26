package com.guestexperience.shared.sync

import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.model.WifiInfo
import com.guestexperience.shared.validation.ContentProblem
import com.guestexperience.shared.validation.ContentValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Runs the phone client over [SamsungChannelDialer] against a fake Samsung multiscreen service. The
 * fake relays each channel client's messages to the real [TvSyncServer] (standing in for the Samsung
 * TV app, which implements the same protocol in JavaScript), so pairing, encryption and replay
 * protection are exercised end to end over the WebSocket transport.
 */
class SamsungChannelTest {
    private class MemoryStore : TvSyncStore {
        override val tvId = "uuid:test-tv"
        override val tvName = "Samsung test TV"
        val phones = ConcurrentHashMap<String, PairedPhone>()
        @Volatile var content = PropertyContent(propertyName = "Lake House", guestName = "Before", wifi = WifiInfo("Lake-Guest", "lakeview2026"))
        override suspend fun findPhone(phoneId: String) = phones[phoneId]
        override suspend fun savePhone(phone: PairedPhone) { phones[phone.phoneId] = phone }
        override suspend fun currentContent() = content
        // The Samsung app applies the same rules as ContentValidator (samsung-tv/content.js).
        override suspend fun applyContent(content: PropertyContent): ContentProblem? =
            ContentValidator.validate(content) ?: run { this.content = content; null }
    }

    /** Minimal stand-in for the TV's port-8001 service: channel WebSocket plus the app-start endpoint. */
    private class FakeSamsungService(private val tvServerPort: Int, @Volatile var appRunning: Boolean) {
        val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val port get() = server.localPort
        @Volatile var startRequests = 0
        private val waitingClients = CopyOnWriteArrayList<OutputStream>()

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val s = runCatching { server.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { runCatching { handle(s) }; runCatching { s.close() } }
                }
            }
        }

        private fun handle(s: Socket) {
            val input = DataInputStream(BufferedInputStream(s.getInputStream()))
            val out = BufferedOutputStream(s.getOutputStream())
            val headers = generateSequence { readLine(input) }.takeWhile { it.isNotEmpty() }.toList()
            if (headers.first().startsWith("POST /api/v2/applications/${SamsungChannelDialer.APP_ID}")) {
                startRequests++
                out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".toByteArray()); out.flush()
                appRunning = true
                waitingClients.forEach { sendText(it, """{"event":"ms.channel.clientConnect","data":{"id":"tv","isHost":true}}""") }
                return
            }
            check(headers.first().startsWith("GET /api/v2/channels/${SamsungChannelDialer.CHANNEL_ID}?name="))
            check(headers.none { it.startsWith("Sec-WebSocket-Extensions", ignoreCase = true) }) { "client must not offer extensions" }
            val key = headers.first { it.startsWith("Sec-WebSocket-Key:") }.substringAfter(':').trim()
            val accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
            )
            out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
            val hostClient = if (appRunning) """{"id":"tv","isHost":true},""" else ""
            sendText(out, """{"event":"ms.channel.connect","data":{"id":"me","clients":[$hostClient{"id":"me","isHost":false}]}}""")
            if (!appRunning) waitingClients += out

            // One TCP connection to the TV protocol server per channel client, like one app session.
            Socket(InetAddress.getLoopbackAddress(), tvServerPort).use { tv ->
                val tvIn = DataInputStream(BufferedInputStream(tv.getInputStream()))
                val tvOut = DataOutputStream(BufferedOutputStream(tv.getOutputStream()))
                sendText(out, """{"event":"ms.channel.ping","data":{}}""") // noise the client must skip
                while (true) {
                    val text = readClientText(input) ?: return
                    val params = Json.parseToJsonElement(text).jsonObject["params"]!!.jsonObject
                    check(params["to"]!!.jsonPrimitive.content == "host")
                    val message = SyncProtocol.json.decodeFromString(WireMessage.serializer(), params["data"]!!.jsonPrimitive.content)
                    FrameIO.write(tvOut, message)
                    val reply = SyncProtocol.json.encodeToString(WireMessage.serializer(), FrameIO.read(tvIn))
                    val event = kotlinx.serialization.json.buildJsonObject {
                        put("event", kotlinx.serialization.json.JsonPrimitive(SamsungChannelDialer.EVENT))
                        put("from", kotlinx.serialization.json.JsonPrimitive("tv"))
                        put("data", kotlinx.serialization.json.JsonPrimitive(reply))
                    }
                    sendText(out, event.toString())
                }
            }
        }

        private fun readLine(input: DataInputStream): String {
            val b = ByteArrayOutputStream()
            while (true) {
                val c = input.readUnsignedByte()
                if (c == '\n'.code) return b.toString().trimEnd('\r')
                b.write(c)
            }
        }

        /** Reads one masked client text frame; null on close. */
        private fun readClientText(input: DataInputStream): String? {
            val b0 = input.readUnsignedByte()
            val b1 = input.readUnsignedByte()
            if (b0 and 0x0F == 0x8) return null
            check(b1 and 0x80 != 0) { "client frames must be masked" }
            val len = when (val l = b1 and 0x7F) { 126 -> input.readUnsignedShort(); 127 -> input.readLong().toInt(); else -> l }
            val mask = ByteArray(4).also(input::readFully)
            val p = ByteArray(len).also(input::readFully)
            return String(ByteArray(len) { (p[it].toInt() xor mask[it % 4].toInt()).toByte() }, Charsets.UTF_8)
        }

        @Synchronized
        private fun sendText(out: OutputStream, text: String) {
            val p = text.toByteArray(Charsets.UTF_8)
            synchronized(out) {
                out.write(0x81)
                if (p.size < 126) out.write(p.size) else { out.write(126); out.write(p.size shr 8); out.write(p.size and 0xFF) }
                out.write(p); out.flush()
            }
        }

        fun close() = server.close()
    }

    private var now = 5_000_000L
    private val store = MemoryStore()
    private val codes = PairingCodeManager(clock = { now })
    private val tvServer = TvSyncServer(store, codes, clock = { now })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HostSyncClient("phone-s", "Host phone", clock = { now })
    private lateinit var service: FakeSamsungService

    private fun dialer(hostWaitMs: Long = 5_000) =
        SamsungChannelDialer("127.0.0.1", "Host phone", port = service.port, hostWaitMs = hostWaitMs)

    @Before
    fun setUp() {
        val port = tvServer.start(scope, 0, InetAddress.getLoopbackAddress())
        service = FakeSamsungService(port, appRunning = true)
    }

    @After
    fun tearDown() {
        service.close()
        tvServer.stop()
        scope.cancel()
    }

    @Test
    fun `pairs, reads and sends content over the Samsung channel`() = runBlocking {
        val code = codes.start().code
        val paired = client.pair(dialer(), code)
        assertTrue("pairing failed: $paired", paired is PairOutcome.Paired)
        var tv = (paired as PairOutcome.Paired).tv.copy(kind = TvKind.SAMSUNG)
        assertEquals("uuid:test-tv", tv.tvId)

        val fetched = client.fetch(dialer(), tv)
        assertTrue(fetched is SyncResult.Ok)
        assertEquals("Before", (fetched as SyncResult.Ok).value.guestName)
        tv = fetched.tv

        now += 1_000
        val pushed = client.push(dialer(), tv, fetched.value.copy(guestName = "Ananya", hostMessage = "Chai is on the house"))
        assertTrue("push failed: $pushed", pushed is SyncResult.Ok)
        assertEquals("Ananya", store.content.guestName)
        tv = (pushed as SyncResult.Ok).tv

        now += 1_000
        val tooLong = client.push(dialer(), tv, store.content.copy(guestName = "x".repeat(41)))
        assertEquals(SyncResult.Failed(SyncError.INVALID_CONTENT, "guestName"), tooLong)
        assertEquals(0, service.startRequests)
    }

    @Test
    fun `wrong code is reported`() = runBlocking {
        val code = codes.start().code
        val wrong = if (code == "000000") "111111" else "000000"
        assertEquals(PairOutcome.Failed(SyncError.WRONG_CODE), client.pair(dialer(), wrong))
    }

    @Test
    fun `starts the TV app when it is not open`() = runBlocking {
        service.appRunning = false
        val code = codes.start().code
        val paired = client.pair(dialer(), code)
        assertTrue("pairing failed: $paired", paired is PairOutcome.Paired)
        assertEquals(1, service.startRequests)
    }

    /**
     * Opt-in check against a real Samsung TV running Guest Welcome: `GE_SAMSUNG_TV=<tv ip>`.
     * Without a code it looks the TV up by IP and joins the app's channel; with
     * `GE_SAMSUNG_CODE=<code shown>` it also pairs and reads the content.
     */
    @Test
    fun `real Samsung TV answers`() = runBlocking {
        val ip = System.getenv("GE_SAMSUNG_TV")
        org.junit.Assume.assumeTrue("set GE_SAMSUNG_TV to run", ip != null)
        val real = HostSyncClient("kotlin-test-phone", "Kotlin test")
        val dialer = SamsungChannelDialer(ip!!, "Kotlin test")
        val info = SamsungChannelDialer.lookup(ip)
        if (info == null || !info.id.startsWith("uuid:")) {
            val raw = runCatching { SamsungChannelDialer.httpRequest(ip, SamsungChannelDialer.DEFAULT_PORT, "GET", "/api/v2/", null, 4_000) }
            org.junit.Assert.fail("lookup failed: info=$info raw=${raw.map { "${it.first} ${it.second.take(200)}" }.getOrElse { it.toString() }}")
        }
        val code = System.getenv("GE_SAMSUNG_CODE")
        if (code == null) {
            // Only join the channel (opening the app if needed); never spend a pairing attempt.
            dialer.dial().close()
        } else {
            val paired = real.pair(dialer, code)
            assertTrue("pairing failed: $paired", paired is PairOutcome.Paired)
            val fetched = real.fetch(dialer, (paired as PairOutcome.Paired).tv)
            assertTrue("fetch failed: $fetched", fetched is SyncResult.Ok)
        }
    }

    @Test
    fun `lookup by IP reads a Samsung TV's id and name`() {
        val http = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            http.accept().use { s ->
                val input = s.getInputStream().bufferedReader()
                while (input.readLine().orEmpty().isNotEmpty()) Unit
                val body = """{"device":{"id":"uuid:abc","name":"[TV] Samsung 4 Series (32)"},"id":"uuid:abc"}"""
                // Chunked, as some TVs answer.
                val chunked = "${Integer.toHexString(body.length)}\r\n$body\r\n0\r\n\r\n"
                s.getOutputStream().write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n$chunked".toByteArray())
            }
        }
        assertEquals(SamsungTvInfo("uuid:abc", "Samsung 4 Series (32)"), SamsungChannelDialer.lookup("127.0.0.1", http.localPort))
        http.close()
        assertEquals(null, SamsungChannelDialer.lookup("127.0.0.1", http.localPort, timeoutMs = 500))
    }

    @Test
    fun `nothing listening is unreachable`() = runBlocking {
        service.close()
        assertEquals(PairOutcome.Failed(SyncError.UNREACHABLE), client.pair(dialer(), "123456"))
    }
}
