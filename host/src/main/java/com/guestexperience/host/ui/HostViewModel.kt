package com.guestexperience.host.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestexperience.host.HostContainer
import com.guestexperience.host.data.FoundTv
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.sync.PairOutcome
import com.guestexperience.shared.sync.PairedTv
import com.guestexperience.shared.sync.SyncError
import com.guestexperience.shared.sync.SyncResult
import com.guestexperience.shared.sync.SamsungChannelDialer
import com.guestexperience.shared.sync.TvKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.guestexperience.shared.validation.ContentValidator
import com.guestexperience.shared.validation.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HostUiState(
    val loaded: Boolean = false,
    val pairedTv: PairedTv? = null,
    val found: List<FoundTv> = emptyList(),
    val searching: Boolean = false,
    val draft: PropertyContent? = null,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val showPairing: Boolean = false,
) {
    val connectedTv: FoundTv? get() = pairedTv?.let { tv -> found.firstOrNull { it.tvId == tv.tvId } }

    /** The Samsung Guest Welcome app shows only the guest name and the welcome message. */
    val welcomeOnly: Boolean get() = pairedTv?.kind == TvKind.SAMSUNG
}

fun SyncError.friendly(field: String? = null): String = when (this) {
    SyncError.UNREACHABLE -> "Can't reach the TV. Make sure it is on, the app is installed on it, and your phone is on the same Wi-Fi."
    SyncError.NO_PAIRING_CODE -> "On the TV, open Pair a phone to show a code."
    SyncError.WRONG_CODE -> "That code isn't right. Check the 6 digits on the TV."
    SyncError.TOO_MANY_ATTEMPTS -> "Too many wrong codes. On the TV, choose \"Show a new code\"."
    SyncError.UNKNOWN_PHONE, SyncError.DECRYPT_FAILED -> "This TV no longer recognises this phone. Pair again."
    SyncError.REPLAY -> "Please try again."
    SyncError.INVALID_CONTENT -> "The TV didn't accept ${field ?: "a field"}. Check it and send again."
    SyncError.BAD_PROTOCOL -> "Please update both apps to the latest version."
    SyncError.INTERNAL -> "Something went wrong. Please try again."
}

class HostViewModel(private val c: HostContainer) : ViewModel() {
    private val _state = MutableStateFlow(HostUiState())
    val state: StateFlow<HostUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            c.store.pairedTv.collect { tv ->
                // A Samsung TV is also reachable at its remembered address, even if DNS-SD never finds it.
                val rememberedHost = tv?.host
                if (tv?.kind == TvKind.SAMSUNG && rememberedHost != null) {
                    c.discovery.addManual(FoundTv(tv.name, tv.tvId, rememberedHost, SamsungChannelDialer.DEFAULT_PORT, TvKind.SAMSUNG))
                }
                _state.update {
                    it.copy(
                        loaded = true,
                        pairedTv = tv,
                        // No TV yet: pairing. Same TV as before: keep whatever screen the user chose.
                        showPairing = tv == null || (it.showPairing && it.pairedTv?.tvId == tv.tvId),
                    )
                }
                autoFetch()
            }
        }
        viewModelScope.launch {
            c.discovery.tvs.collect { list ->
                _state.update { it.copy(found = list) }
                autoFetch()
            }
        }
        viewModelScope.launch { c.discovery.searching.collect { s -> _state.update { it.copy(searching = s) } } }
    }

    fun onForeground() = c.discovery.start()
    fun onBackground() = c.discovery.stop()
    fun refresh() = c.discovery.refresh()

    private fun autoFetch() {
        val s = _state.value
        if (s.draft == null && !s.busy && s.connectedTv != null) fetch()
    }

    fun fetch() {
        val s = _state.value
        val tv = s.pairedTv ?: return
        val target = s.connectedTv ?: return message("Looking for your TV on this Wi-Fi…", false)
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = c.client.fetch(c.dialer(target), tv)) {
                is SyncResult.Ok -> {
                    c.store.save(r.tv)
                    _state.update { it.copy(busy = false, draft = r.value, dirty = false, message = null) }
                }
                is SyncResult.Failed -> {
                    _state.update { it.copy(busy = false) }
                    message(r.error.friendly(), true)
                }
            }
        }
    }

    fun edit(change: (PropertyContent) -> PropertyContent) =
        _state.update { s -> s.draft?.let { s.copy(draft = change(it), dirty = true, message = null) } ?: s }

    fun nextGuest() = edit { it.copy(guestName = "", hostMessage = "", checkOut = null) }

    fun send() {
        val s = _state.value
        val tv = s.pairedTv ?: return
        val draft = s.draft ?: return
        val cleaned = draft.copy(
            houseNotes = draft.houseNotes.map(String::trim).filter(String::isNotEmpty),
            places = draft.places.filter { it.name.isNotBlank() },
        )
        val problem = if (s.welcomeOnly) ContentValidator.validateWelcomeOnly(cleaned) else ContentValidator.validate(cleaned)
        problem?.let { p -> return message("Check ${fieldLabel(p.field)}.", true) }
        val target = s.connectedTv ?: return message(SyncError.UNREACHABLE.friendly(), true)
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = c.client.push(c.dialer(target), tv, cleaned)) {
                is SyncResult.Ok -> {
                    c.store.save(r.tv)
                    _state.update { it.copy(busy = false, dirty = false, draft = cleaned.copy(updatedAt = r.value), message = "Sent — the TV is updated.", messageIsError = false) }
                }
                is SyncResult.Failed -> {
                    _state.update { it.copy(busy = false) }
                    message(r.error.friendly(r.field?.let(::fieldLabel)), true)
                }
            }
        }
    }

    fun showPairing(show: Boolean) = _state.update { it.copy(showPairing = show, message = null) }

    /** Adds a Samsung TV by its IP address, for Wi-Fi networks where it doesn't show up by itself. */
    fun addByAddress(input: String) {
        val ip = input.trim()
        if (!Regex("""^(\d{1,3})(\.\d{1,3}){3}$""").matches(ip) || ip.split('.').any { it.toInt() > 255 }) {
            return message("Enter the TV's IP address, like 192.168.1.20.", true)
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { SamsungChannelDialer.lookup(ip) }
            _state.update { it.copy(busy = false) }
            if (info == null) {
                message("No Samsung TV answered at $ip. Check the address on the TV and that both are on the same Wi-Fi.", true)
            } else {
                c.discovery.addManual(FoundTv(info.name, info.id, ip, SamsungChannelDialer.DEFAULT_PORT, TvKind.SAMSUNG))
                message("Found ${info.name}. Now enter the code shown on the TV.", false)
            }
        }
    }

    fun pair(target: FoundTv, code: String) {
        if (Validators.pairingCode(code) != null) return message("Enter the 6-digit code shown on the TV.", true)
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = c.client.pair(c.dialer(target), code)) {
                is PairOutcome.Paired -> {
                    val tv = r.tv.copy(kind = target.kind, host = target.host)
                    c.store.save(tv)
                    _state.update { it.copy(busy = false, showPairing = false, draft = null, message = "Connected to ${tv.name}.", messageIsError = false) }
                    autoFetch()
                }
                is PairOutcome.Failed -> {
                    _state.update { it.copy(busy = false) }
                    message(r.error.friendly(), true)
                }
            }
        }
    }

    fun forget() {
        viewModelScope.launch {
            c.store.forget()
            _state.update { it.copy(draft = null, dirty = false, showPairing = true) }
        }
    }

    private fun message(text: String, error: Boolean) = _state.update { it.copy(message = text, messageIsError = error) }

    private fun fieldLabel(field: String): String = when (field) {
        "propertyName" -> "the property name"
        "guestName" -> "the guest name"
        "hostMessage" -> "the welcome message"
        "wifi.ssid" -> "the Wi-Fi network name"
        "wifi.password" -> "the Wi-Fi password"
        "checkOut.date", "checkOut.time" -> "the check-out time"
        "houseNotes" -> "the house notes"
        "places" -> "the nearby places"
        else -> "the host contact"
    }
}
