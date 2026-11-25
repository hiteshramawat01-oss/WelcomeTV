package com.guestexperience.tv.ui.host

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.sync.ActivePairingCode
import com.guestexperience.shared.sync.PairedPhone
import com.guestexperience.shared.sync.PairingCodeManager
import com.guestexperience.shared.validation.ContentValidator
import com.guestexperience.shared.validation.FieldError
import com.guestexperience.shared.validation.Validators
import com.guestexperience.tv.data.ImageStore
import com.guestexperience.tv.data.TvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun FieldError.message(): String = when (this) {
    FieldError.REQUIRED -> "Required"
    FieldError.TOO_SHORT -> "Too short"
    FieldError.TOO_LONG -> "Too long"
    FieldError.DIGITS_ONLY -> "Digits only"
    FieldError.INVALID_FORMAT -> "Check the format"
    FieldError.MISMATCH -> "Doesn't match"
}

/** Friendly message for a whole-content validation failure. */
fun fieldName(field: String): String = when (field) {
    "propertyName" -> "Property name"
    "location" -> "Location"
    "guestName" -> "Guest name"
    "hostMessage" -> "Welcome message"
    "wifi.ssid" -> "Wi-Fi network name"
    "wifi.password" -> "Wi-Fi password"
    "checkOut.date", "checkOut.time" -> "Check-out"
    "houseNotes" -> "House notes"
    "places" -> "Nearby places"
    else -> "Host contact"
}

class HostViewModel(
    private val repository: TvRepository,
    private val imageStore: ImageStore,
    private val pairingCodes: PairingCodeManager,
    private val onPhotoChanged: () -> Unit,
) : ViewModel() {
    private val _draft = MutableStateFlow<PropertyContent?>(null)
    val draft: StateFlow<PropertyContent?> = _draft.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _errorField = MutableStateFlow<String?>(null)
    val errorField: StateFlow<String?> = _errorField.asStateFlow()

    val pairedPhones: StateFlow<List<PairedPhone>> =
        repository.pairedPhones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pairingCode: StateFlow<ActivePairingCode?> = pairingCodes.active

    val hasPhoto: Boolean get() = imageStore.hasPhoto()

    init {
        viewModelScope.launch { _draft.value = repository.content.first() }
        // Phone pushes arrive while Host Mode is open; refresh the draft if nothing is being edited.
        viewModelScope.launch {
            repository.content.collect { latest ->
                if (_status.value != EDITING) _draft.value = latest
            }
        }
    }

    fun edit(change: (PropertyContent) -> PropertyContent) {
        _draft.update { it?.let(change) }
        _status.value = EDITING
        _errorField.value = null
    }

    fun save(onSaved: () -> Unit = {}) {
        val content = _draft.value ?: return
        val cleaned = content.copy(
            houseNotes = content.houseNotes.map(String::trim).filter(String::isNotEmpty),
            places = content.places.filter { it.name.isNotBlank() },
        )
        ContentValidator.validate(cleaned)?.let { problem ->
            _errorField.value = problem.field
            _status.value = "${fieldName(problem.field)}: ${problem.error.message()}"
            return
        }
        viewModelScope.launch {
            repository.saveContent(cleaned)
            _status.value = "Saved — the guest screen is updated"
            onSaved()
        }
    }

    fun clearForNextGuest() {
        edit { it.copy(guestName = "", hostMessage = "", checkOut = null) }
        save()
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            _status.value = "Preparing photo…"
            if (imageStore.importPhoto(uri)) {
                edit { it.copy(background = it.background.copy(useCustomPhoto = true)) }
                onPhotoChanged()
                save()
            } else {
                _status.value = "That photo couldn't be opened"
            }
        }
    }

    fun removePhoto() {
        imageStore.deletePhoto()
        onPhotoChanged()
        edit { it.copy(background = it.background.copy(useCustomPhoto = false)) }
        save()
    }

    fun changePin(pin: String, confirm: String): String? {
        Validators.pin(pin)?.let { return "New PIN: ${it.message()}" }
        Validators.pinConfirmation(pin, confirm)?.let { return "Confirmation: ${it.message()}" }
        viewModelScope.launch {
            repository.changePin(pin)
            _status.value = "PIN changed"
        }
        return null
    }

    fun startPairing() = pairingCodes.start()
    fun stopPairing() = pairingCodes.stop()
    fun removePhone(id: String) = viewModelScope.launch { repository.removePhone(id) }

    override fun onCleared() {
        pairingCodes.stop()
    }

    private companion object {
        const val EDITING = "Unsaved changes"
    }
}
