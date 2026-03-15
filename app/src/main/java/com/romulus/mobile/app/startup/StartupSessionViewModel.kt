package com.romulus.mobile.app.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.romulus.mobile.app.shell.AppLaunchIntent
import com.romulus.mobile.app.shell.ShellRoute
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@JvmInline
value class ColdLaunchToken(val value: String) {
    companion object {
        fun create(): ColdLaunchToken = ColdLaunchToken(UUID.randomUUID().toString())
    }
}

data class StartupBootstrapRequest(
    val coldLaunchToken: ColdLaunchToken,
    val launchIntent: AppLaunchIntent?
)

data class StartupSessionState(val bootstrapping: Boolean, val routeDecision: StartupRouteDecision?)

class StartupSessionViewModel(private val startupBootstrapper: StartupBootstrapper) :
    ViewModel() {
    val coldLaunchToken: ColdLaunchToken = ColdLaunchToken.create()

    private val mutableState = MutableStateFlow(
        StartupSessionState(
            bootstrapping = false,
            routeDecision = null
        )
    )
    val state: StateFlow<StartupSessionState> = mutableState.asStateFlow()

    private var hasStarted = false

    fun startOnce(request: StartupBootstrapRequest) {
        if (hasStarted) {
            return
        }

        hasStarted = true
        viewModelScope.launch {
            mutableState.value = StartupSessionState(
                bootstrapping = true,
                routeDecision = null
            )
            val routeDecision = startupBootstrapper.bootstrap(request)
            mutableState.value = StartupSessionState(
                bootstrapping = false,
                routeDecision = routeDecision
            )
        }
    }

    fun completeSetup(initialRoute: ShellRoute) {
        val currentState = mutableState.value
        if (
            currentState.bootstrapping ||
            currentState.routeDecision != StartupRouteDecision.Setup
        ) {
            return
        }

        mutableState.value = currentState.copy(
            routeDecision = StartupRouteDecision.Shell(initialRoute = initialRoute)
        )
    }

    companion object {
        fun factory(startupBootstrapper: StartupBootstrapper): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    if (modelClass.isAssignableFrom(StartupSessionViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return StartupSessionViewModel(startupBootstrapper) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
