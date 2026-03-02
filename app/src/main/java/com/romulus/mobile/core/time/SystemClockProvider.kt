package com.romulus.mobile.core.time

class SystemClockProvider : ClockProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
