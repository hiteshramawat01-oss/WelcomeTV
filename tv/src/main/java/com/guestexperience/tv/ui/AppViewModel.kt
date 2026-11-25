package com.guestexperience.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.tv.data.ImageStore
import com.guestexperience.tv.data.TvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

enum class Screen { Guest, Pin, Host }

sealed interface AppState {
    data object Loading : AppState
    data object Onboarding : AppState
    data class Ready(val screen: Screen) : AppState
}

/** Top-level navigation, plus the content the guest screen shows. */
class AppViewModel(
    repository: TvRepository,
    private val imageStore: ImageStore,
) : ViewModel() {
    private val screen = MutableStateFlow(Screen.Guest)

    val state: StateFlow<AppState> = combine(repository.onboarded, screen) { onboarded, s ->
        if (!onboarded) AppState.Onboarding else AppState.Ready(s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    val content: StateFlow<PropertyContent?> = repository.content
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val photoVersion = MutableStateFlow(0)
    val photo: StateFlow<File?> = photoVersion
        .map { if (imageStore.hasPhoto()) imageStore.photoFile else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentScreen: StateFlow<Screen> = screen.asStateFlow()

    fun openPin() { screen.value = Screen.Pin }
    fun openHost() { screen.value = Screen.Host }
    fun backToGuest() { screen.value = Screen.Guest }
    fun photoChanged() { photoVersion.value++ }
}
