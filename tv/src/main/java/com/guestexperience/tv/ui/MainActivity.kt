package com.guestexperience.tv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guestexperience.tv.appContainer
import com.guestexperience.tv.ui.guest.GuestScreen
import com.guestexperience.tv.ui.host.HostScreen
import com.guestexperience.tv.ui.host.HostViewModel
import com.guestexperience.tv.ui.host.PinScreen
import com.guestexperience.tv.ui.host.PinViewModel
import com.guestexperience.tv.ui.onboarding.OnboardingScreen
import com.guestexperience.tv.ui.onboarding.OnboardingViewModel
import com.guestexperience.tv.ui.theme.GuestTvTheme
import com.guestexperience.tv.ui.theme.Gtv

class MainActivity : ComponentActivity() {
    private val container by lazy { appContainer }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuestTvTheme {
                AppRoot(onExitApp = ::finish)
            }
        }
    }

    // The local sync server only runs while the app is visible.
    override fun onStart() {
        super.onStart()
        container.syncController.start()
        container.updates?.checkIfDue()
    }

    override fun onStop() {
        container.syncController.stop()
        super.onStop()
    }
}

@Composable
private fun AppRoot(onExitApp: () -> Unit) {
    val container = androidx.compose.ui.platform.LocalContext.current.appContainer
    val app: AppViewModel = viewModel { AppViewModel(container.repository, container.imageStore) }
    val state by app.state.collectAsStateWithLifecycle()
    val content by app.content.collectAsStateWithLifecycle()
    val photo by app.photo.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Gtv.Ink)) {
        when (val s = state) {
            AppState.Loading -> Unit
            AppState.Onboarding -> {
                val vm: OnboardingViewModel = viewModel { OnboardingViewModel(container.repository) }
                OnboardingScreen(vm, onExitApp)
            }
            is AppState.Ready -> when (s.screen) {
                Screen.Guest -> content?.let { GuestScreen(it, photo, onOpenHost = app::openPin) }
                Screen.Pin -> {
                    // A fresh PIN screen each time Host Mode is requested.
                    val vm: PinViewModel = viewModel(key = "pin-${System.identityHashCode(s)}") { PinViewModel(container.repository) }
                    PinScreen(vm, onUnlocked = app::openHost, onCancel = app::backToGuest)
                }
                Screen.Host -> {
                    val vm: HostViewModel = viewModel(key = "host-${System.identityHashCode(s)}") {
                        HostViewModel(container.repository, container.imageStore, container.pairingCodes, app::photoChanged)
                    }
                    HostScreen(vm, onExit = app::backToGuest)
                }
            }
        }
    }
}
