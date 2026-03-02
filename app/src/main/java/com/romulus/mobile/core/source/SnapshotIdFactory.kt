package com.romulus.mobile.core.source

import java.util.UUID

object SnapshotIdFactory {
    fun newId(nowEpochMs: Long): String {
        return "snapshot-${nowEpochMs}-${UUID.randomUUID()}"
    }
}
