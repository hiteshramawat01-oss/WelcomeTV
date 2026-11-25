package com.guestexperience.shared.sync

import com.guestexperience.shared.model.ContentJson
import com.guestexperience.shared.model.PropertyContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Same-Wi-Fi protocol between the Host phone app and the TV.
 *
 * Transport: one TCP connection per request; each message is a 4-byte big-endian length followed by
 * UTF-8 JSON. The TV advertises itself with DNS-SD as [SERVICE_TYPE] only while the app is open.
 *
 * Pairing: the TV shows a 6-digit code. Phone and TV run an ephemeral ECDH (P-256) exchange and derive
 * the pairing key from the shared secret and the code; each side proves it knows the code with an HMAC.
 * A passive eavesdropper learns nothing; an active attacker on the same network during the 5-minute
 * pairing window could in theory brute-force the code, which is why codes expire and allow 5 attempts.
 *
 * After pairing, every request and reply is AES-256-GCM encrypted with the pairing key and carries a
 * strictly increasing counter, so recorded messages cannot be replayed.
 */
object SyncProtocol {
    const val VERSION = 1
    /** DNS-SD service type, in the form Android's NsdManager expects. */
    const val SERVICE_TYPE = "_guesttv._tcp"
    const val TXT_TV_ID = "id"
    const val TXT_VERSION = "v"
    const val MAX_FRAME_BYTES = 512 * 1024
    const val SOCKET_TIMEOUT_MS = 15_000
    const val PAIRING_CODE_TTL_MS = 5 * 60_000L
    const val MAX_CODE_ATTEMPTS = 5

    val json: Json = Json(from = ContentJson.json) {
        classDiscriminator = "type"
    }
}

@Serializable
enum class SyncError {
    BAD_PROTOCOL,
    NO_PAIRING_CODE,
    WRONG_CODE,
    TOO_MANY_ATTEMPTS,
    UNKNOWN_PHONE,
    REPLAY,
    DECRYPT_FAILED,
    INVALID_CONTENT,
    UNREACHABLE,
    INTERNAL,
}

@Serializable
sealed interface WireMessage

@Serializable
@SerialName("pair_hello")
data class PairHello(
    val protocol: Int,
    val phoneId: String,
    val phoneName: String,
    val phonePublicKey: String,
) : WireMessage

@Serializable
@SerialName("pair_challenge")
data class PairChallenge(
    val tvId: String,
    val tvName: String,
    val tvPublicKey: String,
    val nonce: String,
) : WireMessage

@Serializable
@SerialName("pair_confirm")
data class PairConfirm(val mac: String) : WireMessage

@Serializable
@SerialName("pair_result")
data class PairResult(
    val ok: Boolean,
    val mac: String? = null,
    val error: SyncError? = null,
) : WireMessage

@Serializable
@SerialName("secure")
data class SecureEnvelope(
    val phoneId: String,
    val counter: Long,
    val iv: String,
    val ciphertext: String,
) : WireMessage

@Serializable
@SerialName("error")
data class ErrorMessage(val error: SyncError) : WireMessage

/** Decrypted body of a phone → TV [SecureEnvelope]. */
@Serializable
sealed interface SecureCommand

@Serializable
@SerialName("get_content")
data object GetContent : SecureCommand

@Serializable
@SerialName("put_content")
data class PutContent(val content: PropertyContent) : SecureCommand

/** Decrypted body of a TV → phone [SecureEnvelope]. */
@Serializable
sealed interface SecureReply

@Serializable
@SerialName("content")
data class ContentReply(val content: PropertyContent) : SecureReply

@Serializable
@SerialName("saved")
data class SavedReply(val updatedAt: Long) : SecureReply

@Serializable
@SerialName("rejected")
data class RejectedReply(val error: SyncError, val field: String? = null) : SecureReply
