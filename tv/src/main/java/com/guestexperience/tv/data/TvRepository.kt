package com.guestexperience.tv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.guestexperience.shared.model.ContentJson
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.security.LockoutState
import com.guestexperience.shared.security.PinHash
import com.guestexperience.shared.security.PinHasher
import com.guestexperience.shared.security.PinLockoutPolicy
import com.guestexperience.shared.sync.PairedPhone
import com.guestexperience.shared.sync.TvSyncStore
import com.guestexperience.shared.validation.ContentProblem
import com.guestexperience.shared.validation.ContentValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val attemptsBeforeLock: Int) : PinCheck
    data class Locked(val remainingMs: Long) : PinCheck
    data object NoPinSet : PinCheck
}

/**
 * Everything the TV keeps on disk, in one Preferences DataStore:
 *  - the content JSON, with the Wi-Fi password removed,
 *  - the Wi-Fi password, encrypted with the Keystore key,
 *  - the PIN hash and lockout state,
 *  - paired phones, with their keys encrypted.
 */
class TvRepository(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
    override val tvId: String,
    private val deviceName: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lockoutPolicy: PinLockoutPolicy = PinLockoutPolicy(),
) : TvSyncStore {

    private object Keys {
        val CONTENT = stringPreferencesKey("content_json")
        val WIFI_PASSWORD = stringPreferencesKey("wifi_password_enc")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val PIN = stringPreferencesKey("pin_hash")
        val LOCKOUT = stringPreferencesKey("pin_lockout")
        val PHONES = stringPreferencesKey("paired_phones")
    }

    private val json = Json(from = ContentJson.json) {}
    private val phonesSerializer = ListSerializer(PairedPhone.serializer())

    override val tvName: String get() = deviceName()

    val content: Flow<PropertyContent> = store.data.map(::readContent).distinctUntilChanged()
    val onboarded: Flow<Boolean> = store.data.map { it[Keys.ONBOARDED] == true }.distinctUntilChanged()
    val pairedPhones: Flow<List<PairedPhone>> = store.data.map(::readPhones).distinctUntilChanged()
    val lockout: Flow<LockoutState> = store.data.map(::readLockout).distinctUntilChanged()

    private fun readContent(p: Preferences): PropertyContent {
        val base = p[Keys.CONTENT]?.let { runCatching { ContentJson.decode(it) }.getOrNull() } ?: PropertyContent()
        val password = p[Keys.WIFI_PASSWORD]?.let(cipher::decrypt).orEmpty()
        return base.copy(wifi = base.wifi.copy(password = password))
    }

    private fun readPhones(p: Preferences): List<PairedPhone> =
        p[Keys.PHONES]?.let { runCatching { json.decodeFromString(phonesSerializer, it) }.getOrNull() }
            .orEmpty()
            .mapNotNull { phone -> cipher.decrypt(phone.keyB64)?.let { phone.copy(keyB64 = it) } }

    private fun readLockout(p: Preferences): LockoutState =
        p[Keys.LOCKOUT]?.let { runCatching { json.decodeFromString(LockoutState.serializer(), it) }.getOrNull() }
            ?: LockoutState()

    private fun writeContent(prefs: MutablePreferences, c: PropertyContent) {
        prefs[Keys.CONTENT] = ContentJson.encode(c.copy(wifi = c.wifi.copy(password = "")))
        prefs[Keys.WIFI_PASSWORD] = cipher.encrypt(c.wifi.password)
    }

    suspend fun saveContent(c: PropertyContent) {
        val stamped = c.copy(updatedAt = clock())
        store.edit { writeContent(it, stamped) }
    }

    /** Finishes first-run setup in one write, so a half-finished setup is never treated as done. */
    suspend fun completeOnboarding(c: PropertyContent, pin: String) {
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        val stamped = c.copy(updatedAt = clock())
        store.edit {
            writeContent(it, stamped)
            it[Keys.PIN] = json.encodeToString(PinHash.serializer(), hash)
            it[Keys.LOCKOUT] = json.encodeToString(LockoutState.serializer(), LockoutState())
            it[Keys.ONBOARDED] = true
        }
    }

    suspend fun changePin(pin: String) {
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        store.edit { it[Keys.PIN] = json.encodeToString(PinHash.serializer(), hash) }
    }

    suspend fun checkPin(pin: String): PinCheck {
        val prefs = store.data.first()
        val stored = prefs[Keys.PIN]?.let { json.decodeFromString(PinHash.serializer(), it) } ?: return PinCheck.NoPinSet
        val now = clock()
        val state = readLockout(prefs)
        if (lockoutPolicy.isLocked(state, now)) return PinCheck.Locked(lockoutPolicy.remainingLockMs(state, now))

        val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, stored) }
        val next = if (ok) lockoutPolicy.onSuccess() else lockoutPolicy.onFailure(state, now)
        store.edit { it[Keys.LOCKOUT] = json.encodeToString(LockoutState.serializer(), next) }
        return when {
            ok -> PinCheck.Ok
            lockoutPolicy.isLocked(next, now) -> PinCheck.Locked(lockoutPolicy.remainingLockMs(next, now))
            else -> PinCheck.Wrong(lockoutPolicy.attemptsBeforeLock(next))
        }
    }

    fun remainingLockMs(state: LockoutState): Long = lockoutPolicy.remainingLockMs(state, clock())

    suspend fun removePhone(phoneId: String) = editPhones { list -> list.filterNot { it.phoneId == phoneId } }

    private suspend fun editPhones(change: (List<PairedPhone>) -> List<PairedPhone>) {
        store.edit { prefs ->
            val updated = change(readPhones(prefs))
            val encrypted = updated.map { it.copy(keyB64 = cipher.encrypt(it.keyB64)) }
            prefs[Keys.PHONES] = json.encodeToString(phonesSerializer, encrypted)
        }
    }

    // --- TvSyncStore (called by the local sync server) ---

    override suspend fun findPhone(phoneId: String): PairedPhone? = pairedPhones.first().firstOrNull { it.phoneId == phoneId }

    override suspend fun savePhone(phone: PairedPhone) = editPhones { list -> list.filterNot { it.phoneId == phone.phoneId } + phone }

    override suspend fun currentContent(): PropertyContent = content.first()

    override suspend fun applyContent(content: PropertyContent): ContentProblem? {
        ContentValidator.validate(content)?.let { return it }
        // The phone never sees or sends the photo; keep the TV's own choice of photo.
        val current = currentContent()
        val merged = content.copy(
            background = content.background.copy(useCustomPhoto = current.background.useCustomPhoto && content.background.useCustomPhoto),
            updatedAt = content.updatedAt,
        )
        store.edit { writeContent(it, merged) }
        return null
    }
}
