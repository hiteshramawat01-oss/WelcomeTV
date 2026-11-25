package com.guestexperience.shared.security

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A salted PBKDF2 hash of the host PIN. The PIN itself is never stored. */
@Serializable
data class PinHash(
    val saltB64: String,
    val hashB64: String,
    val iterations: Int,
)

object PinHasher {
    /** Tuned to stay under ~1 s on a 2 GB Android 12 TV; the hash runs off the main thread. */
    const val DEFAULT_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): PinHash {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinHash(b64(salt), b64(derive(pin, salt, iterations)), iterations)
    }

    fun verify(pin: String, stored: PinHash): Boolean {
        val salt = Base64.getDecoder().decode(stored.saltB64)
        val expected = Base64.getDecoder().decode(stored.hashB64)
        val actual = derive(pin, salt, stored.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}
