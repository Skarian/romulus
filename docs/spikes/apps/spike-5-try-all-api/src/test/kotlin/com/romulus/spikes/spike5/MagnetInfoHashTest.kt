package com.romulus.spikes.spike5

import com.romulus.spikes.spike5.service.MagnetInfoHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MagnetInfoHashTest {
    @Test
    fun `extracts hex info hash`() {
        assertEquals(
            "0123456789abcdef0123456789abcdef01234567",
            MagnetInfoHash.extract("magnet:?xt=urn:btih:0123456789ABCDEF0123456789ABCDEF01234567"),
        )
    }

    @Test
    fun `returns null when xt is missing`() {
        assertNull(MagnetInfoHash.extract("magnet:?dn=missing"))
    }
}
