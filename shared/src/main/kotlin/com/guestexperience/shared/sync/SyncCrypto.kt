package com.guestexperience.shared.sync

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small, standard-library-only crypto helpers used by both sides of the sync protocol. */
object SyncCrypto {
    private val random = SecureRandom()
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), random)
    }.generateKeyPair()

    fun encodePublicKey(key: PublicKey): String = b64(key.encoded)

    fun decodePublicKey(b64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(b64)))

    fun sharedSecret(own: PrivateKey, peer: PublicKey): ByteArray = KeyAgreement.getInstance("ECDH").run {
        init(own)
        doPhase(peer, true)
        generateSecret()
    }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    /** Binds the pairing key to the ECDH secret, the code shown on the TV, both public keys and the TV's nonce. */
    fun derivePairKey(
        sharedSecret: ByteArray,
        code: String,
        phonePublicKey: String,
        tvPublicKey: String,
        nonce: String,
    ): ByteArray = hmac(
        sharedSecret,
        "guesttv-pair-v1|$code|$phonePublicKey|$tvPublicKey|$nonce".toByteArray(Charsets.UTF_8),
    )

    fun confirmMac(pairKey: ByteArray, role: String, nonce: String): String =
        b64(hmac(pairKey, "confirm|$role|$nonce".toByteArray(Charsets.UTF_8)))

    fun macEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    fun aad(direction: String, phoneId: String, counter: Long): ByteArray =
        "$direction|$phoneId|$counter".toByteArray(Charsets.UTF_8)

    /** Returns (iv, ciphertext), both base64. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): Pair<String, String> {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return b64(iv) to b64(cipher.doFinal(plaintext))
    }

    /** Throws if the ciphertext was tampered with or the key/aad do not match. */
    fun open(key: ByteArray, ivB64: String, ciphertextB64: String, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, unb64(ivB64)))
        cipher.updateAAD(aad)
        return cipher.doFinal(unb64(ciphertextB64))
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
