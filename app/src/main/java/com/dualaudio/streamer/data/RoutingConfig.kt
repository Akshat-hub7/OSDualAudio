package com.dualaudio.streamer.data

/**
 * Configuration payload defining device addresses, volume scaling, and sync offsets.
 */
data class RoutingConfig(
    val deviceAddress1: String? = null,
    val deviceAddress2: String? = null,
    val volume1: Float = 1.0f,
    val volume2: Float = 1.0f,
    val delayMs1: Int = 0,
    val delayMs2: Int = 0,
    val mode: RoutingEngineMode = RoutingEngineMode.DUAL_AUDIOTRACK_FALLBACK,
    val sampleRate: Int = 48000,
    val channelConfig: Int = 2 // Stereo
)
