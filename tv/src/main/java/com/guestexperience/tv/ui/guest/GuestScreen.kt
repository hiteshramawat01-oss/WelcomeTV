package com.guestexperience.tv.ui.guest

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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

/**
 * The welcome screen. Two focus targets: the Wi-Fi card (OK = large QR) and a small settings tile
 * (OK = Host Mode). Holding OK for 3 s anywhere also opens Host Mode; on a touch screen, long-press.
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
                .padding(horizontal = 56.dp, vertical = 48.dp), // ≥5% overscan-safe margins
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier, verticalAlignment = Alignment.Top) {
                GreetingBlock(content, clock, Modifier.weight(1f))
                if (interactive) SettingsTile(onOpenHost)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val (time, suffix) = TimeText.clock(clock.toLocalTime(), content.timeFormat)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(time, style = GtvType.Clock)
                        if (suffix != null) Text(" $suffix", style = GtvType.Subtitle.copy(color = Gtv.Soft), modifier = Modifier.padding(bottom = 6.dp))
                    }
                    content.checkOut?.let { co ->
                        val date = runCatching { LocalDate.parse(co.date) }.getOrNull()
                        val t = runCatching { LocalTime.parse(co.time) }.getOrNull()
                        if (date != null && t != null) {
                            Text(
                                "Check-out   ${TimeText.day(date)} · ${TimeText.clockString(t, content.timeFormat)}",
                                style = GtvType.BodyStrong.copy(color = Gtv.Soft),
                            )
                        }
                    }
                    InfoTicker(content)
                }
                if (content.wifi.ssid.isNotBlank()) {
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

@Composable
private fun GreetingBlock(content: PropertyContent, clock: LocalDateTime, modifier: Modifier) {
    val greeting = when (Greeting.forHour(clock.hour)) {
        Greeting.MORNING -> "Good morning"
        Greeting.AFTERNOON -> "Good afternoon"
        Greeting.EVENING -> "Good evening"
        Greeting.NIGHT -> "Good night"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(greeting.uppercase(), style = GtvType.Overline.copy(fontSize = GtvType.Overline.fontSize * 1.08f))
        Text(
            if (content.guestName.isBlank()) "Welcome" else "Welcome, ${content.guestName.trim()}",
            style = GtvType.Greeting,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val place = listOf(content.propertyName, content.location).filter { it.isNotBlank() }.joinToString(", ")
        if (place.isNotEmpty()) Text("to $place", style = GtvType.Subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (content.hostMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(content.hostMessage, style = GtvType.Body, modifier = Modifier.widthIn(max = 420.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One quiet line that rotates through house notes, nearby places and the host contact. */
@Composable
private fun InfoTicker(content: PropertyContent) {
    val items = remember(content) {
        buildList {
            content.houseNotes.filter { it.isNotBlank() }.forEach { add("House note" to it) }
            content.places.filter { it.name.isNotBlank() }.forEach {
                add("Nearby" to listOf(it.name, it.description).filter(String::isNotBlank).joinToString(" — "))
            }
            content.hostContact?.let { c ->
                val text = listOf(c.name, c.phone, c.note).filter(String::isNotBlank).joinToString(" · ")
                if (text.isNotBlank()) add("Your host" to text)
            }
        }
    }
    if (items.isEmpty()) return
    var index by remember(items) { mutableIntStateOf(0) }
    LaunchedEffect(items) {
        while (items.size > 1) {
            delay(12_000)
            index = (index + 1) % items.size
        }
    }
    AnimatedContent(
        targetState = items[index.coerceIn(0, items.lastIndex)],
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ticker",
    ) { (label, text) ->
        Row(Modifier.padding(top = 6.dp).widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label.uppercase(), style = GtvType.Overline)
            Text(text, style = GtvType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WifiCard(content: PropertyContent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.testTag(GuestTags.WIFI_CARD),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Gtv.Ink.copy(alpha = 0.78f),
            contentColor = Gtv.Paper,
            focusedContainerColor = Gtv.Ink.copy(alpha = 0.85f),
            focusedContentColor = Gtv.Paper,
        ),
        border = focusBorder(shape, idle = Gtv.Gold.copy(alpha = 0.5f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QrCode(
                WifiQrPayload.build(content.wifi),
                Modifier.size(100.dp),
                description = "Wi-Fi QR code for ${content.wifi.ssid}",
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("WI-FI", style = GtvType.Overline)
                Text(content.wifi.ssid, style = GtvType.BodyStrong.copy(fontSize = GtvType.BodyStrong.fontSize * 1.06f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                if (content.wifi.security != WifiSecurity.OPEN) {
                    Text(content.wifi.password, style = GtvType.BodyStrong.copy(color = Gtv.Soft, fontWeight = GtvType.Body.fontWeight), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                }
                Text("Scan to join · OK to enlarge", style = GtvType.Label)
            }
        }
    }
}

@Composable
private fun SettingsTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp).testTag(GuestTags.SETTINGS),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Gtv.Dim,
            focusedContainerColor = Gtv.Paper,
            focusedContentColor = Gtv.Ink,
        ),
        border = focusBorder(shape, idle = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Text("⚙", style = GtvType.Title.copy(fontSize = GtvType.BodyStrong.fontSize * 1.4f), modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun BigQrOverlay(content: PropertyContent, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Gtv.Ink.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClose,
            modifier = Modifier.focusRequester(focus).testTag(GuestTags.BIG_QR),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(12.dp)) {
                QrCode(WifiQrPayload.build(content.wifi), Modifier.size(320.dp), description = "Large Wi-Fi QR code")
                Text(content.wifi.ssid, style = GtvType.BodyStrong)
                Text("Press OK or Back to close", style = GtvType.Label)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler(onBack = onClose)
}
