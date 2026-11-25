package com.guestexperience.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.guestexperience.tv.ui.theme.Gtv
import com.guestexperience.tv.ui.theme.GtvType

@Composable
fun PinDots(filled: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "$filled digits entered" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(total) { i ->
            val m = Modifier.size(14.dp)
            Box(if (i < filled) m.background(Gtv.Gold, CircleShape) else m.border(BorderStroke(2.dp, Gtv.LineStrong), CircleShape))
        }
    }
}

/**
 * 3×4 keypad. Remote number keys and the Delete key work too, so hosts with a numeric remote can
 * just type.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    submitLabel: String = "OK",
    enabled: Boolean = true,
    requestInitialFocus: Boolean = true,
) {
    val first = remember { FocusRequester() }
    val shape = RoundedCornerShape(12.dp)
    @Composable
    fun Key(label: String, tag: String, onClick: () -> Unit, mod: Modifier = Modifier) {
        Surface(
            onClick = { if (enabled) onClick() },
            modifier = mod.size(width = 84.dp, height = 56.dp).testTag(tag),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Gtv.Card,
                contentColor = Gtv.Paper,
                focusedContainerColor = Gtv.Paper,
                focusedContentColor = Gtv.Ink,
            ),
            border = focusBorder(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        ) {
            // No explicit colour: the label follows the surface's focused/unfocused content colour.
            Text(
                label,
                style = if (label.length == 1) GtvType.Button.copy(fontSize = GtvType.BodyStrong.fontSize * 1.3f, fontWeight = FontWeight.Medium) else GtvType.Button,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
    Column(
        modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val code = e.key.nativeKeyCode
            when (code) {
                in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> {
                    if (enabled) onDigit('0' + (code - AndroidKeyEvent.KEYCODE_0)); true
                }
                AndroidKeyEvent.KEYCODE_DEL -> { if (enabled) onDelete(); true }
                else -> false
            }
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf("123", "456", "789").forEachIndexed { row, digits ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                digits.forEachIndexed { col, d ->
                    Key(d.toString(), "pin_$d", { onDigit(d) }, if (row == 0 && col == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Key("Delete", "pin_delete", onDelete)
            Key("0", "pin_0", { onDigit('0') })
            Key(submitLabel, "pin_submit", onSubmit)
        }
    }
    if (requestInitialFocus) LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
fun PinHeader(overline: String, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(overline.uppercase(), style = GtvType.Overline)
        Text(title, style = GtvType.Title.copy(fontSize = GtvType.Title.fontSize * 1.1f))
        Text(hint, style = GtvType.Body)
    }
}
