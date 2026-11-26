package com.guestexperience.tv.ui.guest

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.guestexperience.shared.display.Greeting
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.model.WifiSecurity
import com.guestexperience.shared.wifi.WifiQrPayload
import com.guestexperience.tv.ui.components.GuestBackground
import com.guestexperience.tv.ui.components.QrCode
import com.guestexperience.tv.ui.components.TimeText
import com.guestexperience.tv.ui.components.focusBorder
import com.guestexperience.tv.ui.components.rememberMinuteClock
import com.guestexperience.tv.ui.theme.Gtv
import com.guestexperience.tv.ui.theme.GtvType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object GuestTags {
    const val WIFI_CARD = "guest_wifi_card"
    const val SETTINGS = "guest_settings"
    const val BIG_QR = "guest_big_qr"
}

private const val HOST_HOLD_MS = 3_000L

/** Translucent panel behind the stay details and the Wi-Fi card ("Welcome v2" design). */
private val PanelShape = RoundedCornerShape(12.dp)
private val PanelColor = Gtv.Ink.copy(alpha = 0.62f)
private val PanelEdge = Gtv.Gold.copy(alpha = 0.22f)
private val PanelLabelWidth = 104.dp

/**
 * The welcome screen ("Welcome v2", shared with the Samsung app). Two focus targets: the Wi-Fi card
 * (OK = large QR) and a small settings tile next to the clock (OK = Host Mode). Holding OK for 3 s
 * anywhere also opens Host Mode; on a touch screen, long-press.
 *
 * Sizes are in dp/sp on a 960×540 dp screen: half the 1920×1080 px of the design canvas.
 */
@Composable
fun GuestScreen(
    content: PropertyContent,
    photo: File?,
    onOpenHost: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    now: () -> LocalDateTime = LocalDateTime::now,
) {
    val clock by rememberMinuteClock(now)
    var bigQr by remember { mutableStateOf(false) }
    val wifiFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier
            .fillMaxSize()
            .then(
                if (!interactive) Modifier.focusProperties { canFocus = false } else Modifier
                    .onPreviewKeyEvent { event ->
                        val code = event.key.nativeKeyCode
                        if (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER) {
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                holdJob?.cancel()
                                holdJob = scope.launch { delay(HOST_HOLD_MS); onOpenHost() }
                            } else if (event.type == KeyEventType.KeyUp) {
                                holdJob?.cancel()
                            }
                        }
                        false
                    }
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenHost() }) },
            ),
    ) {
        GuestBackground(content.background, photo)

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 44.dp, bottom = 42.dp), // ≥5% overscan-safe margins
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                PropertyMark(content, Modifier.weight(1f).padding(top = 5.dp))
                ClockBlock(content, clock)
                if (interactive) {
                    Spacer(Modifier.width(14.dp))
                    SettingsTile(onOpenHost, Modifier.padding(top = 3.dp))
                }
            }
            GreetingBlock(content, clock)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { StayPanel(content) }
                if (content.wifi.ssid.isNotBlank()) {
                    Spacer(Modifier.width(20.dp))
                    WifiCard(
                        content = content,
                        onClick = { bigQr = true },
                        modifier = Modifier
                            .focusProperties { canFocus = interactive }
                            .focusRequester(wifiFocus),
                    )
                }
            }
        }

        if (bigQr) {
            BigQrOverlay(content, onClose = { bigQr = false; runCatching { wifiFocus.requestFocus() } })
        }
    }

    if (interactive) {
        LaunchedEffect(content.wifi.ssid.isNotBlank()) {
            if (content.wifi.ssid.isNotBlank()) runCatching { wifiFocus.requestFocus() }
        }
    }
}

/** Gold dot and "PROPERTY · AREA" in the top-left corner. */
@Composable
private fun PropertyMark(content: PropertyContent, modifier: Modifier) {
    val place = listOf(content.propertyName, content.location).filter { it.isNotBlank() }.joinToString(" · ")
    if (place.isEmpty()) {
        Spacer(modifier)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(5.dp).background(Gtv.Gold, CircleShape))
        Text(
            place.uppercase(),
            style = GtvType.Overline.copy(fontSize = 11.sp, letterSpacing = GtvType.Overline.letterSpacing * 1.1f, color = Gtv.Soft),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClockBlock(content: PropertyContent, clock: LocalDateTime) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val (time, suffix) = TimeText.clock(clock.toLocalTime(), content.timeFormat)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            // Lining figures: Cormorant's default old-style digits make "10:15" read as "IO:I5".
            Text(time, style = GtvType.Clock.copy(fontSize = 56.sp, lineHeight = 52.sp, fontFeatureSettings = "lnum"))
            if (suffix != null) {
                Text(suffix, style = GtvType.BodyStrong.copy(fontSize = 15.sp, color = Gtv.Soft), modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        Text(TimeText.longDay(clock.toLocalDate()), style = GtvType.Body.copy(color = Gtv.Muted))
    }
}

@Composable
private fun GreetingBlock(content: PropertyContent, clock: LocalDateTime) {
    val greeting = when (Greeting.forHour(clock.hour)) {
        Greeting.MORNING -> "Good morning"
        Greeting.AFTERNOON -> "Good afternoon"
        Greeting.EVENING -> "Good evening"
        Greeting.NIGHT -> "Good night"
    }
    val name = content.guestName.trim()
    Column(Modifier.widthIn(max = 540.dp)) {
        Text(greeting.uppercase(), style = GtvType.Overline, modifier = Modifier.padding(bottom = 10.dp))
        if (name.isEmpty()) {
            Text("Welcome", style = GtvType.Greeting.copy(fontSize = 88.sp, lineHeight = 90.sp))
        } else {
            Text("Welcome,", style = GtvType.Title.copy(fontSize = 36.sp, lineHeight = 36.sp, color = Gtv.Soft))
            Text(name, style = GtvType.Greeting.copy(fontSize = 88.sp, lineHeight = 90.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(top = 16.dp, bottom = 14.dp).size(width = 48.dp, height = 1.dp).background(Gtv.Gold))
        if (content.hostMessage.isNotBlank()) {
            Text(
                content.hostMessage.trim(),
                style = GtvType.BodyStrong.copy(fontWeight = GtvType.Body.fontWeight, color = Gtv.Soft, lineHeight = 24.sp),
                modifier = Modifier.widthIn(max = 410.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Check-out time and one line that rotates through house notes, nearby places and the host contact. */
@Composable
private fun StayPanel(content: PropertyContent) {
    val checkOut = content.checkOut?.let { co ->
        val date = runCatching { LocalDate.parse(co.date) }.getOrNull()
        val t = runCatching { LocalTime.parse(co.time) }.getOrNull()
        if (date != null && t != null) "${TimeText.day(date)} · ${TimeText.clockString(t, content.timeFormat)}" else null
    }
    val items = remember(content) { tickerItems(content) }
    if (checkOut == null && items.isEmpty()) return

    var index by remember(items) { mutableIntStateOf(0) }
    LaunchedEffect(items) {
        while (items.size > 1) {
            delay(12_000)
            index = (index + 1) % items.size
        }
    }
    Column(
        Modifier
            .widthIn(max = 450.dp)
            .background(PanelColor, PanelShape)
            .border(1.dp, PanelEdge, PanelShape)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (checkOut != null) PanelRow("Check-out") { Text(checkOut, style = GtvType.BodyStrong, maxLines = 1) }
        if (checkOut != null && items.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(Gtv.Paper.copy(alpha = 0.1f)))
        if (items.isNotEmpty()) {
            AnimatedContent(
                targetState = items[index.coerceIn(0, items.lastIndex)],
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ticker",
            ) { (label, text) ->
                PanelRow(label) { Text(text, style = GtvType.Body.copy(color = Gtv.Soft), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (items.size > 1) {
                Row(Modifier.padding(start = PanelLabelWidth), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.indices.forEach { i ->
                        val on = i == index
                        Box(
                            Modifier
                                .size(width = if (on) 11.dp else 4.dp, height = 2.dp)
                                .background(if (on) Gtv.Gold else Gtv.Paper.copy(alpha = 0.3f), RoundedCornerShape(1.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(label: String, value: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = GtvType.Overline.copy(fontSize = 10.sp), maxLines = 1, modifier = Modifier.width(PanelLabelWidth))
        value()
    }
}

private fun tickerItems(content: PropertyContent): List<Pair<String, String>> = buildList {
    content.houseNotes.filter { it.isNotBlank() }.forEach { add("House note" to it) }
    content.places.filter { it.name.isNotBlank() }.forEach {
        add("Nearby" to listOf(it.name, it.description).filter(String::isNotBlank).joinToString(" — "))
    }
    content.hostContact?.let { c ->
        val text = listOf(c.name, c.phone, c.note).filter(String::isNotBlank).joinToString(" · ")
        if (text.isNotBlank()) add("Your host" to text)
    }
}

@Composable
private fun WifiCard(content: PropertyContent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag(GuestTags.WIFI_CARD),
        shape = ClickableSurfaceDefaults.shape(PanelShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Gtv.Ink.copy(alpha = 0.72f),
            contentColor = Gtv.Paper,
            focusedContainerColor = Gtv.Ink.copy(alpha = 0.82f),
            focusedContentColor = Gtv.Paper,
        ),
        border = focusBorder(PanelShape, idle = PanelEdge),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QrCode(
                WifiQrPayload.build(content.wifi),
                Modifier.size(100.dp),
                description = "Wi-Fi QR code for ${content.wifi.ssid}",
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("WI-FI", style = GtvType.Overline.copy(fontSize = 10.sp))
                Text(content.wifi.ssid, style = GtvType.BodyStrong.copy(fontSize = 18.sp, lineHeight = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 270.dp))
                if (content.wifi.security != WifiSecurity.OPEN) {
                    Text(content.wifi.password, style = GtvType.BodyStrong.copy(fontSize = 15.sp, color = Gtv.Soft, fontWeight = GtvType.Body.fontWeight), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 270.dp))
                }
                Text("Scan with your phone camera · OK to enlarge", style = GtvType.Label.copy(fontSize = 11.sp), modifier = Modifier.padding(top = 3.dp).widthIn(max = 270.dp))
            }
        }
    }
}

@Composable
private fun SettingsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.size(32.dp).testTag(GuestTags.SETTINGS),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Gtv.Ink.copy(alpha = 0.4f),
            contentColor = Gtv.Dim,
            focusedContainerColor = Gtv.Paper,
            focusedContentColor = Gtv.Ink,
        ),
        border = focusBorder(shape, idle = Gtv.Paper.copy(alpha = 0.16f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Text("⚙", style = GtvType.Title.copy(fontSize = 18.sp), modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun BigQrOverlay(content: PropertyContent, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Gtv.Ink.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClose,
            modifier = Modifier.focusRequester(focus).testTag(GuestTags.BIG_QR),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
                Text("JOIN THE WI-FI", style = GtvType.Overline)
                QrCode(WifiQrPayload.build(content.wifi), Modifier.size(300.dp), description = "Large Wi-Fi QR code")
                Text(content.wifi.ssid, style = GtvType.BodyStrong.copy(fontSize = 22.sp, lineHeight = 26.sp))
                if (content.wifi.security != WifiSecurity.OPEN) {
                    Text("Password · ${content.wifi.password}", style = GtvType.BodyStrong.copy(color = Gtv.Soft, fontWeight = GtvType.Body.fontWeight))
                }
                Text("Point your phone camera at the code · Press OK or Back to close", style = GtvType.Label)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler(onBack = onClose)
}
