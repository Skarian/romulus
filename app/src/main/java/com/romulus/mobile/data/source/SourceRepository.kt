package com.romulus.mobile.data.source

import android.net.Uri
import com.romulus.mobile.core.validation.ValidationResult
import com.romulus.mobile.domain.source.RefreshResult
import com.romulus.mobile.domain.source.SourceSnapshot
import kotlinx.coroutines.flow.Flow

interface SourceRepository {
    fun observeActiveSnapshot(): Flow<SourceSnapshot>

    suspend fun loadActiveSnapshot(): SourceSnapshot

    suspend fun setLocalFileSource(uri: Uri): ValidationResult

    suspend fun setUrlSource(url: String): ValidationResult

    suspend fun refreshFromUrlOnColdLaunchIfNeeded(): RefreshResult
}
