package com.romulus.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.romulus.mobile.app.RomulusApp

class MainActivity : ComponentActivity() {
    private val startRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startRoute.value = intent?.getStringExtra(EXTRA_START_ROUTE)
        setContent {
            RomulusApp(initialRoute = startRoute.value)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        startRoute.value = intent.getStringExtra(EXTRA_START_ROUTE)
    }

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
        const val ROUTE_DOWNLOADS = "downloads"
    }
}
