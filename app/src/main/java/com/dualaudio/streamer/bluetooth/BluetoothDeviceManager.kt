package com.dualaudio.streamer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.dualaudio.streamer.audio.AudioDeviceInfoHelper
import com.dualaudio.streamer.data.BluetoothDeviceModel
import com.dualaudio.streamer.data.BluetoothDeviceType
import com.dualaudio.streamer.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Bluetooth device discovery, connection state monitoring,
 * and profile proxy bindings (A2DP, Headset, LE Audio).
 */
class BluetoothDeviceManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "BluetoothDeviceManager"
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Profile proxies
    private var a2dpProfile: BluetoothA2dp? = null
    private var headsetProfile: BluetoothHeadset? = null
    private var leAudioProfile: BluetoothProfile? = null

    private val _deviceList = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val deviceList: StateFlow<List<BluetoothDeviceModel>> = _deviceList.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    // BroadcastReceiver for ACL and A2DP connection events
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    refreshDevices()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    refreshDevices()
                }
            }
        }
    }

    // AudioManager AudioDeviceCallback for real-time audio routing changes
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            refreshDevices()
        }
    }

    // Profile listener for A2DP, Headset, and LE Audio
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            when (profile) {
                BluetoothProfile.A2DP -> {
                    a2dpProfile = proxy as? BluetoothA2dp
                    Log.d(TAG, "Bluetooth A2DP profile proxy connected")
                }
                BluetoothProfile.HEADSET -> {
                    headsetProfile = proxy as? BluetoothHeadset
                    Log.d(TAG, "Bluetooth Headset profile proxy connected")
                }
                22 /* BluetoothProfile.LE_AUDIO */ -> {
                    leAudioProfile = proxy
                    Log.d(TAG, "Bluetooth LE Audio profile proxy connected")
                }
            }
            refreshDevices()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = null
                BluetoothProfile.HEADSET -> headsetProfile = null
                22 -> leAudioProfile = null
            }
            refreshDevices()
        }
    }

    /**
     * Initializes profile proxies and registers event listeners.
     */
    fun initialize() {
        if (!PermissionHelper.hasBluetoothConnectPermission(context)) {
            Log.w(TAG, "Bluetooth permissions not yet granted; deferring init")
            return
        }

        val adapter = bluetoothAdapter ?: return

        // Bind A2DP and Headset profiles
        adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        adapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)

        // Bind LE Audio profile if supported (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // Profile ID 22 corresponds to BluetoothProfile.LE_AUDIO
                adapter.getProfileProxy(context, profileListener, 22)
            } catch (e: Exception) {
                Log.d(TAG, "LE Audio profile not supported on this device: ${e.message}")
            }
        }

        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)

        // Register AudioManager callback
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        refreshDevices()
    }

    /**
     * Queries paired devices, checks connection states against active profiles and AudioManager,
     * and updates the StateFlow.
     */
    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        if (!PermissionHelper.hasBluetoothConnectPermission(context)) {
            Log.d(TAG, "Cannot refresh devices: Missing BLUETOOTH_CONNECT permission")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _deviceList.value = emptyList()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val bonded = adapter.bondedDevices ?: emptySet()
                val connectedAudioSinks = AudioDeviceInfoHelper.getConnectedBluetoothAudioDevices(audioManager)

                val models = bonded.map { device ->
                    val isConnectedToA2dp = a2dpProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
                    val isConnectedToHeadset = headsetProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
                    val isConnectedToAudioSink = connectedAudioSinks.any { sink ->
                        sink.address.equals(device.address, ignoreCase = true) ||
                        sink.productName.toString().equals(device.name, ignoreCase = true)
                    }

                    val isConnected = isConnectedToA2dp || isConnectedToHeadset || isConnectedToAudioSink

                    // Infer device type from BluetoothClass and name
                    val deviceType = resolveDeviceType(device)

                    BluetoothDeviceModel(
                        name = device.name ?: "Unknown Device",
                        address = device.address,
                        isConnected = isConnected,
                        deviceType = deviceType,
                        isBonded = true,
                        isLeAudioCapable = (device.type == BluetoothDevice.DEVICE_TYPE_DUAL || device.type == BluetoothDevice.DEVICE_TYPE_LE)
                    )
                }.sortedWith(compareByDescending<BluetoothDeviceModel> { it.isConnected }.thenBy { it.name })

                _deviceList.value = models
                Log.d(TAG, "Refreshed devices: found ${models.size} paired devices (${models.count { it.isConnected }} connected)")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing Bluetooth devices", e)
            }
        }
    }

    /**
     * Resolves human-friendly BluetoothDeviceType from BluetoothClass.
     */
    @SuppressLint("MissingPermission")
    private fun resolveDeviceType(device: BluetoothDevice): BluetoothDeviceType {
        val btClass = device.bluetoothClass ?: return BluetoothDeviceType.HEADSET
        val nameLower = (device.name ?: "").lowercase()

        if (nameLower.contains("buds") || nameLower.contains("airpods") || nameLower.contains("earphone")) {
            return BluetoothDeviceType.EARBUDS
        }
        if (nameLower.contains("speaker") || nameLower.contains("soundbar")) {
            return BluetoothDeviceType.SPEAKER
        }

        return when (btClass.deviceClass) {
            android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> BluetoothDeviceType.HEADSET
            android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> BluetoothDeviceType.SPEAKER
            else -> BluetoothDeviceType.HEADSET
        }
    }

    /**
     * Cleans up receivers and profile proxies.
     */
    fun release() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}

        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {}

        val adapter = bluetoothAdapter
        if (adapter != null) {
            a2dpProfile?.let { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headsetProfile?.let { adapter.closeProfileProxy(BluetoothProfile.HEADSET, it) }
            leAudioProfile?.let { adapter.closeProfileProxy(22, it) }
        }
        a2dpProfile = null
        headsetProfile = null
        leAudioProfile = null
    }
}

