package com.guestexperience.tv.dream

import android.service.dreams.DreamService
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.Text
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.tv.appContainer
import com.guestexperience.tv.ui.components.GuestBackground
import com.guestexperience.tv.ui.components.TimeText
import com.guestexperience.tv.ui.components.rememberMinuteClock
import com.guestexperience.tv.ui.theme.GuestTvTheme
import com.guestexperience.tv.ui.theme.Gtv
import com.guestexperience.tv.ui.theme.GtvType
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

/**
 * Screensaver: a calm, dimmer version of the welcome screen. Shown when the TV is idle on devices
 * that let the user pick a third-party screensaver.
 */
class GuestDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val repository = appContainer.repository
        val imageStore = appContainer.imageStore
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GuestDreamService)
            setViewTreeSavedStateRegistryOwner(this@GuestDreamService)
            setContent {
                GuestTvTheme {
                    val content by repository.content.collectAsState(initial = null)
                    content?.let { ScreensaverContent(it, if (imageStore.hasPhoto()) imageStore.photoFile else null) }
                }
            }
        }
        setContentView(view)
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDetachedFromWindow()
    }
}

@Composable
fun ScreensaverContent(content: PropertyContent, photo: File?) {
    val clock by rememberMinuteClock()
    // Drift a few dp every minute so nothing stays on the same pixels (burn-in protection).
    var drift by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            drift = Random.nextInt(-16, 17) to Random.nextInt(-12, 13)
        }
    }
    Box(Modifier.fillMaxSize()) {
        GuestBackground(content.background, photo, dimForScreensaver = true)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 70.dp, bottom = 60.dp)
                .offset { IntOffset(drift.first.dp.roundToPx(), drift.second.dp.roundToPx()) },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val (time, suffix) = TimeText.clock(clock.toLocalTime(), content.timeFormat)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, style = GtvType.Clock.copy(fontSize = GtvType.Clock.fontSize * 1.5f, color = Gtv.Soft))
                if (suffix != null) Text(" $suffix", style = GtvType.Subtitle.copy(color = Gtv.Muted))
            }
            val line = buildList {
                if (content.propertyName.isNotBlank()) add(content.propertyName)
                if (content.wifi.ssid.isNotBlank()) add("Wi-Fi ${content.wifi.ssid}")
            }.joinToString("  ·  ")
            if (line.isNotEmpty()) Text(line, style = GtvType.Body.copy(color = Gtv.Muted))
        }
    }
}
