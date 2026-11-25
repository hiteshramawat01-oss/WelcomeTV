package com.guestexperience.shared.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

data class ActivePairingCode(val code: String, val expiresAtMs: Long)

/** Holds the one 6-digit code the TV is currently showing, with expiry and an attempt limit. Thread-safe. */
class PairingCodeManager(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private val _active = MutableStateFlow<ActivePairingCode?>(null)
    val active: StateFlow<ActivePairingCode?> = _active.asStateFlow()
    private var failures = 0

    @Synchronized
    fun start(): ActivePairingCode {
        failures = 0
        val code = (0 until 6).joinToString("") { random.nextInt(10).toString() }
        return ActivePairingCode(code, clock() + SyncProtocol.PAIRING_CODE_TTL_MS).also { _active.value = it }
    }

    @Synchronized
    fun stop() {
        _active.value = null
    }

    /** The code a phone must match right now, or null if pairing is not open or the code has expired. */
    @Synchronized
    fun current(): String? {
        val active = _active.value ?: return null
        return if (clock() >= active.expiresAtMs) null else active.code
    }

    /** Records a wrong code. Returns false once the code has been withdrawn after too many attempts. */
    @Synchronized
    fun registerFailure(): Boolean {
        failures++
        if (failures >= SyncProtocol.MAX_CODE_ATTEMPTS) {
            _active.value = null
            return false
        }
        return true
    }

    /** A code works once: after a successful pairing it is withdrawn. */
    @Synchronized
    fun consume() {
        _active.value = null
    }
}
