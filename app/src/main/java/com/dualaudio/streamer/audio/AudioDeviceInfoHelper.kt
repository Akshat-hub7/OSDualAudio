package com.dualaudio.streamer.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

object AudioDeviceInfoHelper {

    private const val TAG = "AudioDeviceInfoHelper"

    /**
     * Checks whether an AudioDeviceInfo represents a Bluetooth audio sink.
     */
    fun isBluetoothDevice(deviceInfo: AudioDeviceInfo): Boolean {
        return when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_HEARING_AID -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    deviceInfo.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    deviceInfo.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    deviceInfo.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                } else {
                    false
                }
            }
        }
    }

    /**
     * Returns a list of all currently connected Bluetooth output devices known to AudioManager.
     */
    fun getConnectedBluetoothAudioDevices(audioManager: AudioManager): List<AudioDeviceInfo> {
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputDevices.filter { isBluetoothDevice(it) }
    }

    /**
     * Finds the AudioDeviceInfo matching the provided Bluetooth MAC address or device name.
     */
    fun findAudioDeviceInfo(
        audioManager: AudioManager,
        targetAddress: String?,
        targetName: String? = null
    ): AudioDeviceInfo? {
        if (targetAddress.isNullOrBlank() && targetName.isNullOrBlank()) return null

        val bluetoothDevices = getConnectedBluetoothAudioDevices(audioManager)

        // 1. First priority: exact match on hardware MAC address (API 28+)
        if (!targetAddress.isNullOrBlank()) {
            val addressMatch = bluetoothDevices.firstOrNull { deviceInfo ->
                deviceInfo.address.equals(targetAddress, ignoreCase = true)
            }
            if (addressMatch != null) {
                Log.d(TAG, "Matched AudioDeviceInfo by address: ${addressMatch.productName} ($targetAddress)")
                return addressMatch
            }
        }

        // 2. Second priority: match on product name if address is masked or unavailable
        if (!targetName.isNullOrBlank()) {
            val nameMatch = bluetoothDevices.firstOrNull { deviceInfo ->
                deviceInfo.productName.toString().equals(targetName, ignoreCase = true) ||
                deviceInfo.productName.toString().contains(targetName, ignoreCase = true)
            }
            if (nameMatch != null) {
                Log.d(TAG, "Matched AudioDeviceInfo by product name: ${nameMatch.productName}")
                return nameMatch
            }
        }

        Log.w(TAG, "Could not find matching AudioDeviceInfo for address: $targetAddress, name: $targetName")
        return null
    }
}

