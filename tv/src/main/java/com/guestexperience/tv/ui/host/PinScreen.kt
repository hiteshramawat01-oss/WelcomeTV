package com.guestexperience.tv.ui.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.guestexperience.shared.validation.Validators
import com.guestexperience.tv.data.PinCheck
import com.guestexperience.tv.data.TvRepository
import com.guestexperience.tv.ui.components.PinDots
import com.guestexperience.tv.ui.components.PinHeader
import com.guestexperience.tv.ui.components.PinPad
import com.guestexperience.tv.ui.theme.Gtv
import com.guestexperience.tv.ui.theme.GtvType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PinUiState(
    val entered: String = "",
    val message: String? = null,
    val lockedSeconds: Int = 0,
    val checking: Boolean = false,
    val unlocked: Boolean = false,
)

class PinViewModel(private val repository: TvRepository) : ViewModel() {
    private val _state = MutableStateFlow(PinUiState())
    val state: StateFlow<PinUiState> = _state.asStateFlow()
    private var countdown: Job? = null

    init {
        viewModelScope.launch {
            val remaining = repository.remainingLockMs(repository.lockout.first())
            if (remaining > 0) startCountdown(remaining)
        }
    }

    fun digit(c: Char) = _state.update {
        if (it.lockedSeconds > 0 || it.checking || it.entered.length >= Validators.PIN_MAX) it
        else it.copy(entered = it.entered + c, message = null)
    }

    fun delete() = _state.update { it.copy(entered = it.entered.dropLast(1)) }

    fun submit() {
        val s = _state.value
        if (s.checking || s.lockedSeconds > 0) return
        if (Validators.pin(s.entered) != null) {
            _state.update { it.copy(message = "Enter your 4–6 digit PIN") }
            return
        }
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            when (val result = repository.checkPin(s.entered)) {
                PinCheck.Ok, PinCheck.NoPinSet -> _state.update { it.copy(checking = false, unlocked = true) }
                is PinCheck.Wrong -> _state.update {
                    it.copy(
                        entered = "",
                        checking = false,
                        message = "Wrong PIN. ${result.attemptsBeforeLock} " +
                            (if (result.attemptsBeforeLock == 1) "attempt" else "attempts") + " left before a short wait.",
                    )
                }
                is PinCheck.Locked -> {
                    _state.update { it.copy(entered = "", checking = false) }
                    startCountdown(result.remainingMs)
                }
            }
        }
    }

    private fun startCountdown(ms: Long) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var seconds = ((ms + 999) / 1000).toInt()
            while (seconds > 0) {
                _state.update { it.copy(lockedSeconds = seconds, message = "Too many wrong PINs. Try again in ${formatWait(seconds)}.") }
                delay(1000)
                seconds--
            }
            _state.update { it.copy(lockedSeconds = 0, message = null) }
        }
    }

    private fun formatWait(seconds: Int) = if (seconds >= 60) "${seconds / 60} min ${seconds % 60} s" else "$seconds s"
}

@Composable
fun PinScreen(viewModel: PinViewModel, onUnlocked: () -> Unit, onCancel: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.unlocked) { if (state.unlocked) onUnlocked() }
    BackHandler(onBack = onCancel)

    Row(
        Modifier
            .fillMaxSize()
            .background(Gtv.Ink)
            .padding(horizontal = 56.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PinHeader("Host Mode", "Enter your PIN", "Use the number buttons on your remote, or pick digits with the arrows and press OK. Press Back to return.")
            PinDots(filled = state.entered.length, total = maxOf(4, state.entered.length))
            state.message?.let { Text(it, style = GtvType.Body.copy(color = Gtv.Warn)) }
        }
        PinPad(
            onDigit = viewModel::digit,
            onDelete = viewModel::delete,
            onSubmit = viewModel::submit,
            enabled = state.lockedSeconds == 0 && !state.checking,
        )
    }
}
