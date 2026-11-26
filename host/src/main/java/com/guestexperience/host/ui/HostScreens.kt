package com.guestexperience.host.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guestexperience.host.R
import com.guestexperience.host.data.FoundTv
import com.guestexperience.shared.model.BuiltInScene
import com.guestexperience.shared.model.CheckOut
import com.guestexperience.shared.model.HostContact
import com.guestexperience.shared.model.Place
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.model.TimeFormat
import com.guestexperience.shared.model.WifiSecurity
import com.guestexperience.shared.sync.TvKind
import com.guestexperience.shared.validation.Validators
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Tab(val label: String, val icon: Int) {
    STAY("Stay", R.drawable.ic_stay),
    WIFI("Wi-Fi", R.drawable.ic_wifi),
    PROPERTY("Property", R.drawable.ic_property),
    TV("TV", R.drawable.ic_tv),
}

@Composable
fun HostRoot(vm: HostViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    when {
        !s.loaded -> Box(Modifier.fillMaxSize().background(HostColors.Ink))
        s.showPairing || s.pairedTv == null -> PairScreen(s, vm)
        else -> MainScreen(s, vm)
    }
}

@Composable
private fun Header(overline: String, title: String) {
    Column(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(overline.uppercase(), style = Overline)
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun Message(s: HostUiState) {
    s.message?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = if (s.messageIsError) HostColors.Warn else HostColors.Ok)
    }
}

@Composable
private fun PairScreen(s: HostUiState, vm: HostViewModel) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    val selected: FoundTv? = s.found.firstOrNull { it.tvId == selectedId } ?: s.found.singleOrNull()
    if (s.pairedTv != null) BackHandler { vm.showPairing(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(HostColors.Ink)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header("Guest Experience Host", "Connect to your TV")
        Text("TVs on this Wi-Fi network", style = MaterialTheme.typography.bodyMedium, color = HostColors.Muted)
        if (s.found.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    "Looking for TVs… Make sure the TV is on and this phone is on the same Wi-Fi. On Android TV, open Guest Experience TV.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        s.found.forEach { tv ->
            val isSelected = selected?.tvId == tv.tvId
            OutlinedCard(
                onClick = { selectedId = tv.tvId },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(2.dp, if (isSelected) HostColors.Gold else HostColors.Line),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(painterResource(R.drawable.ic_tv), contentDescription = null, tint = if (isSelected) HostColors.Gold else HostColors.Dim)
                    Column {
                        Text(tv.serviceName, style = MaterialTheme.typography.titleMedium)
                        val app = if (tv.kind == TvKind.SAMSUNG) "Samsung · Guest Welcome" else "Android TV · Guest Experience TV"
                        Text("$app · ${if (s.pairedTv?.tvId == tv.tvId) "Paired" else "Not paired yet"}", style = MaterialTheme.typography.bodySmall, color = HostColors.Muted)
                    }
                }
            }
        }
        TextButton(onClick = vm::refresh) { Text("Search again") }
        var address by rememberSaveable { mutableStateOf("") }
        Text("Samsung TV not listed? Add it by its IP address (on the TV: Settings › General › Network › Network Status › IP Settings).",
            style = MaterialTheme.typography.bodySmall, color = HostColors.Dim)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.filter { ch -> ch.isDigit() || ch == '.' }.take(15) },
                label = { Text("TV IP address") },
                placeholder = { Text("192.168.1.20") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { vm.addByAddress(address) }, enabled = address.isNotBlank() && !s.busy) { Text("Add") }
        }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(Validators.PAIRING_CODE_LENGTH) },
            label = { Text("Code shown on the TV") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (selected?.kind == TvKind.SAMSUNG) "On the Samsung TV: open Guest Welcome, press OK 5 times quickly, then choose Pair a phone."
            else "On the TV: hold OK for 3 seconds (or choose ⚙), enter your PIN, then open Pair a phone.",
            style = MaterialTheme.typography.bodySmall,
            color = HostColors.Dim,
        )
        Message(s)
        Button(
            onClick = { selected?.let { vm.pair(it, code) } },
            enabled = selected != null && code.length == Validators.PAIRING_CODE_LENGTH && !s.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (s.busy) "Pairing…" else "Pair") }
        if (s.pairedTv != null) OutlinedButton(onClick = { vm.showPairing(false) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun MainScreen(s: HostUiState, vm: HostViewModel) {
    var chosenTab by rememberSaveable { mutableStateOf(Tab.STAY) }
    // A Samsung TV shows only the guest name and message, so its Wi-Fi and property tabs are hidden.
    val tabs = if (s.welcomeOnly) listOf(Tab.STAY, Tab.TV) else Tab.entries
    val tab = chosenTab.takeIf { it in tabs } ?: Tab.STAY
    Scaffold(
        containerColor = HostColors.Ink,
        bottomBar = {
            Column {
                if (tab != Tab.TV && s.draft != null) {
                    Row(Modifier.fillMaxWidth().background(HostColors.Ink).padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (tab == Tab.STAY) OutlinedButton(onClick = vm::nextGuest, modifier = Modifier.weight(1f).height(52.dp)) { Text("Next guest") }
                        Button(onClick = vm::send, enabled = !s.busy, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(if (s.busy) "Sending…" else if (s.dirty) "Send to TV" else "Sent ✓")
                        }
                    }
                }
                NavigationBar(containerColor = HostColors.Ink) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { chosenTab = t },
                            icon = { Icon(painterResource(t.icon), contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ConnectionLine(s)
            Header("", when (tab) {
                Tab.STAY -> "This stay"
                Tab.WIFI -> "Guest Wi-Fi"
                Tab.PROPERTY -> "Property"
                Tab.TV -> "Your TV"
            })
            val d = s.draft
            when {
                tab == Tab.TV -> TvTab(s, vm)
                d == null -> Waiting(s, vm)
                tab == Tab.STAY -> StayTab(d, vm, showCheckOut = !s.welcomeOnly)
                tab == Tab.WIFI -> WifiTab(d, vm)
                tab == Tab.PROPERTY -> PropertyTab(d, vm)
            }
            if (tab != Tab.TV) Message(s)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectionLine(s: HostUiState) {
    val connected = s.connectedTv != null
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(if (connected) HostColors.Ok else HostColors.Dim, CircleShape))
        Text(
            "${s.pairedTv?.name ?: "TV"} · ${if (connected) "on this Wi-Fi" else "not found on this Wi-Fi"}",
            style = MaterialTheme.typography.bodySmall,
            color = HostColors.Muted,
        )
    }
}

@Composable
private fun Waiting(s: HostUiState, vm: HostViewModel) {
    Text(
        if (s.connectedTv == null) "Looking for your TV. Make sure it is on, and this phone is on the same Wi-Fi." +
            (if (s.welcomeOnly) "" else " On Android TV, open Guest Experience TV.")
        else "Loading what the TV is showing…",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = vm::refresh) { Text("Search again") }
        if (s.connectedTv != null) Button(onClick = vm::fetch, enabled = !s.busy) { Text("Retry") }
    }
    Message(s)
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    max: Int,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(max)) },
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val timeFormat12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@Composable
private fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = HostColors.Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onMinus, shape = CircleShape) { Text("−") }
            Text(value, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.width(120.dp))
            OutlinedButton(onClick = onPlus, shape = CircleShape) { Text("+") }
        }
    }
}

@Composable
private fun StayTab(d: PropertyContent, vm: HostViewModel, showCheckOut: Boolean) {
    Field("Guest name", d.guestName, { v -> vm.edit { it.copy(guestName = v) } }, Validators.NAME_MAX, "e.g. Ananya")
    val co = d.checkOut
    if (!showCheckOut) {
        // Samsung Guest Welcome has no check-out line.
    } else if (co == null) {
        OutlinedButton(onClick = { vm.edit { it.copy(checkOut = CheckOut(LocalDate.now().plusDays(1).toString(), "11:00")) } }) {
            Text("Add check-out time")
        }
    } else {
        val date = runCatching { LocalDate.parse(co.date) }.getOrElse { LocalDate.now() }
        val time = runCatching { LocalTime.parse(co.time) }.getOrElse { LocalTime.of(11, 0) }
        StepRow("Check-out date", date.format(dayFormat),
            { vm.edit { it.copy(checkOut = co.copy(date = date.minusDays(1).toString())) } },
            { vm.edit { it.copy(checkOut = co.copy(date = date.plusDays(1).toString())) } })
        StepRow("Check-out time", time.format(timeFormat12).uppercase(),
            { vm.edit { it.copy(checkOut = co.copy(time = time.minusMinutes(30).toString())) } },
            { vm.edit { it.copy(checkOut = co.copy(time = time.plusMinutes(30).toString())) } })
        TextButton(onClick = { vm.edit { it.copy(checkOut = null) } }) { Text("Don't show check-out") }
    }
    Field("Welcome message (optional)", d.hostMessage, { v -> vm.edit { it.copy(hostMessage = v) } }, Validators.MESSAGE_MAX,
        "Chai is on the house…", singleLine = false)
}

@Composable
private fun WifiTab(d: PropertyContent, vm: HostViewModel) {
    var show by rememberSaveable { mutableStateOf(false) }
    Field("Network name", d.wifi.ssid, { v -> vm.edit { it.copy(wifi = it.wifi.copy(ssid = v)) } }, 32)
    if (d.wifi.security != WifiSecurity.OPEN) {
        Field("Password", d.wifi.password, { v -> vm.edit { it.copy(wifi = it.wifi.copy(password = v)) } }, 64,
            keyboardType = KeyboardType.Password, secret = !show)
        TextButton(onClick = { show = !show }) { Text(if (show) "Hide password" else "Show password") }
    }
    Text("Security", style = MaterialTheme.typography.labelMedium, color = HostColors.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(WifiSecurity.WPA to "WPA", WifiSecurity.WEP to "WEP", WifiSecurity.OPEN to "Open").forEach { (sec, label) ->
            FilterChip(selected = d.wifi.security == sec, onClick = { vm.edit { it.copy(wifi = it.wifi.copy(security = sec)) } }, label = { Text(label) })
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Hidden network", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = d.wifi.hidden, onCheckedChange = { h -> vm.edit { it.copy(wifi = it.wifi.copy(hidden = h)) } })
    }
    Box(Modifier.fillMaxWidth().background(HostColors.Card, RoundedCornerShape(12.dp)).padding(14.dp)) {
        Text("Sent straight to the TV over your Wi-Fi, encrypted, and stored encrypted on the TV. Nothing goes to the internet.",
            style = MaterialTheme.typography.bodySmall, color = HostColors.Muted)
    }
}

@Composable
private fun PropertyTab(d: PropertyContent, vm: HostViewModel) {
    Field("Property name", d.propertyName, { v -> vm.edit { it.copy(propertyName = v) } }, Validators.PROPERTY_MAX)
    Field("Area or city", d.location, { v -> vm.edit { it.copy(location = v) } }, Validators.PROPERTY_MAX, "Udaipur")

    Text("HOUSE NOTES", style = Overline, modifier = Modifier.padding(top = 8.dp))
    val notes = (d.houseNotes + List(5) { "" }).take(5)
    notes.forEachIndexed { i, n ->
        Field("Note ${i + 1}", n, { v -> vm.edit { c -> c.copy(houseNotes = notes.toMutableList().also { it[i] = v }) } }, Validators.NOTE_MAX,
            if (i == 0) "Rooftop sunset is best at 5:30" else "")
    }

    Text("NEARBY PLACES", style = Overline, modifier = Modifier.padding(top = 8.dp))
    val places = (d.places + List(4) { Place("") }).take(4)
    places.forEachIndexed { i, p ->
        fun set(np: Place) = vm.edit { c -> c.copy(places = places.toMutableList().also { it[i] = np }) }
        Field("Place ${i + 1}", p.name, { set(p.copy(name = it)) }, Validators.NAME_MAX, if (i == 0) "Ambrai Ghat" else "")
        if (p.name.isNotBlank()) Field("Why go", p.description, { set(p.copy(description = it)) }, Validators.NOTE_MAX, if (i == 0) "Sunset views across the lake" else "")
    }

    Text("YOUR CONTACT", style = Overline, modifier = Modifier.padding(top = 8.dp))
    val c = d.hostContact ?: HostContact()
    Field("Name", c.name, { v -> vm.edit { it.copy(hostContact = c.copy(name = v)) } }, Validators.NAME_MAX)
    Field("Phone or WhatsApp", c.phone, { v -> vm.edit { it.copy(hostContact = c.copy(phone = v)) } }, 24, keyboardType = KeyboardType.Phone)
    Field("Note", c.note, { v -> vm.edit { it.copy(hostContact = c.copy(note = v)) } }, Validators.NOTE_MAX, "Call any time before 10 pm")

    Text("ON THE TV", style = Overline, modifier = Modifier.padding(top = 8.dp))
    Text("Background", style = MaterialTheme.typography.labelMedium, color = HostColors.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(BuiltInScene.UDAIPUR_DUSK to "Dusk", BuiltInScene.UDAIPUR_NIGHT to "Night", BuiltInScene.PLAIN to "Plain").forEach { (scene, label) ->
            FilterChip(
                selected = !d.background.useCustomPhoto && BuiltInScene.fromId(d.background.scene) == scene,
                onClick = { vm.edit { it.copy(background = it.background.copy(scene = scene.id, useCustomPhoto = false)) } },
                label = { Text(label) },
            )
        }
        if (d.background.useCustomPhoto) FilterChip(selected = true, onClick = {}, label = { Text("TV photo") })
    }
    Text("Photos are chosen on the TV itself (Host Mode › Background).", style = MaterialTheme.typography.bodySmall, color = HostColors.Dim)
    Text("Clock", style = MaterialTheme.typography.labelMedium, color = HostColors.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = d.timeFormat == TimeFormat.H12, onClick = { vm.edit { it.copy(timeFormat = TimeFormat.H12) } }, label = { Text("12-hour") })
        FilterChip(selected = d.timeFormat == TimeFormat.H24, onClick = { vm.edit { it.copy(timeFormat = TimeFormat.H24) } }, label = { Text("24-hour") })
    }
}

@Composable
private fun TvTab(s: HostUiState, vm: HostViewModel) {
    val tv = s.pairedTv ?: return
    Box(Modifier.fillMaxWidth().background(HostColors.Card, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tv.name, style = MaterialTheme.typography.titleMedium)
            Text(
                s.connectedTv?.let { "Found on this Wi-Fi" } ?: "Not found. Open the app on the TV, and check both are on the same Wi-Fi.",
                style = MaterialTheme.typography.bodySmall,
                color = HostColors.Muted,
            )
        }
    }
    OutlinedButton(onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) { Text("Search again") }
    OutlinedButton(onClick = { vm.showPairing(true) }, modifier = Modifier.fillMaxWidth()) { Text("Pair with a different TV") }
    TextButton(onClick = vm::forget, modifier = Modifier.fillMaxWidth()) { Text("Forget this TV", color = HostColors.Warn) }
    Text(
        "This phone and the TV talk only over your Wi-Fi. To stop this phone controlling the TV, also remove it on the TV under " +
            (if (s.welcomeOnly) "Pair a phone › Remove all phones." else "Host Mode › Pair a phone."),
        style = MaterialTheme.typography.bodySmall,
        color = HostColors.Dim,
    )
}
