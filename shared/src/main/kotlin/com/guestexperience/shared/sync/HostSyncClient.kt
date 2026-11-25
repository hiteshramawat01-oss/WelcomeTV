package com.guestexperience.shared.sync

import com.guestexperience.shared.model.PropertyContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** A TV the phone has paired with. [lastCounter] must be persisted after every request. */
@Serializable
data class PairedTv(
    val tvId: String,
    val name: String,
    val keyB64: String,
    val lastCounter: Long = 0L,
)

sealed interface SyncResult<out T> {
    data class Ok<T>(val value: T, val tv: PairedTv) : SyncResult<T>
    data class Failed(val error: SyncError, val field: String? = null) : SyncResult<Nothing>
}

sealed interface PairOutcome {
    data class Paired(val tv: PairedTv) : PairOutcome
    data class Failed(val error: SyncError) : PairOutcome
}

/** The phone side of [SyncProtocol]. Pure JVM; the Android app only supplies addresses found via DNS-SD. */
class HostSyncClient(
    private val phoneId: String,
    private val phoneName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val connectTimeoutMs: Int = 5_000,
) {
    suspend fun pair(host: String, port: Int, code: String): PairOutcome = withContext(Dispatchers.IO) {
        try {
            connect(host, port).use { s ->
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val keys = SyncCrypto.newKeyPair()
                val phonePublic = SyncCrypto.encodePublicKey(keys.public)
                FrameIO.write(output, PairHello(SyncProtocol.VERSION, phoneId, phoneName, phonePublic))

                val challenge = when (val reply = FrameIO.read(input)) {
                    is PairChallenge -> reply
                    is PairResult -> return@withContext PairOutcome.Failed(reply.error ?: SyncError.INTERNAL)
                    else -> return@withContext PairOutcome.Failed(SyncError.BAD_PROTOCOL)
                }
                val secret = SyncCrypto.sharedSecret(keys.private, SyncCrypto.decodePublicKey(challenge.tvPublicKey))
                val pairKey = SyncCrypto.derivePairKey(secret, code, phonePublic, challenge.tvPublicKey, challenge.nonce)
                FrameIO.write(output, PairConfirm(SyncCrypto.confirmMac(pairKey, "phone", challenge.nonce)))

                val result = FrameIO.read(input) as? PairResult
                    ?: return@withContext PairOutcome.Failed(SyncError.BAD_PROTOCOL)
                if (!result.ok) return@withContext PairOutcome.Failed(result.error ?: SyncError.INTERNAL)
                // The TV must prove it derived the same key, i.e. that it is the TV showing the code.
                val tvMac = result.mac ?: return@withContext PairOutcome.Failed(SyncError.BAD_PROTOCOL)
                if (!SyncCrypto.macEquals(tvMac, SyncCrypto.confirmMac(pairKey, "tv", challenge.nonce))) {
                    return@withContext PairOutcome.Failed(SyncError.WRONG_CODE)
                }
                PairOutcome.Paired(PairedTv(challenge.tvId, challenge.tvName, SyncCrypto.b64(pairKey)))
            }
        } catch (_: IOException) {
            PairOutcome.Failed(SyncError.UNREACHABLE)
        }
    }

    suspend fun fetch(host: String, port: Int, tv: PairedTv): SyncResult<PropertyContent> =
        when (val r = request(host, port, tv, GetContent)) {
            is SyncResult.Failed -> r
            is SyncResult.Ok -> when (val reply = r.value) {
                is ContentReply -> SyncResult.Ok(reply.content, r.tv)
                is RejectedReply -> SyncResult.Failed(reply.error, reply.field)
                is SavedReply -> SyncResult.Failed(SyncError.BAD_PROTOCOL)
            }
        }

    suspend fun push(host: String, port: Int, tv: PairedTv, content: PropertyContent): SyncResult<Long> =
        when (val r = request(host, port, tv, PutContent(content))) {
            is SyncResult.Failed -> r
            is SyncResult.Ok -> when (val reply = r.value) {
                is SavedReply -> SyncResult.Ok(reply.updatedAt, r.tv)
                is RejectedReply -> SyncResult.Failed(reply.error, reply.field)
                is ContentReply -> SyncResult.Failed(SyncError.BAD_PROTOCOL)
            }
        }

    /** Sends one encrypted command. The returned [PairedTv] carries the advanced counter; persist it. */
    suspend fun request(host: String, port: Int, tv: PairedTv, command: SecureCommand): SyncResult<SecureReply> =
        withContext(Dispatchers.IO) {
            val counter = maxOf(clock(), tv.lastCounter + 1)
            val updatedTv = tv.copy(lastCounter = counter)
            val key = SyncCrypto.unb64(tv.keyB64)
            val body = SyncProtocol.json.encodeToString(SecureCommand.serializer(), command).toByteArray(Charsets.UTF_8)
            val (iv, ct) = SyncCrypto.seal(key, body, SyncCrypto.aad("p2t", phoneId, counter))
            try {
                connect(host, port).use { s ->
                    val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                    val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                    FrameIO.write(output, SecureEnvelope(phoneId, counter, iv, ct))
                    when (val reply = FrameIO.read(input)) {
                        is ErrorMessage -> SyncResult.Failed(reply.error)
                        is SecureEnvelope -> {
                            if (reply.counter != counter) return@withContext SyncResult.Failed(SyncError.BAD_PROTOCOL)
                            val plain = try {
                                SyncCrypto.open(key, reply.iv, reply.ciphertext, SyncCrypto.aad("t2p", phoneId, counter))
                            } catch (_: Exception) {
                                return@withContext SyncResult.Failed(SyncError.DECRYPT_FAILED)
                            }
                            val decoded = SyncProtocol.json.decodeFromString(SecureReply.serializer(), plain.toString(Charsets.UTF_8))
                            SyncResult.Ok(decoded, updatedTv)
                        }
                        else -> SyncResult.Failed(SyncError.BAD_PROTOCOL)
                    }
                }
            } catch (_: IOException) {
                SyncResult.Failed(SyncError.UNREACHABLE)
            }
        }

    private fun connect(host: String, port: Int): Socket = Socket().apply {
        soTimeout = SyncProtocol.SOCKET_TIMEOUT_MS
        connect(InetSocketAddress(host, port), connectTimeoutMs)
    }
}
