package com.dualaudio.streamer.data

/**
 * Data model representing a discovered or bonded Bluetooth audio device.
 */
data class BluetoothDeviceModel(
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val deviceType: BluetoothDeviceType = BluetoothDeviceType.HEADSET,
    val batteryLevel: Int = -1,
    val isBonded: Boolean = true,
    val isLeAudioCapable: Boolean = false
)

/**
 * Categorization of Bluetooth audio devices for UI presentation and icon selection.
 */
enum class BluetoothDeviceType {
    HEADSET,
    EARBUDS,
    SPEAKER,
    LE_AUDIO_AURACAST,
    HEARING_AID,
    UNKNOWN
}

/**
 * State representing a headset assignment slot (Headset 1 or Headset 2).
 */
data class HeadsetSlot(
    val slotNumber: Int,
    val selectedDevice: BluetoothDeviceModel? = null,
    val volume: Float = 1.0f,          // 0.0f to 1.0f (or software gain)
    val syncDelayMs: Int = 0,          // 0ms to 500ms delay offset
    val isMuted: Boolean = false
)

/**
 * Operating mode of the dual audio routing engine.
 */
enum class RoutingEngineMode {
    /**
     * Secondary fallback mode for Android 10+:
     * Captures system audio via MediaProjection and routes duplicate PCM streams
     * to two concurrent AudioTrack instances mapped to each Bluetooth device.
     */
    DUAL_AUDIOTRACK_FALLBACK,

    /**
     * Primary route for Android 13+ (API 33+):
     * Uses BluetoothLeBroadcast (Auracast) to broadcast audio to multiple LE Audio receivers.
     */
    AURACAST_LE_AUDIO
}

/**
 * Lifecycle state of the background audio routing service.
 */
enum class RoutingServiceState {
    IDLE,
    STARTING,
    RUNNING_AUDIOTRACK,
    RUNNING_AURACAST,
    STOPPING,
    ERROR
}

