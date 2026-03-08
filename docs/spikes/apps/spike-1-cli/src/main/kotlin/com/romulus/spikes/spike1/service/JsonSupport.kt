package com.romulus.spikes.spike1.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
fun spikeJson(): Json {
    return Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
    }
}
