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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/** Runs the real TV server and phone client over localhost sockets. */
class SyncIntegrationTest {
    private class MemoryStore : TvSyncStore {
        override val tvId = "tv-1"
        override val tvName = "Living room TV"
        val phones = ConcurrentHashMap<String, PairedPhone>()
        @Volatile var content = PropertyContent(propertyName = "Pichola Haveli", wifi = WifiInfo("Old", "12345678"))
        override suspend fun findPhone(phoneId: String) = phones[phoneId]
        override suspend fun savePhone(phone: PairedPhone) { phones[phone.phoneId] = phone }
        override suspend fun currentContent() = content
        override suspend fun applyContent(content: PropertyContent): ContentProblem? =
            ContentValidator.validate(content) ?: run { this.content = content; null }
    }

    private var now = 1_000_000L
    private val store = MemoryStore()
    private val codes = PairingCodeManager(clock = { now })
    private val server = TvSyncServer(store, codes, clock = { now })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val host = InetAddress.getLoopbackAddress().hostAddress
    private var port = 0
    private val client = HostSyncClient("phone-1", "Host phone", clock = { now })

    @Before
    fun setUp() {
        port = server.start(scope, 0, InetAddress.getLoopbackAddress())
    }

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun pairOrFail(): PairedTv = runBlocking {
        val code = codes.start().code
        val outcome = client.pair(host, port, code)
        assertTrue("pairing failed: $outcome", outcome is PairOutcome.Paired)
        (outcome as PairOutcome.Paired).tv
    }

    @Test
    fun `pairing needs an open code`() = runBlocking {
        assertEquals(PairOutcome.Failed(SyncError.NO_PAIRING_CODE), client.pair(host, port, "123456"))
    }

    @Test
    fun `wrong code fails and too many attempts withdraw the code`() = runBlocking {
        val code = codes.start().code
        val wrong = if (code == "000000") "111111" else "000000"
        repeat(SyncProtocol.MAX_CODE_ATTEMPTS - 1) {
            assertEquals(PairOutcome.Failed(SyncError.WRONG_CODE), client.pair(host, port, wrong))
        }
        assertEquals(PairOutcome.Failed(SyncError.TOO_MANY_ATTEMPTS), client.pair(host, port, wrong))
        // Even the right code no longer works.
        assertEquals(PairOutcome.Failed(SyncError.NO_PAIRING_CODE), client.pair(host, port, code))
        assertTrue(store.phones.isEmpty())
    }

    @Test
    fun `expired code is refused`() = runBlocking {
        val code = codes.start().code
        now += SyncProtocol.PAIRING_CODE_TTL_MS
        assertEquals(PairOutcome.Failed(SyncError.NO_PAIRING_CODE), client.pair(host, port, code))
    }

    @Test
    fun `pair then fetch and push content`() = runBlocking {
        var tv = pairOrFail()
        assertEquals("tv-1", tv.tvId)
        assertEquals("Living room TV", tv.name)
        assertNotNull(store.phones["phone-1"])
        assertNull("code is single-use", codes.current())

        now += 1
        val fetched = client.fetch(host, port, tv)
        assertTrue(fetched is SyncResult.Ok)
        tv = (fetched as SyncResult.Ok).tv
        assertEquals("Pichola Haveli", fetched.value.propertyName)

        now += 1
        val updated = fetched.value.copy(guestName = "Ananya", wifi = WifiInfo("Pichola-Guest", "lakeview2026"))
        val pushed = client.push(host, port, tv, updated)
        assertTrue(pushed is SyncResult.Ok)
        assertEquals("Ananya", store.content.guestName)
        assertEquals("lakeview2026", store.content.wifi.password)
        assertEquals(now, store.content.updatedAt)
    }

    @Test
    fun `invalid content is rejected with the field name`() = runBlocking {
        val tv = pairOrFail()
        now += 1
        val result = client.push(host, port, tv, store.content.copy(wifi = WifiInfo("x", "short")))
        assertEquals(SyncResult.Failed(SyncError.INVALID_CONTENT, "wifi.password"), result)
        assertEquals("Old", store.content.wifi.ssid)
    }

    @Test
    fun `replayed request is refused`() = runBlocking {
        val tv = pairOrFail()
        now += 1
        val first = client.fetch(host, port, tv)
        assertTrue(first is SyncResult.Ok)
        // Re-using the old counter (as a recorded message would) fails.
        val replay = client.fetch(host, port, tv.copy(lastCounter = 0L).let { it.copy(lastCounter = now - 1) })
        assertEquals(SyncResult.Failed(SyncError.REPLAY), replay)
    }

    @Test
    fun `unknown phone and wrong key are refused`() = runBlocking {
        val tv = pairOrFail()
        now += 1
        val stranger = HostSyncClient("phone-2", "Stranger", clock = { now })
        assertEquals(SyncResult.Failed(SyncError.UNKNOWN_PHONE), stranger.fetch(host, port, tv))
        val forged = tv.copy(keyB64 = SyncCrypto.b64(ByteArray(32)))
        assertEquals(SyncResult.Failed(SyncError.DECRYPT_FAILED), client.fetch(host, port, forged))
    }

    @Test
    fun `garbage frames do not crash the server`() = runBlocking {
        Socket(host, port).use { s ->
            DataOutputStream(BufferedOutputStream(s.getOutputStream())).apply {
                writeInt(5); write("hello".toByteArray()); flush()
            }
            // Server closes the connection.
            assertEquals(-1, DataInputStream(BufferedInputStream(s.getInputStream())).read())
        }
        val tv = pairOrFail()
        now += 1
        assertTrue(client.fetch(host, port, tv) is SyncResult.Ok)
    }
}
