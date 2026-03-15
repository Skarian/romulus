package com.romulus.mobile.downloads.config

data class DownloadSettingsDraft(val outputDirectoryUri: String, val maxConcurrency: Int)

data class DownloadSettingsState(val outputDirectoryUri: String?, val maxConcurrency: Int)

data class DownloadSettingsReadiness(val isUsable: Boolean, val brokenReason: String?)
