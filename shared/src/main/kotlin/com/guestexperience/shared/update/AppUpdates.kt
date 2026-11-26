package com.guestexperience.shared.update

import kotlinx.coroutines.flow.StateFlow

/** What the app shows about its own updates. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    /** Android needs "Install unknown apps" allowed for this app first. */
    data class NeedsPermission(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val percent: Int) : UpdateState
    /** Android's install dialog is open (or the update is being installed). */
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

/**
 * Self-updates for builds installed outside Google Play (the `sideload` flavour). Play builds have
 * none: the app's update UI is simply absent there.
 */
interface AppUpdates {
    val installedVersionName: String
    val state: StateFlow<UpdateState>

    /** Checks GitHub if the last check was long enough ago (call when the app comes to the front). */
    fun checkIfDue()

    fun check()

    /** Downloads, verifies and opens Android's install dialog; asks for the install permission first if needed. */
    fun install()

    /** Opens Android's "Install unknown apps" setting for this app. */
    fun openInstallPermission()
}
