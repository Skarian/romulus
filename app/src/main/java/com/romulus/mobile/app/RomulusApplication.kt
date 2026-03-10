package com.romulus.mobile.app

import android.app.Application
import com.romulus.mobile.app.startup.AppGraph

class RomulusApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(applicationContext) }
    val appGraph: AppGraph by lazy { AppGraph.create(this) }
}
