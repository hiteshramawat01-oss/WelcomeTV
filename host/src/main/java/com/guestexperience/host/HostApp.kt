package com.guestexperience.host

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.guestexperience.host.data.HostStore
import com.guestexperience.host.data.KeystoreCipher
import com.guestexperience.host.data.TvDiscovery
import com.guestexperience.shared.sync.HostSyncClient
import java.io.File
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guest_host")

/** Manual dependency container for the Host phone app. */
class HostContainer(context: Context) {
    private val app = context.applicationContext

    private val phoneId: String = File(app.filesDir, "phone_id").let { file ->
        file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { file.writeText(it) }
    }

    private val phoneName: String =
        Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    val store = HostStore(app.dataStore, KeystoreCipher())
    val discovery = TvDiscovery(app)
    val client = HostSyncClient(phoneId, phoneName)
}

class HostApp : Application() {
    val container: HostContainer by lazy { HostContainer(this) }
}

val Context.hostContainer: HostContainer get() = (applicationContext as HostApp).container
