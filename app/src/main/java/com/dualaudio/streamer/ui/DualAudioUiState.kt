package com.dualaudio.streamer.ui

import com.dualaudio.streamer.data.BluetoothDeviceModel
import com.dualaudio.streamer.data.HeadsetSlot
import com.dualaudio.streamer.data.RoutingEngineMode
import com.dualaudio.streamer.data.RoutingServiceState

/**
 * UI State container for Dual Audio Streamer.
 */
data class DualAudioUiState(
    val pairedDevices: List<BluetoothDeviceModel> = emptyList(),
    val isBluetoothEnabled: Boolean = true,
    val headsetSlot1: HeadsetSlot = HeadsetSlot(slotNumber = 1, volume = 0.9f, syncDelayMs = 0),
    val headsetSlot2: HeadsetSlot = HeadsetSlot(slotNumber = 2, volume = 0.9f, syncDelayMs = 0),
    val routingMode: RoutingEngineMode = RoutingEngineMode.DUAL_AUDIOTRACK_FALLBACK,
    val isAuracastSupported: Boolean = false,
    val auracastStatusMessage: String = "Auracast status check pending...",
    val serviceState: RoutingServiceState = RoutingServiceState.IDLE,
    val liveAmplitude: Float = 0f,
    val hasRequiredPermissions: Boolean = false,
    val errorMessage: String? = null
)

