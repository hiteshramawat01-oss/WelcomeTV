package com.guestexperience.tv.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.model.WifiInfo
import com.guestexperience.shared.model.WifiSecurity
import com.guestexperience.shared.validation.Validators
import com.guestexperience.tv.data.TvRepository
import com.guestexperience.tv.ui.components.ChoiceChip
import com.guestexperience.tv.ui.components.FieldLabel
import com.guestexperience.tv.ui.components.GtvButton
import com.guestexperience.tv.ui.components.PinDots
import com.guestexperience.tv.ui.components.PinPad
import com.guestexperience.tv.ui.components.TvTextField
import com.guestexperience.tv.ui.guest.GuestScreen
import com.guestexperience.tv.ui.host.message
import com.guestexperience.tv.ui.theme.Gtv
import com.guestexperience.tv.ui.theme.GtvType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, PIN, PIN_CONFIRM, PROPERTY, WIFI, PREVIEW }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val pin: String = "",
    val pinConfirm: String = "",
    val content: PropertyContent = PropertyContent(location = "Udaipur", wifi = WifiInfo()),
    val error: String? = null,
    val finishing: Boolean = false,
)

class OnboardingViewModel(private val repository: TvRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun start() = go(OnboardingStep.PIN)

    fun pinDigit(c: Char) = _state.update {
        when (it.step) {
            OnboardingStep.PIN -> if (it.pin.length < Validators.PIN_MAX) it.copy(pin = it.pin + c, error = null) else it
            OnboardingStep.PIN_CONFIRM -> if (it.pinConfirm.length < Validators.PIN_MAX) it.copy(pinConfirm = it.pinConfirm + c, error = null) else it
            else -> it
        }
    }

    fun pinDelete() = _state.update {
        when (it.step) {
            OnboardingStep.PIN -> it.copy(pin = it.pin.dropLast(1))
            OnboardingStep.PIN_CONFIRM -> it.copy(pinConfirm = it.pinConfirm.dropLast(1))
            else -> it
        }
    }

    fun pinNext() {
        val s = _state.value
        if (s.step == OnboardingStep.PIN) {
            Validators.pin(s.pin)?.let { e -> _state.update { it.copy(error = "PIN: ${e.message()} — use 4 to 6 digits") }; return }
            go(OnboardingStep.PIN_CONFIRM)
        } else {
            if (Validators.pinConfirmation(s.pin, s.pinConfirm) != null) {
                _state.update { it.copy(pinConfirm = "", error = "The PINs don't match. Try again.") }
                return
            }
            go(OnboardingStep.PROPERTY)
        }
    }

    fun edit(change: (PropertyContent) -> PropertyContent) = _state.update { it.copy(content = change(it.content), error = null) }

    fun propertyNext() {
        Validators.requiredText(_state.value.content.propertyName, Validators.PROPERTY_MAX)?.let { e ->
            _state.update { it.copy(error = "Property name: ${e.message()}") }; return
        }
        go(OnboardingStep.WIFI)
    }

    fun wifiNext() {
        val w = _state.value.content.wifi
        (Validators.ssid(w.ssid)?.let { "Network name: ${it.message()}" }
            ?: Validators.wifiPassword(w.password, w.security)?.let { "Password: ${it.message()}" })
            ?.let { msg -> _state.update { it.copy(error = msg) }; return }
        go(OnboardingStep.PREVIEW)
    }

    fun finish() {
        val s = _state.value
        if (s.finishing) return
        _state.update { it.copy(finishing = true) }
        viewModelScope.launch { repository.completeOnboarding(s.content, s.pin) }
    }

    fun back(): Boolean {
        val prev = when (_state.value.step) {
            OnboardingStep.WELCOME -> return false
            OnboardingStep.PIN -> OnboardingStep.WELCOME
            OnboardingStep.PIN_CONFIRM -> OnboardingStep.PIN
            OnboardingStep.PROPERTY -> OnboardingStep.PIN
            OnboardingStep.WIFI -> OnboardingStep.PROPERTY
            OnboardingStep.PREVIEW -> OnboardingStep.WIFI
        }
        _state.update {
            it.copy(
                step = prev,
                error = null,
                pin = if (prev == OnboardingStep.PIN) "" else it.pin,
                pinConfirm = "",
            )
        }
        return true
    }

    private fun go(step: OnboardingStep) = _state.update { it.copy(step = step, error = null) }
}

/** First run: PIN → property → Wi-Fi → preview. Designed to take under two minutes with a remote. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onExitApp: () -> Unit) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { if (!viewModel.back()) onExitApp() }

    val stepNumber = when (s.step) {
        OnboardingStep.WELCOME -> 0
        OnboardingStep.PIN, OnboardingStep.PIN_CONFIRM -> 1
        OnboardingStep.PROPERTY -> 2
        OnboardingStep.WIFI -> 3
        OnboardingStep.PREVIEW -> 4
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Gtv.Ink)
            .padding(horizontal = 56.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (stepNumber > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SET UP · STEP $stepNumber OF 4", style = GtvType.Overline, modifier = Modifier.padding(end = 10.dp))
                repeat(4) { i ->
                    Box(Modifier.width(36.dp).height(3.dp).background(if (i < stepNumber) Gtv.Gold else Gtv.LineStrong, RoundedCornerShape(2.dp)))
                }
            }
        }
        when (s.step) {
            OnboardingStep.WELCOME -> Welcome(viewModel::start)
            OnboardingStep.PIN, OnboardingStep.PIN_CONFIRM -> PinStep(s, viewModel)
            OnboardingStep.PROPERTY -> TwoColumn("Your property", "This is what guests see under their name.") {
                TvTextField("Property name", s.content.propertyName, { v -> viewModel.edit { it.copy(propertyName = v) } },
                    placeholder = "e.g. Pichola Haveli", maxLength = Validators.PROPERTY_MAX)
                TvTextField("Area or city (optional)", s.content.location, { v -> viewModel.edit { it.copy(location = v) } },
                    maxLength = Validators.PROPERTY_MAX)
                Error(s.error)
                NextRow(viewModel::propertyNext, "Next: Wi-Fi")
            }
            OnboardingStep.WIFI -> TwoColumn("Your guest Wi-Fi", "Guests scan the code on screen to join. The password is encrypted and never leaves this TV.") {
                val w = s.content.wifi
                TvTextField("Network name", w.ssid, { v -> viewModel.edit { it.copy(wifi = it.wifi.copy(ssid = v)) } }, maxLength = 32)
                if (w.security != WifiSecurity.OPEN) {
                    TvTextField("Password", w.password, { v -> viewModel.edit { it.copy(wifi = it.wifi.copy(password = v)) } },
                        keyboardType = KeyboardType.Password, maxLength = 64)
                }
                FieldLabel("Security")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(WifiSecurity.WPA to "WPA / WPA2 / WPA3", WifiSecurity.WEP to "WEP", WifiSecurity.OPEN to "Open").forEach { (sec, label) ->
                        ChoiceChip(label, w.security == sec, { viewModel.edit { it.copy(wifi = it.wifi.copy(security = sec)) } })
                    }
                    ChoiceChip("Hidden", w.hidden, { viewModel.edit { it.copy(wifi = it.wifi.copy(hidden = !it.wifi.hidden)) } })
                }
                Error(s.error)
                NextRow(viewModel::wifiNext, "Next: preview")
            }
            OnboardingStep.PREVIEW -> Preview(s, viewModel)
        }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit) {
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
        Text("WELCOME", style = GtvType.Overline)
        Text("Let's set up your welcome screen", style = GtvType.Greeting)
        Text("Four short steps: a PIN for Host Mode, your property name, your guest Wi-Fi, and a preview.", style = GtvType.Body)
        GtvButton("Start", onStart, Modifier.focusRequester(focus), primary = true)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun PinStep(s: OnboardingState, vm: OnboardingViewModel) {
    val confirming = s.step == OnboardingStep.PIN_CONFIRM
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (confirming) "Repeat your PIN" else "Create a Host PIN", style = GtvType.Title)
            Text("4 to 6 digits. You'll need it to open Host Mode: hold OK for 3 seconds on the welcome screen, or choose the ⚙ tile.", style = GtvType.Body)
            val entered = if (confirming) s.pinConfirm else s.pin
            PinDots(entered.length, maxOf(4, entered.length))
            Error(s.error)
        }
        // key() forces fresh focus on the keypad when switching between create and confirm.
        androidx.compose.runtime.key(confirming) {
            PinPad(vm::pinDigit, vm::pinDelete, vm::pinNext, submitLabel = "Next")
        }
    }
}

@Composable
private fun TwoColumn(title: String, hint: String, fields: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = GtvType.Title.copy(fontSize = GtvType.Title.fontSize * 1.1f))
            Text(hint, style = GtvType.Body)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { fields() }
    }
}

@Composable
private fun NextRow(onNext: () -> Unit, label: String) {
    val focus = remember { FocusRequester() }
    Spacer(Modifier.height(4.dp))
    GtvButton(label, onNext, Modifier.focusRequester(focus), primary = true)
}

@Composable
private fun Error(text: String?) {
    if (text != null) Text(text, style = GtvType.Body.copy(color = Gtv.Warn))
}

@Composable
private fun Preview(s: OnboardingState, vm: OnboardingViewModel) {
    val focus = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
        // The real guest screen at half size, not focusable.
        Box(Modifier.size(480.dp, 270.dp).clip(RoundedCornerShape(12.dp))) {
            Box(
                Modifier
                    .requiredSize(960.dp, 540.dp)
                    .graphicsLayer { scaleX = 0.5f; scaleY = 0.5f },
            ) {
                GuestScreen(content = s.content, photo = null, onOpenHost = {}, interactive = false)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("This is your welcome screen", style = GtvType.Title)
            Text("Add a guest name, check-out time, house notes and nearby places later in Host Mode — or from the Host app on your phone.", style = GtvType.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GtvButton("Back", { vm.back() })
                GtvButton(if (s.finishing) "Saving…" else "Finish", vm::finish, Modifier.focusRequester(focus), primary = true, enabled = !s.finishing)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
