package com.romulus.mobile.data.downloads.local

import androidx.room.TypeConverter
import com.romulus.mobile.domain.downloads.DownloadState

class DownloadStateConverter {
    @TypeConverter
    fun toState(value: String): DownloadState = DownloadState.valueOf(value)

    @TypeConverter
    fun fromState(state: DownloadState): String = state.name
}
