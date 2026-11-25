package com.guestexperience.host.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.guestexperience.shared.sync.PairedTv
import com.guestexperience.shared.sync.SyncProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The one TV this phone is paired with. Its pairing key is stored encrypted with the Keystore key. */
class HostStore(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    private val key = stringPreferencesKey("paired_tv")

    val pairedTv: Flow<PairedTv?> = store.data.map { prefs ->
        prefs[key]
            ?.let { runCatching { SyncProtocol.json.decodeFromString(PairedTv.serializer(), it) }.getOrNull() }
            ?.let { tv -> cipher.decrypt(tv.keyB64)?.let { tv.copy(keyB64 = it) } }
    }.distinctUntilChanged()

    suspend fun save(tv: PairedTv) {
        val encrypted = tv.copy(keyB64 = cipher.encrypt(tv.keyB64))
        store.edit { it[key] = SyncProtocol.json.encodeToString(PairedTv.serializer(), encrypted) }
    }

    suspend fun forget() {
        store.edit { it.remove(key) }
    }
}
