package com.romulus.mobile.data.settings

import com.romulus.mobile.domain.source.SourceMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun configurationValidityRequiresApiSourceAndDirectory() {
        val empty = AppSettings()
        val complete = AppSettings(
            encryptedApiKey = "encrypted",
            sourceMode = SourceMode.URL,
            sourceValue = "https://example.com/source.json",
            sourceSnapshotId = "snapshot-1",
            downloadDirectoryUri = "content://tree"
        )

        assertFalse(empty.isConfigurationValid)
        assertTrue(complete.isConfigurationValid)
    }
}
