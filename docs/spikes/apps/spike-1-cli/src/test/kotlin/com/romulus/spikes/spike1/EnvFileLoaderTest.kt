package com.romulus.spikes.spike1

import com.romulus.spikes.spike1.config.EnvFileLoader
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvFileLoaderTest {
    @Test
    fun loadsQuotedAndPlainValues() {
        val envFile = createTempFile(prefix = "spike1-", suffix = ".env")
        envFile.writeText(
            """
            # comment
            RD_API_TOKEN="token-value"
            SPIKE1_MAGNET=magnet:?xt=urn:btih:ABC123
            SPIKE1_ROOT_SELECTED_PATHS="/one.mkv,/two.mkv"
            """.trimIndent()
        )

        val values = EnvFileLoader().load(envFile)

        assertEquals("token-value", values["RD_API_TOKEN"])
        assertEquals("magnet:?xt=urn:btih:ABC123", values["SPIKE1_MAGNET"])
        assertEquals("/one.mkv,/two.mkv", values["SPIKE1_ROOT_SELECTED_PATHS"])
    }
}
