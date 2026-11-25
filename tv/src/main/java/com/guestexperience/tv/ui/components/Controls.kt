package com.guestexperience.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.guestexperience.tv.ui.theme.Gtv
import com.guestexperience.tv.ui.theme.GtvType

private val Pill = RoundedCornerShape(50)
private val FieldShape = RoundedCornerShape(10.dp)

/** Focus look used everywhere: 3 dp gold outline + slight lift, readable from across a room. */
@Composable
fun focusBorder(shape: androidx.compose.ui.graphics.Shape, idle: Color = Gtv.Line, idleWidth: Dp = 2.dp) =
    ClickableSurfaceDefaults.border(
        border = Border(BorderStroke(idleWidth, idle), shape = shape),
        focusedBorder = Border(BorderStroke(3.dp, Gtv.Gold), shape = shape),
        pressedBorder = Border(BorderStroke(3.dp, Gtv.Gold), shape = shape),
    )

@Composable
fun GtvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 40.dp),
        shape = ClickableSurfaceDefaults.shape(Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Gtv.Gold else Color.Transparent,
            contentColor = if (primary) Gtv.Ink else Gtv.Paper,
            focusedContainerColor = Gtv.Paper,
            focusedContentColor = Gtv.Ink,
            pressedContainerColor = Gtv.Paper,
            pressedContentColor = Gtv.Ink,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Gtv.Dim,
        ),
        border = focusBorder(Pill, idle = if (primary) Color.Transparent else Gtv.LineStrong),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text,
            style = GtvType.Button,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 22.dp, vertical = 11.dp),
        )
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = ClickableSurfaceDefaults.shape(Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Gtv.Paper else Color.Transparent,
            contentColor = if (selected) Gtv.Ink else Gtv.Soft,
            focusedContainerColor = if (selected) Gtv.Paper else Gtv.Card,
            focusedContentColor = if (selected) Gtv.Ink else Gtv.Paper,
        ),
        border = focusBorder(Pill, idle = if (selected) Color.Transparent else Gtv.LineStrong),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            (if (selected) "✓  " else "") + text,
            style = GtvType.Button,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = GtvType.Label, modifier = modifier)
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) Text(text, style = GtvType.Label.copy(color = Gtv.Warn))
}

/**
 * A text field that behaves well with a TV remote. Focus lands on a read-only box; OK opens the
 * on-screen keyboard; Done, Back, Up or Down closes it and focus stays on the box, so the D-pad
 * never gets trapped moving the cursor.
 */
@Composable
fun TvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLength: Int = 200,
    testTag: String? = null,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    val boxFocus = remember { FocusRequester() }
    val editFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        if (!editing) {
            Surface(
                onClick = { editing = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (singleLine) 44.dp else 76.dp)
                    .focusRequester(boxFocus)
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                shape = ClickableSurfaceDefaults.shape(FieldShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Gtv.Card,
                    contentColor = Gtv.Paper,
                    focusedContainerColor = Gtv.Card,
                    focusedContentColor = Gtv.Paper,
                ),
                border = focusBorder(FieldShape, idle = if (error != null) Gtv.Warn else Gtv.Line),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
                Text(
                    text = value.ifEmpty { placeholder },
                    style = GtvType.BodyStrong.copy(color = if (value.isEmpty()) Gtv.Dim else Gtv.Paper),
                    maxLines = if (singleLine) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(if (singleLine) Alignment.CenterStart else Alignment.TopStart)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
            LaunchedEffect(returnFocus) {
                if (returnFocus) {
                    runCatching { boxFocus.requestFocus() }
                    returnFocus = false
                }
            }
        } else {
            fun close() {
                keyboard?.hide()
                editing = false
                returnFocus = true
            }
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(if (singleLine) it.replace("\n", "").take(maxLength) else it.take(maxLength)) },
                singleLine = singleLine,
                textStyle = GtvType.BodyStrong,
                cursorBrush = SolidColor(Gtv.Gold),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { close() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (singleLine) 44.dp else 76.dp)
                    .border(3.dp, Gtv.Gold, FieldShape)
                    .padding(horizontal = 14.dp, vertical = 11.dp)
                    .focusRequester(editFocus)
                    .onPreviewKeyEvent { event ->
                        val code = event.key.nativeKeyCode
                        val exits = code == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                            code == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
                            code == AndroidKeyEvent.KEYCODE_BACK
                        if (exits && event.type == KeyEventType.KeyUp) close()
                        exits
                    },
            )
            LaunchedEffect(Unit) {
                runCatching { editFocus.requestFocus() }
                keyboard?.show()
            }
        }
        ErrorText(error)
    }
}

/** − value + control; easier than typing dates and times with a remote. */
@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GtvButton("−", onMinus)
            Text(value, style = GtvType.BodyStrong, textAlign = TextAlign.Center, modifier = Modifier.width(110.dp))
            GtvButton("+", onPlus)
        }
    }
}
