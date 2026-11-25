package com.guestexperience.tv.di

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.guestexperience.shared.sync.PairingCodeManager
import com.guestexperience.shared.sync.TvSyncServer
import com.guestexperience.tv.data.ImageStore
import com.guestexperience.tv.data.KeystoreCipher
import com.guestexperience.tv.data.SyncController
import com.guestexperience.tv.data.TvRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guest_tv")

/** Manual dependency container: one instance per process, owned by [com.guestexperience.tv.GuestTvApp]. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tvId: String = File(app.filesDir, "tv_id").let { file ->
        file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { file.writeText(it) }
    }

    val deviceName: () -> String = {
        Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
    }

    val repository = TvRepository(
        store = app.dataStore,
        cipher = KeystoreCipher(),
        tvId = tvId,
        deviceName = deviceName,
    )

    val imageStore = ImageStore(app)

    val pairingCodes = PairingCodeManager()

    val syncController = SyncController(
        context = app,
        server = TvSyncServer(repository, pairingCodes),
        tvId = tvId,
        serviceName = deviceName,
        scope = appScope,
    )
}
