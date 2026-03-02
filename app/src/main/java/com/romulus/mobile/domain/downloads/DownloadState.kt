package com.romulus.mobile.domain.downloads

enum class DownloadState {
    QUEUED,
    RESOLVING_LINK,
    RUNNING,
    PAUSED,
    RETRY_SCHEDULED,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED

    val isActive: Boolean
        get() = this == QUEUED ||
            this == RESOLVING_LINK ||
            this == RUNNING ||
            this == PAUSED ||
            this == RETRY_SCHEDULED ||
            this == RETRYING
}
