package com.guestexperience.tv

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guestexperience.shared.model.BackgroundChoice
import com.guestexperience.shared.model.BuiltInScene
import com.guestexperience.shared.model.CheckOut
import com.guestexperience.shared.model.HostContact
import com.guestexperience.shared.model.Place
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.model.WifiInfo
import com.guestexperience.shared.sync.PairingCodeManager
import com.guestexperience.tv.data.ImageStore
import com.guestexperience.tv.data.SecretCipher
import com.guestexperience.tv.data.TvRepository
import com.guestexperience.tv.dream.ScreensaverContent
import com.guestexperience.tv.ui.guest.GuestScreen
import com.guestexperience.tv.ui.host.HostScreen
import com.guestexperience.tv.ui.host.HostViewModel
import com.guestexperience.tv.ui.host.PinScreen
import com.guestexperience.tv.ui.host.PinViewModel
import com.guestexperience.tv.ui.theme.GuestTvTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

/**
 * Renders real screens at 1920×1080 into tv/build/screenshots/. Used to review the UI without a
 * device and as a starting point for Play Store TV screenshots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val outDir = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
    private val evening = { LocalDateTime.of(2026, 11, 23, 19, 42) }

    private val content = PropertyContent(
        propertyName = "Pichola Haveli",
        location = "Udaipur",
        guestName = "Ananya",
        hostMessage = "",
        wifi = WifiInfo("Pichola-Guest", "lakeview2026"),
        checkOut = CheckOut("2026-11-26", "11:00"),
        houseNotes = listOf("Rooftop sunset is best at 5:30"),
        places = listOf(Place("Ambrai Ghat", "Sunset views across the lake")),
        hostContact = HostContact("Meera", "WhatsApp +91 00000 00000"),
    )

    private val plainCipher = object : SecretCipher {
        override fun encrypt(plain: String) = plain
        override fun decrypt(blob: String) = blob
    }

    private fun repository(): TvRepository {
        val store = PreferenceDataStoreFactory.create { File(tmp.root, "test.preferences_pb") }
        return TvRepository(store, plainCipher, tvId = "tv", deviceName = { "Living room TV" }).also {
            runBlocking { it.completeOnboarding(content, "4829") }
        }
    }

    private fun shoot(name: String, ui: @Composable () -> Unit) {
        rule.setContent { GuestTvTheme { ui() } }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun guestDusk() = shoot("01-guest-dusk") { GuestScreen(content, null, {}, now = evening) }

    @Test fun guestNight() = shoot("02-guest-night") {
        GuestScreen(content.copy(background = BackgroundChoice(BuiltInScene.UDAIPUR_NIGHT.id)), null, {}, now = { LocalDateTime.of(2026, 11, 23, 22, 15) })
    }

    @Test fun screensaver() = shoot("03-screensaver") { ScreensaverContent(content, null) }

    @Test fun pin() {
        val vm = PinViewModel(repository())
        vm.digit('4'); vm.digit('8')
        shoot("04-host-pin") { PinScreen(vm, {}, {}) }
    }

    @Test fun hostEditor() {
        val context = RuntimeEnvironment.getApplication()
        val vm = HostViewModel(repository(), ImageStore(context), PairingCodeManager(), {})
        shoot("05-host-editor") { HostScreen(vm, {}) }
    }
}
