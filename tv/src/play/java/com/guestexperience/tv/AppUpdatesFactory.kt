package com.guestexperience.tv

import android.content.Context
import com.guestexperience.shared.update.AppUpdates

/** Google Play builds don't update themselves: Play does it. */
@Suppress("UNUSED_PARAMETER")
fun createAppUpdates(context: Context): AppUpdates? = null
