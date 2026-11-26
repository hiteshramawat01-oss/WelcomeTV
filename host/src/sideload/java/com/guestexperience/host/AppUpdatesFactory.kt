package com.guestexperience.host

import android.content.Context
import com.guestexperience.shared.update.AppUpdates
import com.guestexperience.shared.update.UpdateChannel
import com.guestexperience.updater.GitHubUpdater

/** Builds installed from GitHub Releases offer "Update available" (see the :updater module). */
fun createAppUpdates(context: Context): AppUpdates? =
    GitHubUpdater(context, UpdateChannel.HOST, BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME)
