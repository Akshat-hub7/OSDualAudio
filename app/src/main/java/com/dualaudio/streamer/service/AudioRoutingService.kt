package com.dualaudio.streamer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.dualaudio.streamer.audio.AudioCaptureEngine
import com.dualaudio.streamer.bluetooth.LeBroadcastManager
import com.dualaudio.streamer.data.RoutingConfig
import com.dualaudio.streamer.data.RoutingEngineMode
import com.dualaudio.streamer.data.RoutingServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android Foreground Service maintaining the dual-device audio routing session.
 * Keeps running when screen turns off using a Partial WakeLock and high-priority audio pipeline.
 */
class AudioRoutingService : Service() {

    companion object {
        private const val TAG = "AudioRoutingService"

        const val ACTION_START_DUAL_STREAM = "com.dualaudio.streamer.action.START_DUAL_STREAM"
        const val ACTION_STOP_SERVICE = "com.dualaudio.streamer.action.STOP_SERVICE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_DEVICE_ADDRESS_1 = "extra_device_address_1"
        const val EXTRA_DEVICE_ADDRESS_2 = "extra_device_address_2"
        const val EXTRA_VOLUME_1 = "extra_volume_1"
        const val EXTRA_VOLUME_2 = "extra_volume_2"
        const val EXTRA_DELAY_1 = "extra_delay_1"
        const val EXTRA_DELAY_2 = "extra_delay_2"
        const val EXTRA_ROUTING_MODE = "extra_routing_mode"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val localBinder = LocalBinder()

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var audioCaptureEngine: AudioCaptureEngine? = null
    private var leBroadcastManager: LeBroadcastManager? = null

    // State flows
    private val _serviceState = MutableStateFlow(RoutingServiceState.IDLE)
    val serviceState: StateFlow<RoutingServiceState> = _serviceState.asStateFlow()

    private val _amplitudeFlow = MutableStateFlow(0f)
    val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    private var currentConfig = RoutingConfig()

    inner class LocalBinder : Binder() {
        fun getService(): AudioRoutingService = this@AudioRoutingService
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        leBroadcastManager = LeBroadcastManager(this).apply {
            initialize()
        }

        // Acquire WakeLock to keep audio processing alive when device screen is turned off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DualCast:AudioRoutingWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        Log.d(TAG, "AudioRoutingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_SERVICE) {
            stopAudioSession()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_DUAL_STREAM) {
            // Android 14+ requires starting foreground immediately before getMediaProjection()
            startForegroundServiceWithNotification(
                title = "Dual Bluetooth Audio Routing",
                content = "Initializing audio capture pipeline..."
            )

            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            val modeName = intent.getStringExtra(EXTRA_ROUTING_MODE) ?: RoutingEngineMode.DUAL_AUDIOTRACK_FALLBACK.name
            val mode = RoutingEngineMode.valueOf(modeName)

            currentConfig = RoutingConfig(
                deviceAddress1 = intent.getStringExtra(EXTRA_DEVICE_ADDRESS_1),
                deviceAddress2 = intent.getStringExtra(EXTRA_DEVICE_ADDRESS_2),
                volume1 = intent.getFloatExtra(EXTRA_VOLUME_1, 1.0f),
                volume2 = intent.getFloatExtra(EXTRA_VOLUME_2, 1.0f),
                delayMs1 = intent.getIntExtra(EXTRA_DELAY_1, 0),
                delayMs2 = intent.getIntExtra(EXTRA_DELAY_2, 0),
                mode = mode
            )

            serviceScope.launch {
                startAudioSession(resultCode, resultData, currentConfig)
            }
        }

        return START_STICKY
    }

    /**
     * Enters foreground mode with appropriate foregroundServiceType for Android 14 (API 34).
     */
    private fun startForegroundServiceWithNotification(title: String, content: String) {
        val notification = NotificationHelper.buildForegroundNotification(
            this,
            title,
            content
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            startForeground(NotificationHelper.NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        wakeLock?.acquire(6 * 60 * 60 * 1000L) // 6 hours safety timeout
    }

    /**
     * Initializes and starts audio capture and routing.
     */
    private fun startAudioSession(resultCode: Int, resultData: Intent?, config: RoutingConfig) {
        _serviceState.value = RoutingServiceState.STARTING

        if (config.mode == RoutingEngineMode.AURACAST_LE_AUDIO) {
            val broadcastMgr = leBroadcastManager
            if (broadcastMgr != null && broadcastMgr.isSupported.value) {
                val started = broadcastMgr.startBroadcast("DualCast Auracast")
                if (started) {
                    _serviceState.value = RoutingServiceState.RUNNING_AURACAST
                    startForegroundServiceWithNotification(
                        title = "DualCast: Auracast Broadcasting",
                        content = "Broadcasting audio via LE Audio Auracast to multiple receivers"
                    )
                    return
                }
            }
            Log.w(TAG, "Auracast failed or unsupported. Auto-falling back to Dual AudioTrack mode.")
        }

        // Secondary Fallback: Internal Audio Capture + Dual AudioTrack
        if (resultCode == 0 || resultData == null) {
            Log.e(TAG, "Missing MediaProjection permission intent data for Dual AudioTrack engine")
            _serviceState.value = RoutingServiceState.ERROR
            return
        }

        try {
            val mp = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            if (mp == null) {
                Log.e(TAG, "Unable to obtain MediaProjection instance")
                _serviceState.value = RoutingServiceState.ERROR
                return
            }
            mediaProjection = mp

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioCaptureEngine?.stop()
            val engine = AudioCaptureEngine(
                context = this,
                mediaProjection = mp,
                audioManager = audioManager,
                config = config,
                onAmplitudeChanged = { rms ->
                    _amplitudeFlow.value = rms
                }
            )

            val success = engine.start()
            if (success) {
                audioCaptureEngine = engine
                _serviceState.value = RoutingServiceState.RUNNING_AUDIOTRACK
                startForegroundServiceWithNotification(
                    title = "Dual Audio Routing Active",
                    content = "Streaming system audio to two Bluetooth earphones"
                )
            } else {
                Log.e(TAG, "Failed to start AudioCaptureEngine")
                _serviceState.value = RoutingServiceState.ERROR
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating AudioCaptureEngine", e)
            _serviceState.value = RoutingServiceState.ERROR
        }
    }

    /**
     * Dynamically updates volume for both channels from UI slider without interrupting playback.
     */
    fun updateVolumes(vol1: Float, vol2: Float) {
        currentConfig = currentConfig.copy(volume1 = vol1, volume2 = vol2)
        audioCaptureEngine?.updateVolumes(vol1, vol2)
    }

    /**
     * Dynamically updates sync delay offset in ms from UI slider.
     */
    fun updateDelays(delay1: Int, delay2: Int) {
        currentConfig = currentConfig.copy(delayMs1 = delay1, delayMs2 = delay2)
        audioCaptureEngine?.updateDelays(delay1, delay2)
    }

    /**
     * Updates target Bluetooth endpoints without restarting capture.
     */
    fun updateDevices(addr1: String?, addr2: String?) {
        currentConfig = currentConfig.copy(deviceAddress1 = addr1, deviceAddress2 = addr2)
        audioCaptureEngine?.updatePreferredDevices(addr1, addr2)
    }

    /**
     * Stops current routing session and releases media resources.
     */
    fun stopAudioSession() {
        _serviceState.value = RoutingServiceState.STOPPING

        audioCaptureEngine?.stop()
        audioCaptureEngine = null

        leBroadcastManager?.stopBroadcast()

        mediaProjection?.stop()
        mediaProjection = null

        _amplitudeFlow.value = 0f
        _serviceState.value = RoutingServiceState.IDLE

        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Audio routing session stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioSession()
        leBroadcastManager?.release()
        leBroadcastManager = null
        serviceScope.cancel()
        Log.d(TAG, "AudioRoutingService destroyed")
    }
}

