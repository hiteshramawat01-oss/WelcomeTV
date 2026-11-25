package com.guestexperience.tv

import android.app.Application
import android.content.Context
import com.guestexperience.tv.di.AppContainer

class GuestTvApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer get() = (applicationContext as GuestTvApp).container
