package com.guestexperience.tv.ui.host

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.guestexperience.shared.model.BuiltInScene
import com.guestexperience.shared.model.CheckOut
import com.guestexperience.shared.model.HostContact
import com.guestexperience.shared.model.Place
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.model.TimeFormat
import com.guestexperience.shared.model.WifiSecurity
import com.guestexperience.shared.validation.ContentValidator
import com.guestexperience.shared.validation.Validators
import com.guestexperience.tv.ui.components.ChoiceChip
import com.guestexperience.tv.ui.components.FieldLabel
import com.guestexperience.tv.ui.components.GtvButton
import com.guestexperience.tv.ui.components.Stepper
import com.guestexperience.tv.ui.components.TimeText
import com.guestexperience.tv.ui.components.TvTextField
import com.guestexperience.tv.ui.components.focusBorder
import com.guestexperience.tv.ui.theme.Gtv
import com.guestexperience.shared.update.AppUpdates
import com.guestexperience.shared.update.UpdateState
import com.guestexperience.tv.appContainer
import com.guestexperience.tv.ui.theme.GtvType
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

enum class HostSection(val title: String) {
    STAY("This stay"),
    PROPERTY("Property & welcome"),
    WIFI("Wi-Fi"),
    NOTES("House notes"),
    PLACES("Nearby places"),
    CONTACT("Host contact"),
    DISPLAY("Background & clock"),
    PAIR("Pair a phone"),
    PIN("Change PIN"),
    UPDATE("App update"),
}

@Composable
fun HostScreen(viewModel: HostViewModel, onExit: () -> Unit) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val errorField by viewModel.errorField.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(HostSection.STAY) }
    // Only builds installed from GitHub Releases can update themselves (Play updates the others).
    val updates = LocalContext.current.appContainer.updates
    val sections = HostSection.entries.filter { it != HostSection.UPDATE || updates != null }
    val firstNav = remember { FocusRequester() }
    BackHandler(onBack = onExit)

    Row(
        Modifier
            .fillMaxSize()
            .background(Gtv.Ink)
            .padding(horizontal = 48.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("HOST MODE", style = GtvType.Overline, modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 14.dp))
            sections.forEachIndexed { i, s ->
                NavItem(s.title, s == section, { section = s }, if (i == 0) Modifier.focusRequester(firstNav) else Modifier)
            }
            Spacer(Modifier.height(10.dp))
            NavItem("← Exit to guest view", selected = false, onClick = onExit)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(section.title, style = GtvType.Title, modifier = Modifier.padding(top = 30.dp))
            val d = draft
            if (d == null) {
                Text("Loading…", style = GtvType.Body)
            } else {
                when (section) {
                    HostSection.STAY -> StaySection(d, errorField, viewModel)
                    HostSection.PROPERTY -> PropertySection(d, errorField, viewModel)
                    HostSection.WIFI -> WifiSection(d, errorField, viewModel)
                    HostSection.NOTES -> NotesSection(d, viewModel)
                    HostSection.PLACES -> PlacesSection(d, viewModel)
                    HostSection.CONTACT -> ContactSection(d, viewModel)
                    HostSection.DISPLAY -> DisplaySection(d, viewModel)
                    HostSection.PAIR -> PairingPanel(viewModel)
                    HostSection.PIN -> ChangePinSection(viewModel)
                    HostSection.UPDATE -> updates?.let { UpdateSection(it) }
                }
                if (section != HostSection.PAIR && section != HostSection.PIN && section != HostSection.UPDATE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        GtvButton("Save", { viewModel.save() }, primary = true)
                        if (section == HostSection.STAY) GtvButton("Clear for next guest", viewModel::clearForNextGuest)
                    }
                }
            }
            status?.let { Text(it, style = GtvType.Body.copy(color = if (errorField != null) Gtv.Warn else Gtv.Muted)) }
            Spacer(Modifier.height(24.dp))
        }
    }
    LaunchedEffect(Unit) { runCatching { firstNav.requestFocus() } }
}

@Composable
private fun NavItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(36.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Gtv.Card else Color.Transparent,
            contentColor = if (selected) Gtv.Gold else Gtv.Soft,
            focusedContainerColor = Gtv.Paper,
            focusedContentColor = Gtv.Ink,
        ),
        border = focusBorder(shape, idle = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Text(text, style = GtvType.Button, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp))
    }
}

private fun err(errorField: String?, vararg fields: String): String? =
    if (errorField != null && errorField in fields) "Please check this" else null

@Composable
private fun StaySection(d: PropertyContent, errorField: String?, vm: HostViewModel) {
    TvTextField("Guest name", d.guestName, { v -> vm.edit { it.copy(guestName = v) } }, placeholder = "e.g. Ananya", maxLength = Validators.NAME_MAX, error = err(errorField, "guestName"))
    val co = d.checkOut
    if (co == null) {
        FieldLabel("Check-out")
        GtvButton("Set check-out", {
            vm.edit { it.copy(checkOut = CheckOut(LocalDate.now().plusDays(1).toString(), "11:00")) }
        })
    } else {
        val date = runCatching { LocalDate.parse(co.date) }.getOrElse { LocalDate.now() }
        val time = runCatching { LocalTime.parse(co.time) }.getOrElse { LocalTime.of(11, 0) }
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
            Stepper("Check-out date", TimeText.day(date),
                onMinus = { vm.edit { it.copy(checkOut = co.copy(date = date.minusDays(1).toString())) } },
                onPlus = { vm.edit { it.copy(checkOut = co.copy(date = date.plusDays(1).toString())) } })
            Stepper("Time", TimeText.clockString(time, d.timeFormat),
                onMinus = { vm.edit { it.copy(checkOut = co.copy(time = time.minusMinutes(30).toString())) } },
                onPlus = { vm.edit { it.copy(checkOut = co.copy(time = time.plusMinutes(30).toString())) } })
        }
        GtvButton("Don't show check-out", { vm.edit { it.copy(checkOut = null) } })
    }
    TvTextField("Message for this guest (optional)", d.hostMessage, { v -> vm.edit { it.copy(hostMessage = v) } },
        singleLine = false, maxLength = Validators.MESSAGE_MAX, placeholder = "Chai is on the house…", error = err(errorField, "hostMessage"))
}

@Composable
private fun PropertySection(d: PropertyContent, errorField: String?, vm: HostViewModel) {
    TvTextField("Property name", d.propertyName, { v -> vm.edit { it.copy(propertyName = v) } }, maxLength = Validators.PROPERTY_MAX, error = err(errorField, "propertyName"))
    TvTextField("Area or city (optional)", d.location, { v -> vm.edit { it.copy(location = v) } }, placeholder = "Udaipur", maxLength = Validators.PROPERTY_MAX)
}

@Composable
private fun WifiSection(d: PropertyContent, errorField: String?, vm: HostViewModel) {
    TvTextField("Network name", d.wifi.ssid, { v -> vm.edit { it.copy(wifi = it.wifi.copy(ssid = v)) } }, maxLength = 32, error = err(errorField, "wifi.ssid"))
    if (d.wifi.security != WifiSecurity.OPEN) {
        TvTextField("Password", d.wifi.password, { v -> vm.edit { it.copy(wifi = it.wifi.copy(password = v)) } },
            keyboardType = KeyboardType.Password, maxLength = 64, error = err(errorField, "wifi.password"))
    }
    FieldLabel("Security")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(WifiSecurity.WPA to "WPA / WPA2 / WPA3", WifiSecurity.WEP to "WEP", WifiSecurity.OPEN to "Open").forEach { (s, label) ->
            ChoiceChip(label, d.wifi.security == s, { vm.edit { it.copy(wifi = it.wifi.copy(security = s)) } })
        }
    }
    ChoiceChip("Hidden network", d.wifi.hidden, { vm.edit { it.copy(wifi = it.wifi.copy(hidden = !it.wifi.hidden)) } })
    Text("The password is stored encrypted on this TV only.", style = GtvType.Label)
}

@Composable
private fun NotesSection(d: PropertyContent, vm: HostViewModel) {
    val slots = (d.houseNotes + List(ContentValidator.MAX_NOTES) { "" }).take(5)
    slots.forEachIndexed { i, note ->
        TvTextField("Note ${i + 1}", note, { v ->
            vm.edit { c -> c.copy(houseNotes = slots.toMutableList().also { it[i] = v }) }
        }, placeholder = if (i == 0) "Rooftop sunset is best at 5:30" else "", maxLength = Validators.NOTE_MAX)
    }
}

@Composable
private fun PlacesSection(d: PropertyContent, vm: HostViewModel) {
    val slots = (d.places + List(4) { Place("") }).take(4)
    fun update(i: Int, p: Place) = vm.edit { c -> c.copy(places = slots.toMutableList().also { it[i] = p }) }
    slots.forEachIndexed { i, place ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvTextField("Place ${i + 1}", place.name, { update(i, place.copy(name = it)) }, Modifier.weight(1f),
                placeholder = if (i == 0) "Ambrai Ghat" else "", maxLength = Validators.NAME_MAX)
            TvTextField("Why go", place.description, { update(i, place.copy(description = it)) }, Modifier.weight(1.4f),
                placeholder = if (i == 0) "Sunset views across the lake" else "", maxLength = Validators.NOTE_MAX)
        }
    }
}

@Composable
private fun ContactSection(d: PropertyContent, vm: HostViewModel) {
    val c = d.hostContact ?: HostContact()
    TvTextField("Name", c.name, { v -> vm.edit { it.copy(hostContact = c.copy(name = v)) } }, maxLength = Validators.NAME_MAX)
    TvTextField("Phone or WhatsApp", c.phone, { v -> vm.edit { it.copy(hostContact = c.copy(phone = v)) } }, keyboardType = KeyboardType.Phone, maxLength = 24)
    TvTextField("Note (optional)", c.note, { v -> vm.edit { it.copy(hostContact = c.copy(note = v)) } }, placeholder = "Call any time before 10 pm", maxLength = Validators.NOTE_MAX)
}

@Composable
private fun DisplaySection(d: PropertyContent, vm: HostViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importPhoto) }
    val canPick = remember {
        Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            .resolveActivity(context.packageManager) != null
    }
    FieldLabel("Background")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(BuiltInScene.UDAIPUR_DUSK to "Udaipur dusk", BuiltInScene.UDAIPUR_NIGHT to "Udaipur night", BuiltInScene.PLAIN to "Plain").forEach { (scene, label) ->
            ChoiceChip(label, !d.background.useCustomPhoto && BuiltInScene.fromId(d.background.scene) == scene, {
                vm.edit { it.copy(background = it.background.copy(scene = scene.id, useCustomPhoto = false)) }
            })
        }
        if (vm.hasPhoto) {
            ChoiceChip("My photo", d.background.useCustomPhoto, { vm.edit { it.copy(background = it.background.copy(useCustomPhoto = true)) } })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canPick) GtvButton(if (vm.hasPhoto) "Choose a different photo" else "Use a photo from this TV", { picker.launch(arrayOf("image/*")) })
        if (vm.hasPhoto) GtvButton("Remove photo", vm::removePhoto)
    }
    if (!canPick) Text("This TV has no file picker. Photos can be added from a TV with a file manager installed.", style = GtvType.Label)
    FieldLabel("Clock")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceChip("12-hour", d.timeFormat == TimeFormat.H12, { vm.edit { it.copy(timeFormat = TimeFormat.H12) } })
        ChoiceChip("24-hour", d.timeFormat == TimeFormat.H24, { vm.edit { it.copy(timeFormat = TimeFormat.H24) } })
    }
}

@Composable
private fun ChangePinSection(vm: HostViewModel) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    TvTextField("New PIN (4–6 digits)", pin, { pin = it.filter(Char::isDigit).take(6) }, keyboardType = KeyboardType.NumberPassword)
    TvTextField("Repeat new PIN", confirm, { confirm = it.filter(Char::isDigit).take(6) }, keyboardType = KeyboardType.NumberPassword)
    error?.let { Text(it, style = GtvType.Body.copy(color = Gtv.Warn)) }
    GtvButton("Change PIN", {
        error = vm.changePin(pin, confirm)
        if (error == null) { pin = ""; confirm = "" }
    }, primary = true)
}

@Composable
private fun PairingPanel(vm: HostViewModel) {
    val code by vm.pairingCode.collectAsStateWithLifecycle()
    val phones by vm.pairedPhones.collectAsStateWithLifecycle()
    var secondsLeft by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        vm.startPairing()
        onDispose { vm.stopPairing() }
    }
    LaunchedEffect(code) {
        val active = code ?: return@LaunchedEffect
        while (true) {
            secondsLeft = ((active.expiresAtMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            if (secondsLeft == 0L) { vm.startPairing(); break }
            delay(1000)
        }
    }

    Text("Open the Guest Experience Host app on your phone and enter this code. Your phone must be on the same Wi-Fi as this TV.", style = GtvType.Body)
    val c = code
    if (c == null) {
        Text("Paired — this code has been used.", style = GtvType.BodyStrong.copy(color = Gtv.Ok))
        GtvButton("Show a new code", { vm.startPairing() })
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            c.code.forEachIndexed { i, ch ->
                if (i == 3) Spacer(Modifier.width(14.dp))
                Box(
                    Modifier
                        .width(56.dp)
                        .height(72.dp)
                        .background(Gtv.Card, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(ch.toString(), style = GtvType.Clock.copy(fontSize = GtvType.Clock.fontSize * 0.7f)) }
            }
        }
        Text("Code changes in ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}", style = GtvType.Label)
    }
    FieldLabel("Paired phones")
    if (phones.isEmpty()) Text("None yet", style = GtvType.Body)
    phones.forEach { p ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = GtvType.BodyStrong, modifier = Modifier.width(260.dp))
            GtvButton("Remove", { vm.removePhone(p.phoneId) })
        }
    }
}

@Composable
private fun UpdateSection(updates: AppUpdates) {
    val state by updates.state.collectAsStateWithLifecycle()
    Text("This TV app: version ${updates.installedVersionName}", style = GtvType.Body)
    val text = when (val st = state) {
        UpdateState.Idle -> "Updates come from this app's GitHub releases. You confirm each one on screen."
        UpdateState.Checking -> "Checking for updates…"
        UpdateState.UpToDate -> "Up to date."
        is UpdateState.Available -> "Version ${st.release.versionName} is available."
        is UpdateState.NeedsPermission -> "First allow this app to install apps: choose Allow installs, switch it on, then press Back and choose Update."
        is UpdateState.Downloading -> "Downloading… ${st.percent}%"
        is UpdateState.Installing -> "Confirm the update on the next screen."
        is UpdateState.Failed -> st.message
    }
    Text(text, style = GtvType.Body.copy(color = if (state is UpdateState.Failed) Gtv.Warn else Gtv.Muted))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        when (state) {
            is UpdateState.Available -> GtvButton("Update", updates::install, primary = true)
            is UpdateState.NeedsPermission -> {
                GtvButton("Allow installs", updates::openInstallPermission, primary = true)
                GtvButton("Update", updates::install)
            }
            is UpdateState.Failed -> GtvButton("Try again", { if ((state as UpdateState.Failed).release != null) updates.install() else updates.check() })
            else -> Unit
        }
        GtvButton("Check for updates", updates::check)
    }
}
