package com.guestexperience.host.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets (the Wi-Fi password, pairing keys) before they are written to disk. */
interface SecretCipher {
    fun encrypt(plain: String): String
    /** Returns null if the data cannot be decrypted, e.g. after the key was wiped. */
    fun decrypt(blob: String): String?
}

/** AES-256-GCM with a key that lives in the Android Keystore and never leaves the device. */
class KeystoreCipher(private val alias: String = "guest_host_secrets") : SecretCipher {

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + ct, Base64.NO_WRAP)
    }

    override fun decrypt(blob: String): String? = try {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val ivLength = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val ct = bytes.copyOfRange(1 + ivLength, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.doFinal(ct).toString(Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
