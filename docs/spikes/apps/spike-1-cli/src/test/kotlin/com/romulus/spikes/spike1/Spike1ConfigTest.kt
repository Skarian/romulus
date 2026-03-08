package com.romulus.spikes.spike1

import com.romulus.spikes.spike1.config.Spike1Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class Spike1ConfigTest {
    @Test
    fun parsesOptionalKnownUncachedInputsWhenPresent() {
        val config = Spike1Config.from(
            mapOf(
                "RD_API_TOKEN" to "token",
                "SPIKE1_MAGNET" to "magnet:?xt=urn:btih:ABC123",
                "SPIKE1_ROOT_SELECTED_PATHS" to "/one.mkv",
                "SPIKE1_DIRECTORY_SCOPE" to "/Folder",
                "SPIKE1_DIRECTORY_SELECTED_PATHS" to "/Folder/two.zip",
                "SPIKE1_EXACT_ZIP_PATH" to "/Folder/two.zip",
                "SPIKE1_UNCACHED_MAGNET" to "magnet:?xt=urn:btih:DEF456",
                "SPIKE1_UNCACHED_SELECTED_PATH" to "Folder/three.iso",
            )
        )

        assertEquals("magnet:?xt=urn:btih:DEF456", config.uncachedMagnet)
        assertEquals("/Folder/three.iso", config.uncachedSelectedPath)
        assertEquals("/Folder/three.iso", config.requireKnownUncached().selectedPath)
    }

    @Test
    fun leavesKnownUncachedInputsNullWhenOmitted() {
        val config = Spike1Config.from(
            mapOf(
                "RD_API_TOKEN" to "token",
                "SPIKE1_MAGNET" to "magnet:?xt=urn:btih:ABC123",
                "SPIKE1_ROOT_SELECTED_PATHS" to "/one.mkv",
                "SPIKE1_DIRECTORY_SCOPE" to "/Folder",
                "SPIKE1_DIRECTORY_SELECTED_PATHS" to "/Folder/two.zip",
                "SPIKE1_EXACT_ZIP_PATH" to "/Folder/two.zip",
            )
        )

        assertNull(config.uncachedMagnet)
        assertNull(config.uncachedSelectedPath)
    }

    @Test
    fun rejectsHalfConfiguredKnownUncachedInputs() {
        assertFailsWith<IllegalArgumentException> {
            Spike1Config.from(
                mapOf(
                    "RD_API_TOKEN" to "token",
                    "SPIKE1_MAGNET" to "magnet:?xt=urn:btih:ABC123",
                    "SPIKE1_ROOT_SELECTED_PATHS" to "/one.mkv",
                    "SPIKE1_DIRECTORY_SCOPE" to "/Folder",
                    "SPIKE1_DIRECTORY_SELECTED_PATHS" to "/Folder/two.zip",
                    "SPIKE1_EXACT_ZIP_PATH" to "/Folder/two.zip",
                    "SPIKE1_UNCACHED_MAGNET" to "magnet:?xt=urn:btih:DEF456",
                )
            )
        }
    }
}
