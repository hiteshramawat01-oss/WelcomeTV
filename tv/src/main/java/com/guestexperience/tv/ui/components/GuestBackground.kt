package com.guestexperience.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.guestexperience.shared.model.BackgroundChoice
import com.guestexperience.shared.model.BuiltInScene
import com.guestexperience.tv.ui.theme.Gtv
import java.io.File

/** Built-in scene or the host's photo, plus the dark scrims that keep text readable. */
@Composable
fun GuestBackground(choice: BackgroundChoice, photo: File?, modifier: Modifier = Modifier, dimForScreensaver: Boolean = false) {
    Box(modifier.fillMaxSize().background(Gtv.Ink)) {
        if (choice.useCustomPhoto && photo != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo)
                    .size(1920, 1080)
                    .memoryCacheKey("bg-${photo.lastModified()}")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            when (BuiltInScene.fromId(choice.scene)) {
                BuiltInScene.UDAIPUR_DUSK -> UdaipurScene(night = dimForScreensaver, modifier = Modifier.fillMaxSize())
                BuiltInScene.UDAIPUR_NIGHT -> UdaipurScene(night = true, modifier = Modifier.fillMaxSize())
                BuiltInScene.PLAIN -> Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(Color(0xFF1A1712), Gtv.Ink), radius = 1400f),
                    ),
                )
            }
        }
        // Left-to-right and bottom-up scrims: text sits on the dark side.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Gtv.Ink.copy(alpha = 0.9f),
                    0.42f to Gtv.Ink.copy(alpha = 0.55f),
                    0.7f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.6f to Color.Transparent,
                    1f to Gtv.Ink.copy(alpha = if (dimForScreensaver) 0.85f else 0.75f),
                ),
            ),
        )
    }
}
