package com.guestexperience.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.guestexperience.shared.update.AppRelease
import com.guestexperience.shared.update.AppUpdates
import com.guestexperience.shared.update.Sha256
import com.guestexperience.shared.update.UpdateChannel
import com.guestexperience.shared.update.UpdateManifest
import com.guestexperience.shared.update.UpdateSource
import com.guestexperience.shared.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Checks the latest GitHub Release of this repository for a newer build of this app, downloads it,
 * verifies it and opens Android's install dialog. Nothing is installed without the user confirming.
 *
 * Checks, in order: the download URL is a release asset of [UpdateSource.REPOSITORY] over HTTPS;
 * the file's SHA-256 matches `update.json`; the APK is this app's package; and it is signed with
 * the same key as the installed app (Android would refuse a different key anyway).
 */
class GitHubUpdater(
    context: Context,
    private val channel: UpdateChannel,
    private val installedVersionCode: Long,
    override val installedVersionName: String,
) : AppUpdates {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = app.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        active = this
    }

    override fun checkIfDue() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last >= CHECK_INTERVAL_MS || _state.value is UpdateState.Available) check()
    }

    @Synchronized
    override fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val text = get(UpdateSource.MANIFEST_URL).use { it.readBytes().toString(Charsets.UTF_8) }
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                val release = UpdateManifest.parse(text)?.forApp(channel)
                when {
                    release == null || !release.isNewerThan(installedVersionCode) -> UpdateState.UpToDate
                    !release.isTrusted() -> UpdateState.Failed("The update file looks wrong, so it was not used.")
                    else -> UpdateState.Available(release)
                }
            } catch (_: IOException) {
                UpdateState.Failed("Couldn't check for updates. Is this device online?")
            }
        }
    }

    @Synchronized
    override fun install() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.NeedsPermission -> s.release
            is UpdateState.Failed -> s.release
            else -> null
        } ?: return
        if (job?.isActive == true) return
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        job = scope.launch {
            _state.value = try {
                val apk = download(release)
                when {
                    !isSameApp(apk) -> UpdateState.Failed("The downloaded update isn't signed like this app, so it was not installed.", release)
                    else -> {
                        startInstall(apk)
                        UpdateState.Installing(release)
                    }
                }
            } catch (e: SecurityException) {
                UpdateState.Failed(e.message ?: "The update didn't pass its checks.", release)
            } catch (_: IOException) {
                UpdateState.Failed("The download didn't finish. Try again.", release)
            }
        }
    }

    override fun openInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
            .onFailure { runCatching { app.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** Called by [InstallResultReceiver] when Android reports the outcome. */
    internal fun onInstallResult(success: Boolean, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = if (success) UpdateState.UpToDate else UpdateState.Failed(message ?: "The update was not installed.", release)
    }

    private fun get(url: String) = (URL(url).openConnection() as HttpsURLConnection).run {
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true // release assets redirect to GitHub's download host (HTTPS)
        setRequestProperty("Accept", "application/octet-stream, application/json")
        if (responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP $responseCode")
        inputStream
    }

    private fun download(release: AppRelease): File {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update-${release.versionCode}.apk")
        val connection = URL(release.apk).openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
        val total = connection.contentLengthLong.takeIf { it > 0 }
        if (total != null && total > MAX_APK_BYTES) throw SecurityException("The update is unexpectedly large, so it was not used.")
        connection.inputStream.use { input ->
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var lastPercent = -1
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    if (done > MAX_APK_BYTES) throw SecurityException("The update is unexpectedly large, so it was not used.")
                    val percent = total?.let { (done * 100 / it).toInt() } ?: 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _state.value = UpdateState.Downloading(release, percent)
                    }
                }
            }
        }
        val hash = file.inputStream().use(Sha256::hex)
        if (hash != release.sha256) {
            file.delete()
            throw SecurityException("The download doesn't match the published checksum, so it was not installed.")
        }
        return file
    }

    @Suppress("DEPRECATION")
    private fun isSameApp(apk: File): Boolean {
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        if (archive.packageName != app.packageName) return false
        val installed = pm.getPackageInfo(app.packageName, flags)
        val theirs = signers(archive)
        return theirs.isNotEmpty() && theirs == signers(installed)
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        return raw.orEmpty().map { Sha256.hex(it.toByteArray().inputStream()) }.toSet()
    }

    private fun startInstall(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(app, sessionId, Intent(app, InstallResultReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val MAX_APK_BYTES = 100L * 1024 * 1024

        @Volatile
        internal var active: GitHubUpdater? = null
    }
}
