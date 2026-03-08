package com.romulus.spikes.spike3.http

import com.romulus.spikes.spike3.model.HttpTraceEvent

class HttpTraceRecorder {
    private val events = mutableListOf<HttpTraceEvent>()

    fun record(event: HttpTraceEvent) {
        synchronized(events) {
            events += event
        }
    }

    fun snapshot(): List<HttpTraceEvent> {
        return synchronized(events) {
            events.toList()
        }
    }
}
