package com.guestexperience.shared.sync

import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.validation.ContentProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** A phone the TV trusts. [keyB64] is the pairing key; the Android store keeps it encrypted at rest. */
@Serializable
data class PairedPhone(
    val phoneId: String,
    val name: String,
    val keyB64: String,
    val lastCounter: Long,
    val pairedAtMs: Long,
)

/** What the TV server needs from the app. Implemented over DataStore on Android and in memory in tests. */
interface TvSyncStore {
    val tvId: String
    val tvName: String
    suspend fun findPhone(phoneId: String): PairedPhone?
    suspend fun savePhone(phone: PairedPhone)
    suspend fun currentContent(): PropertyContent
    /** Validates and saves. Returns the problem if the content is rejected. */
    suspend fun applyContent(content: PropertyContent): ContentProblem?
}

/**
 * The TV side of the protocol in [SyncProtocol]. Pure JVM (java.net), so it is unit-tested on the
 * desktop; the Android app only adds DNS-SD advertising around it.
 */
class TvSyncServer(
    private val store: TvSyncStore,
    private val codes: PairingCodeManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    /** Binds to [port] (0 = any free port) and returns the bound port. */
    @Synchronized
    fun start(scope: CoroutineScope, port: Int = 0, bindAddress: InetAddress? = null): Int {
        serverSocket?.let { if (!it.isClosed) return it.localPort }
        val socket = ServerSocket(port, 8, bindAddress)
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                launch { handle(client) }
            }
        }
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
    }

    private suspend fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = SyncProtocol.SOCKET_TIMEOUT_MS
            val input = DataInputStream(BufferedInputStream(s.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            try {
                when (val first = FrameIO.read(input)) {
                    is PairHello -> pair(first, input, output)
                    is SecureEnvelope -> FrameIO.write(output, secure(first))
                    else -> FrameIO.write(output, ErrorMessage(SyncError.BAD_PROTOCOL))
                }
            } catch (_: Exception) {
                // Broken or hostile connection: drop it. Nothing is changed on the TV.
            }
        }
    }

    private suspend fun pair(hello: PairHello, input: DataInputStream, output: DataOutputStream) {
        if (hello.protocol != SyncProtocol.VERSION) {
            FrameIO.write(output, PairResult(ok = false, error = SyncError.BAD_PROTOCOL))
            return
        }
        val code = codes.current()
        if (code == null) {
            FrameIO.write(output, PairResult(ok = false, error = SyncError.NO_PAIRING_CODE))
            return
        }
        val phoneKey = SyncCrypto.decodePublicKey(hello.phonePublicKey)
        val tvKeys = SyncCrypto.newKeyPair()
        val tvPublic = SyncCrypto.encodePublicKey(tvKeys.public)
        val nonce = SyncCrypto.b64(SyncCrypto.randomBytes(16))
        FrameIO.write(output, PairChallenge(store.tvId, store.tvName, tvPublic, nonce))

        val confirm = FrameIO.read(input) as? PairConfirm ?: run {
            FrameIO.write(output, PairResult(ok = false, error = SyncError.BAD_PROTOCOL))
            return
        }
        val secret = SyncCrypto.sharedSecret(tvKeys.private, phoneKey)
        val pairKey = SyncCrypto.derivePairKey(secret, code, hello.phonePublicKey, tvPublic, nonce)
        val expected = SyncCrypto.confirmMac(pairKey, "phone", nonce)
        if (!SyncCrypto.macEquals(expected, confirm.mac)) {
            val stillOpen = codes.registerFailure()
            FrameIO.write(
                output,
                PairResult(ok = false, error = if (stillOpen) SyncError.WRONG_CODE else SyncError.TOO_MANY_ATTEMPTS),
            )
            return
        }
        codes.consume()
        store.savePhone(
            PairedPhone(
                phoneId = hello.phoneId,
                name = hello.phoneName.take(40),
                keyB64 = SyncCrypto.b64(pairKey),
                lastCounter = 0L,
                pairedAtMs = clock(),
            ),
        )
        FrameIO.write(output, PairResult(ok = true, mac = SyncCrypto.confirmMac(pairKey, "tv", nonce)))
    }

    private suspend fun secure(envelope: SecureEnvelope): WireMessage {
        val phone = store.findPhone(envelope.phoneId) ?: return ErrorMessage(SyncError.UNKNOWN_PHONE)
        if (envelope.counter <= phone.lastCounter) return ErrorMessage(SyncError.REPLAY)
        val key = SyncCrypto.unb64(phone.keyB64)
        val plaintext = try {
            SyncCrypto.open(key, envelope.iv, envelope.ciphertext, SyncCrypto.aad("p2t", phone.phoneId, envelope.counter))
        } catch (_: Exception) {
            return ErrorMessage(SyncError.DECRYPT_FAILED)
        }
        // Only an authentic message moves the counter forward.
        store.savePhone(phone.copy(lastCounter = envelope.counter))

        val reply: SecureReply = try {
            when (val command = SyncProtocol.json.decodeFromString(SecureCommand.serializer(), plaintext.toString(Charsets.UTF_8))) {
                GetContent -> ContentReply(store.currentContent())
                is PutContent -> {
                    val stamped = command.content.copy(updatedAt = clock())
                    val problem = store.applyContent(stamped)
                    if (problem == null) SavedReply(stamped.updatedAt) else RejectedReply(SyncError.INVALID_CONTENT, problem.field)
                }
            }
        } catch (_: Exception) {
            RejectedReply(SyncError.BAD_PROTOCOL)
        }
        val body = SyncProtocol.json.encodeToString(SecureReply.serializer(), reply).toByteArray(Charsets.UTF_8)
        val (iv, ct) = SyncCrypto.seal(key, body, SyncCrypto.aad("t2p", phone.phoneId, envelope.counter))
        return SecureEnvelope(phone.phoneId, envelope.counter, iv, ct)
    }
}
