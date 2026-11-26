package com.guestexperience.shared.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest

/**
 * `update.json`, published with every sideload build as a GitHub Release asset
 * (.github/workflows/sideload-release.yml). Apps installed outside Google Play read it to offer
 * "Update available". Play builds never use it.
 */
@Serializable
data class UpdateManifest(
    val host: AppRelease? = null,
    val tv: AppRelease? = null,
) {
    fun forApp(app: UpdateChannel): AppRelease? = when (app) {
        UpdateChannel.HOST -> host
        UpdateChannel.TV -> tv
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Null when the text isn't a usable manifest. */
        fun parse(text: String): UpdateManifest? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

@Serializable
data class AppRelease(
    val versionCode: Long,
    val versionName: String,
    /** Download URL of the signed APK. Must be a release asset of [UpdateSource.REPOSITORY]. */
    val apk: String,
    /** Lower-case hex SHA-256 of the APK. */
    val sha256: String,
) {
    fun isNewerThan(installedVersionCode: Long): Boolean = versionCode > installedVersionCode

    /** Only accept downloads from this repository's releases over HTTPS, with a well-formed hash. */
    fun isTrusted(): Boolean =
        apk.startsWith(UpdateSource.DOWNLOAD_PREFIX) &&
            !apk.contains("..") &&
            apk.endsWith(".apk") &&
            Regex("^[0-9a-f]{64}$").matches(sha256)
}

enum class UpdateChannel { HOST, TV }

object UpdateSource {
    const val REPOSITORY = "hiteshramawat01-oss/WelcomeTV"
    const val MANIFEST_URL = "https://github.com/$REPOSITORY/releases/latest/download/update.json"
    const val DOWNLOAD_PREFIX = "https://github.com/$REPOSITORY/releases/download/"
}

object Sha256 {
    fun hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
