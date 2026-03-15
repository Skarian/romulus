package com.romulus.mobile.app.shell

import com.romulus.mobile.source.snapshot.SnapshotId
import com.romulus.mobile.source.snapshot.SourceEntryId

sealed interface ShellRoute {
    data object Home : ShellRoute

    data class Files(
        val snapshotId: SnapshotId,
        val entryId: SourceEntryId,
        val entryDisplayName: String
    ) : ShellRoute

    data object Downloads : ShellRoute

    data object Settings : ShellRoute
}

data class AppLaunchIntent(val preferredRoute: ShellRoute?, val source: LaunchSource)

enum class LaunchSource {
    APP_ICON,
    NOTIFICATION,
    UNKNOWN
}
