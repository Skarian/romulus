package com.romulus.spikes.spike5

import com.romulus.spikes.spike5.config.EnvFileLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvFileLoaderTest {
    @Test
    fun `loads simple env file values`() {
        val envFile = kotlin.io.path.createTempFile(prefix = "spike5-", suffix = ".env")
        envFile.toFile().writeText(
            """
            RD_API_TOKEN=token
            SPIKE5_MAGNET=magnet:?xt=urn:btih:abc
            """.trimIndent()
        )

        val values = EnvFileLoader().load(envFile)

        assertEquals("token", values["RD_API_TOKEN"])
        assertEquals("magnet:?xt=urn:btih:abc", values["SPIKE5_MAGNET"])
    }

    @Test
    fun `rejects malformed env lines`() {
        val envFile = kotlin.io.path.createTempFile(prefix = "spike5-bad-", suffix = ".env")
        envFile.toFile().writeText("not valid")

        assertFailsWith<IllegalArgumentException> {
            EnvFileLoader().load(envFile)
        }
    }
}
