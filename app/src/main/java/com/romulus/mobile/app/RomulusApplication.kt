package com.romulus.mobile.app

import android.app.Application

class RomulusApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(applicationContext) }
}
