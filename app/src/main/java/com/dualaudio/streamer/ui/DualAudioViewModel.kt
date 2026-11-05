package com.dualaudio.streamer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dualaudio.streamer.bluetooth.BluetoothDeviceManager
import com.dualaudio.streamer.bluetooth.LeBroadcastManager
import com.dualaudio.streamer.data.BluetoothDeviceModel
import com.dualaudio.streamer.data.HeadsetSlot
import com.dualaudio.streamer.data.RoutingEngineMode
import com.dualaudio.streamer.data.RoutingServiceState
import com.dualaudio.streamer.service.AudioRoutingService
import com.dualaudio.streamer.util.PermissionHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main ViewModel coordinating Bluetooth discovery, Auracast capability check,
 * foreground service binding, and user UI interactions.
 */
class DualAudioViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DualAudioViewModel"
    }

    private val bluetoothDeviceManager = BluetoothDeviceManager(application, viewModelScope)
    private val leBroadcastManager = LeBroadcastManager(application)

    private val _uiState = MutableStateFlow(DualAudioUiState())
    val uiState: StateFlow<DualAudioUiState> = _uiState.asStateFlow()

    // Event asking Activity to launch MediaProjection permission dialog
    private val _requestMediaProjectionEvent = MutableSharedFlow<Unit>()
    val requestMediaProjectionEvent: SharedFlow<Unit> = _requestMediaProjectionEvent.asSharedFlow()

    // Service connection
    private var boundService: AudioRoutingService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioRoutingService.LocalBinder
            val routingService = binder?.getService() ?: return
            boundService = routingService
            isBound = true

            // Observe service state
            viewModelScope.launch {
                routingService.serviceState.collect { state ->
                    _uiState.update { it.copy(serviceState = state) }
                }
            }

            // Observe live audio amplitude for visualizer
            viewModelScope.launch {
                routingService.amplitudeFlow.collect { amp ->
                    _uiState.update { it.copy(liveAmplitude = amp) }
                }
            }

            Log.d(TAG, "AudioRoutingService connected to ViewModel")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isBound = false
            _uiState.update { it.copy(serviceState = RoutingServiceState.IDLE, liveAmplitude = 0f) }
            Log.d(TAG, "AudioRoutingService disconnected")
        }
    }

    init {
        checkPermissionsAndInit()
    }

    /**
     * Checks permissions and initializes Bluetooth and Auracast engines.
     */
    fun checkPermissionsAndInit() {
        val context = getApplication<Application>()
        val hasPerms = PermissionHelper.hasAllPermissions(context)
        _uiState.update { it.copy(hasRequiredPermissions = hasPerms) }

        if (hasPerms) {
            bluetoothDeviceManager.initialize()
            leBroadcastManager.initialize()

            // Collect paired & connected devices
            viewModelScope.launch {
                bluetoothDeviceManager.deviceList.collect { devices ->
                    _uiState.update { state ->
                        // Auto-assign first two connected devices if slots are currently unassigned
                        val slot1Dev = state.headsetSlot1.selectedDevice
                            ?: devices.firstOrNull { it.isConnected }
                            ?: devices.getOrNull(0)

                        val slot2Dev = state.headsetSlot2.selectedDevice
                            ?: devices.firstOrNull { it.isConnected && it.address != slot1Dev?.address }
                            ?: devices.firstOrNull { it.address != slot1Dev?.address }

                        state.copy(
                            pairedDevices = devices,
                            headsetSlot1 = state.headsetSlot1.copy(selectedDevice = slot1Dev),
                            headsetSlot2 = state.headsetSlot2.copy(selectedDevice = slot2Dev)
                        )
                    }
                }
            }

            // Collect Bluetooth enabled state
            viewModelScope.launch {
                bluetoothDeviceManager.isBluetoothEnabled.collect { isEnabled ->
                    _uiState.update { it.copy(isBluetoothEnabled = isEnabled) }
                }
            }

            // Collect Auracast support status
            viewModelScope.launch {
                leBroadcastManager.isSupported.collect { supported ->
                    _uiState.update { state ->
                        state.copy(
                            isAuracastSupported = supported,
                            routingMode = if (supported) RoutingEngineMode.AURACAST_LE_AUDIO else RoutingEngineMode.DUAL_AUDIOTRACK_FALLBACK
                        )
                    }
                }
            }

            viewModelScope.launch {
                leBroadcastManager.broadcastStatusText.collect { msg ->
                    _uiState.update { it.copy(auracastStatusMessage = msg) }
                }
            }

            // Bind to running service if already started
            bindRoutingService()
        }
    }

    private fun bindRoutingService() {
        val intent = Intent(getApplication(), AudioRoutingService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Manual refresh of Bluetooth devices.
     */
    fun refreshBluetoothDevices() {
        bluetoothDeviceManager.refreshDevices()
    }

    /**
     * Toggles between Auracast LE Audio and Dual AudioTrack fallback mode.
     */
    fun setRoutingMode(mode: RoutingEngineMode) {
        _uiState.update { it.copy(routingMode = mode) }
    }

    /**
     * Selects device for Headset Slot 1.
     */
    fun selectDeviceSlot1(device: BluetoothDeviceModel?) {
        _uiState.update { it.copy(headsetSlot1 = it.headsetSlot1.copy(selectedDevice = device)) }
        boundService?.updateDevices(device?.address, _uiState.value.headsetSlot2.selectedDevice?.address)
    }

    /**
     * Selects device for Headset Slot 2.
     */
    fun selectDeviceSlot2(device: BluetoothDeviceModel?) {
        _uiState.update { it.copy(headsetSlot2 = it.headsetSlot2.copy(selectedDevice = device)) }
        boundService?.updateDevices(_uiState.value.headsetSlot1.selectedDevice?.address, device?.address)
    }

    /**
     * Updates volume for Headset Slot 1.
     */
    fun updateVolumeSlot1(volume: Float) {
        _uiState.update { it.copy(headsetSlot1 = it.headsetSlot1.copy(volume = volume)) }
        boundService?.updateVolumes(
            if (_uiState.value.headsetSlot1.isMuted) 0f else volume,
            if (_uiState.value.headsetSlot2.isMuted) 0f else _uiState.value.headsetSlot2.volume
        )
    }

    /**
     * Updates volume for Headset Slot 2.
     */
    fun updateVolumeSlot2(volume: Float) {
        _uiState.update { it.copy(headsetSlot2 = it.headsetSlot2.copy(volume = volume)) }
        boundService?.updateVolumes(
            if (_uiState.value.headsetSlot1.isMuted) 0f else _uiState.value.headsetSlot1.volume,
            if (_uiState.value.headsetSlot2.isMuted) 0f else volume
        )
    }

    /**
     * Toggles mute on Slot 1.
     */
    fun toggleMuteSlot1(muted: Boolean) {
        _uiState.update { it.copy(headsetSlot1 = it.headsetSlot1.copy(isMuted = muted)) }
        boundService?.updateVolumes(
            if (muted) 0f else _uiState.value.headsetSlot1.volume,
            if (_uiState.value.headsetSlot2.isMuted) 0f else _uiState.value.headsetSlot2.volume
        )
    }

    /**
     * Toggles mute on Slot 2.
     */
    fun toggleMuteSlot2(muted: Boolean) {
        _uiState.update { it.copy(headsetSlot2 = it.headsetSlot2.copy(isMuted = muted)) }
        boundService?.updateVolumes(
            if (_uiState.value.headsetSlot1.isMuted) 0f else _uiState.value.headsetSlot1.volume,
            if (muted) 0f else _uiState.value.headsetSlot2.volume
        )
    }

    /**
     * Updates sync delay offset for Headset Slot 1.
     */
    fun updateDelaySlot1(delayMs: Int) {
        _uiState.update { it.copy(headsetSlot1 = it.headsetSlot1.copy(syncDelayMs = delayMs)) }
        boundService?.updateDelays(delayMs, _uiState.value.headsetSlot2.syncDelayMs)
    }

    /**
     * Updates sync delay offset for Headset Slot 2.
     */
    fun updateDelaySlot2(delayMs: Int) {
        _uiState.update { it.copy(headsetSlot2 = it.headsetSlot2.copy(syncDelayMs = delayMs)) }
        boundService?.updateDelays(_uiState.value.headsetSlot1.syncDelayMs, delayMs)
    }

    /**
     * User clicks Start Dual Stream button.
     * If using Auracast, starts directly via foreground service.
     * If using Dual AudioTrack fallback, requests MediaProjection token from MainActivity.
     */
    fun onStartDualAudioClicked() {
        val state = _uiState.value
        if (state.routingMode == RoutingEngineMode.AURACAST_LE_AUDIO && state.isAuracastSupported) {
            startServiceDirectly(null, 0)
        } else {
            viewModelScope.launch {
                _requestMediaProjectionEvent.emit(Unit)
            }
        }
    }

    /**
     * Called when MainActivity successfully receives MediaProjection capture permission.
     */
    fun onMediaProjectionPermissionGranted(resultCode: Int, data: Intent) {
        startServiceDirectly(data, resultCode)
    }

    private fun startServiceDirectly(projectionIntent: Intent?, resultCode: Int) {
        val context = getApplication<Application>()
        val state = _uiState.value

        val serviceIntent = Intent(context, AudioRoutingService::class.java).apply {
            action = AudioRoutingService.ACTION_START_DUAL_STREAM
            putExtra(AudioRoutingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(AudioRoutingService.EXTRA_RESULT_DATA, projectionIntent)
            putExtra(AudioRoutingService.EXTRA_ROUTING_MODE, state.routingMode.name)
            putExtra(AudioRoutingService.EXTRA_DEVICE_ADDRESS_1, state.headsetSlot1.selectedDevice?.address)
            putExtra(AudioRoutingService.EXTRA_DEVICE_ADDRESS_2, state.headsetSlot2.selectedDevice?.address)
            putExtra(AudioRoutingService.EXTRA_VOLUME_1, if (state.headsetSlot1.isMuted) 0f else state.headsetSlot1.volume)
            putExtra(AudioRoutingService.EXTRA_VOLUME_2, if (state.headsetSlot2.isMuted) 0f else state.headsetSlot2.volume)
            putExtra(AudioRoutingService.EXTRA_DELAY_1, state.headsetSlot1.syncDelayMs)
            putExtra(AudioRoutingService.EXTRA_DELAY_2, state.headsetSlot2.syncDelayMs)
        }

        context.startForegroundService(serviceIntent)
        bindRoutingService()
    }

    /**
     * Stops current routing session.
     */
    fun onStopDualAudioClicked() {
        boundService?.stopAudioSession()
        val context = getApplication<Application>()
        val stopIntent = Intent(context, AudioRoutingService::class.java).apply {
            action = AudioRoutingService.ACTION_STOP_SERVICE
        }
        context.startService(stopIntent)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothDeviceManager.release()
        leBroadcastManager.release()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
    }
}

