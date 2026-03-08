package com.romulus.spikes.spike4

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Spike4MatrixInstrumentedTest {
    @Test
    fun runFullMatrix() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deviceSerial = InstrumentationRegistry.getArguments().getString("spike4_device_serial")
        val session = Spike4MatrixRunner(
            appContext = instrumentation.targetContext,
            runtimeConfig = Spike4RuntimeConfigLoader.load(instrumentation.context.assets),
            selectedDeviceSerial = deviceSerial,
        ).runFullMatrix()

        val failures = session.runs.filter { it.status == RunStatusCode.FAILED || it.status == RunStatusCode.BLOCKED }
        assertTrue(
            buildString {
                append("Unexpected Spike 4 failures")
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
