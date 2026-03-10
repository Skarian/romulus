package com.romulus.mobile.ui.downloads

import androidx.lifecycle.SavedStateHandle
import com.romulus.mobile.downloads.DownloadsFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @Test
    fun clearHistoryFailureKeepsDialogOpenWithError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = DownloadsViewModel(
                downloadsFacade = DownloadsFacade(),
                savedStateHandle = SavedStateHandle()
            )

            viewModel.openClearHistory()
            viewModel.confirmClearHistory(includeFailed = true)
            advanceUntilIdle()

            assertEquals(
                ClearHistoryDialogState(
                    includeFailed = true,
                    errorMessage = "DownloadsFacade is not wired yet"
                ),
                readMutableStateFlowValue<ClearHistoryDialogState?>(viewModel, "clearHistoryDialog")
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readMutableStateFlowValue(instance: Any, fieldName: String): T {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        val flow = field.get(instance) as MutableStateFlow<T>
        return flow.value
    }
}
