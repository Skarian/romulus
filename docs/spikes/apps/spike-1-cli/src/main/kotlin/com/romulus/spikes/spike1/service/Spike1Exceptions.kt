package com.romulus.spikes.spike1.service

import com.romulus.spikes.spike1.model.PollingSummary
import com.romulus.spikes.spike1.model.TorrentInfoDto

class Spike1CliException(message: String) : IllegalStateException(message)

class LinkReadyTimeoutException(
    message: String,
    val lastInfo: TorrentInfoDto?,
    val pollingSummary: PollingSummary,
) : IllegalStateException(message)

class RealDebridApiException(
    message: String,
    val httpStatus: Int,
    val providerCode: Int? = null,
    val providerMessage: String? = null,
    val endpoint: String,
    val method: String,
) : RuntimeException(message)
