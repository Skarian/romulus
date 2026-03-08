package com.romulus.spikes.spike2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Spike2MatrixInstrumentedTest {
    @Test
    fun runFullMatrix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deviceSerial = InstrumentationRegistry.getArguments().getString("spike2_device_serial")
        val session = Spike2MatrixRunner(
            appContext = instrumentation.targetContext,
            fixtureAssetManager = instrumentation.context.assets,
            selectedDeviceSerial = deviceSerial,
        ).runFullMatrix()

        val failures = session.runs.filter { it.status == RunStatusCode.FAILED }
        assertTrue(
            buildString {
                append("Unexpected Spike 2 failures")
                if (failures.isNotEmpty()) {
                    append(':')
                    failures.forEach { failure ->
                        append("\n- ")
                        append(failure.runId)
                        append(": ")
                        append(failure.summary)
                    }
                }
            },
            failures.isEmpty(),
        )
    }
}
