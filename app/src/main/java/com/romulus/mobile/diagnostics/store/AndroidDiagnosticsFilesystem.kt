package com.romulus.mobile.diagnostics.store

import android.content.Context
import java.io.File

internal class AndroidDiagnosticsFilesystem(context: Context) : DiagnosticsFilesystem {
    override val diagnosticsRoot: File = File(context.filesDir, "diagnostics")

    override val backupRootParent: File = File(context.cacheDir, "diagnostics-backups")
}
