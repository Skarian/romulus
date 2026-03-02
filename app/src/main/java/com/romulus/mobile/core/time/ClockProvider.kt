package com.romulus.mobile.core.time

interface ClockProvider {
    fun nowEpochMillis(): Long
}
