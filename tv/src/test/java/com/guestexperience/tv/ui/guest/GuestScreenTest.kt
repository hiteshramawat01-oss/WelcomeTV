package com.guestexperience.tv.ui.guest

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guestexperience.shared.model.CheckOut
import com.guestexperience.shared.model.Place
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.model.WifiInfo
import com.guestexperience.tv.ui.theme.GuestTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Runs on the JVM with Robolectric at a 1080p TV size (960×540 dp). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
@OptIn(ExperimentalTestApi::class)
class GuestScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val content = PropertyContent(
        propertyName = "Pichola Haveli",
        location = "Udaipur",
        guestName = "Ananya",
        wifi = WifiInfo("Pichola-Guest", "lakeview2026"),
        checkOut = CheckOut("2026-11-26", "11:00"),
        places = listOf(Place("Ambrai Ghat", "Sunset views")),
    )
    private val evening = { LocalDateTime.of(2026, 11, 23, 19, 42) }
    private var hostOpened = 0

    private fun show() {
        rule.setContent {
            GuestTvTheme {
                GuestScreen(content, photo = null, onOpenHost = { hostOpened++ }, now = evening)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun rendersGreetingWifiAndCheckout() {
        show()
        rule.onNodeWithText("GOOD EVENING").assertIsDisplayed()
        rule.onNodeWithText("Welcome, Ananya").assertIsDisplayed()
        rule.onNodeWithText("to Pichola Haveli, Udaipur").assertIsDisplayed()
        rule.onNodeWithText("Pichola-Guest").assertIsDisplayed()
        rule.onNodeWithText("lakeview2026").assertIsDisplayed()
        rule.onNodeWithText("7:42").assertIsDisplayed()
        rule.onNodeWithText("Check-out   Thu 26 Nov · 11:00 AM").assertIsDisplayed()
    }

    @Test
    fun wifiCardHasInitialFocus() {
        show()
        rule.onNodeWithTag(GuestTags.WIFI_CARD).assertIsFocused()
    }

    @Test
    fun dpadMovesBetweenWifiCardAndSettingsWithNoDeadEnd() {
        show()
        val root = rule.onRoot()
        root.performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag(GuestTags.SETTINGS).assertIsFocused()

        // Nothing above or to the right of the settings tile: focus must stay, not disappear.
        root.performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag(GuestTags.SETTINGS).assertIsFocused()
        root.performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(GuestTags.SETTINGS).assertIsFocused()

        root.performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(GuestTags.WIFI_CARD).assertIsFocused()

        // Edges around the Wi-Fi card keep focus too.
        root.performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(GuestTags.WIFI_CARD).assertIsFocused()
        root.performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(GuestTags.WIFI_CARD).assertIsFocused()
    }

    @Test
    fun okOnWifiCardOpensLargeQrAndOkClosesIt() {
        show()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithTag(GuestTags.BIG_QR).assertIsDisplayed().assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithTag(GuestTags.BIG_QR).assertDoesNotExist()
        rule.onNodeWithTag(GuestTags.WIFI_CARD).assertIsFocused()
    }

    @Test
    fun okOnSettingsTileOpensHostMode() {
        show()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, hostOpened)
    }

    @Test
    fun holdingOkForThreeSecondsOpensHostMode() {
        show()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(3_100)
        }
        rule.mainClock.advanceTimeBy(3_100)
        assertEquals(1, hostOpened)
        rule.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
    }
}
