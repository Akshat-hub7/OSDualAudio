package com.dualaudio.streamer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Primary Route: Implements Bluetooth LE Audio Broadcast / Auracast APIs
 * for Android 13+ (API level 33+) devices using runtime reflection to interface with
 * platform LE Audio Broadcast interfaces without requiring hidden system SDK stubs.
 */
class LeBroadcastManager(private val context: Context) {

    companion object {
        private const val TAG = "LeBroadcastManager"
        private const val LE_AUDIO_BROADCAST_PROFILE = 26 // BluetoothProfile.LE_AUDIO_BROADCAST
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Auracast proxy object
    private var leBroadcastProxy: Any? = null
    private var currentBroadcastId: Int? = null

    private val _isSupported = MutableStateFlow(false)
    val isSupported: StateFlow<Boolean> = _isSupported.asStateFlow()

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _broadcastStatusText = MutableStateFlow("Initializing Auracast...")
    val broadcastStatusText: StateFlow<String> = _broadcastStatusText.asStateFlow()

    private val directExecutor = Executor { command -> command.run() }
    private var dynamicCallbackProxy: Any? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == LE_AUDIO_BROADCAST_PROFILE && proxy != null) {
                leBroadcastProxy = proxy
                registerBroadcastCallback(proxy)
                Log.i(TAG, "BluetoothLeBroadcast proxy connected successfully")
                _broadcastStatusText.value = "Auracast ready to broadcast"
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == LE_AUDIO_BROADCAST_PROFILE) {
                leBroadcastProxy = null
                _isBroadcasting.value = false
                _broadcastStatusText.value = "Auracast service disconnected"
                Log.w(TAG, "BluetoothLeBroadcast proxy disconnected")
            }
        }
    }

    /**
     * Initializes Auracast capability check and binds profile proxy if supported.
     */
    fun initialize() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _isSupported.value = false
            _broadcastStatusText.value = "Auracast requires Android 13+ (API 33). Using Dual AudioTrack Fallback."
            Log.i(TAG, "LE Audio Broadcast not supported on Android < 13")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            _isSupported.value = false
            _broadcastStatusText.value = "Bluetooth not available"
            return
        }

        checkAuracastHardwareSupport(adapter)
    }

    @SuppressLint("MissingPermission")
    private fun checkAuracastHardwareSupport(adapter: BluetoothAdapter) {
        try {
            // Check adapter.isLeAudioBroadcastSourceSupported() via reflection
            val method = adapter.javaClass.getMethod("isLeAudioBroadcastSourceSupported")
            val result = method.invoke(adapter) as? Int
            val isHardwareSupported = (result == BluetoothStatusCodes.FEATURE_SUPPORTED)
            _isSupported.value = isHardwareSupported

            if (isHardwareSupported) {
                adapter.getProfileProxy(context, profileListener, LE_AUDIO_BROADCAST_PROFILE)
                _broadcastStatusText.value = "Auracast LE Audio supported on this device"
                Log.i(TAG, "Auracast LE Audio broadcast source supported!")
            } else {
                _broadcastStatusText.value = "Hardware does not support Auracast. Dual AudioTrack fallback will be used."
                Log.w(TAG, "Auracast LE Audio source not supported by chipset (status: $result)")
            }
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "isLeAudioBroadcastSourceSupported method not present on this device ROM")
            _isSupported.value = false
            _broadcastStatusText.value = "Auracast not supported on this ROM"
        } catch (e: Exception) {
            Log.e(TAG, "Error checking LE Audio support", e)
            _isSupported.value = false
            _broadcastStatusText.value = "Auracast error: ${e.localizedMessage}"
        }
    }

    private fun registerBroadcastCallback(proxy: Any) {
        try {
            val callbackClass = Class.forName("android.bluetooth.BluetoothLeBroadcast\$Callback")
            val invocationHandler = InvocationHandler { _, method, args ->
                when (method.name) {
                    "onBroadcastStarted" -> {
                        val broadcastId = args?.getOrNull(1) as? Int
                        currentBroadcastId = broadcastId
                        _isBroadcasting.value = true
                        _broadcastStatusText.value = "Auracast broadcasting active (ID: $broadcastId)"
                        Log.i(TAG, "Auracast broadcast started: ID=$broadcastId")
                        null
                    }
                    "onBroadcastStartFailed" -> {
                        val reason = args?.getOrNull(0) as? Int
                        _isBroadcasting.value = false
                        _broadcastStatusText.value = "Auracast failed to start (code: $reason)"
                        Log.e(TAG, "Auracast broadcast start failed: $reason")
                        null
                    }
                    "onBroadcastStopped" -> {
                        currentBroadcastId = null
                        _isBroadcasting.value = false
                        _broadcastStatusText.value = "Auracast broadcast stopped"
                        Log.i(TAG, "Auracast broadcast stopped")
                        null
                    }
                    "onBroadcastStopFailed" -> {
                        Log.e(TAG, "Auracast broadcast stop failed")
                        null
                    }
                    else -> null
                }
            }

            val dynamicProxy = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
                invocationHandler
            )
            dynamicCallbackProxy = dynamicProxy

            val registerMethod = proxy.javaClass.getMethod(
                "registerCallback",
                Executor::class.java,
                callbackClass
            )
            registerMethod.invoke(proxy, directExecutor, dynamicProxy)
            Log.d(TAG, "Registered dynamic BluetoothLeBroadcast callback")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register dynamic BluetoothLeBroadcast callback", e)
        }
    }

    /**
     * Starts an Auracast LE Audio broadcast.
     *
     * @param broadcastName Human-readable name for earphones to discover
     * @param broadcastCode Optional encryption PIN / passkey (null for open broadcast)
     */
    fun startBroadcast(broadcastName: String = "DualCast Auracast", broadcastCode: ByteArray? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.w(TAG, "Cannot start Auracast: Android version < 13")
            return false
        }

        val proxy = leBroadcastProxy
        if (proxy == null) {
            Log.e(TAG, "BluetoothLeBroadcast proxy is not connected")
            _broadcastStatusText.value = "Auracast proxy unavailable"
            return false
        }

        return try {
            val metadataClass = Class.forName("android.bluetooth.BluetoothLeAudioContentMetadata")
            val builderClass = Class.forName("android.bluetooth.BluetoothLeAudioContentMetadata\$Builder")

            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("setProgramInfo", String::class.java).invoke(builder, broadcastName)
            builderClass.getMethod("setLanguage", String::class.java).invoke(builder, "eng")
            val metadata = builderClass.getMethod("build").invoke(builder)

            val startMethod = proxy.javaClass.getMethod("startBroadcast", metadataClass, ByteArray::class.java)
            startMethod.invoke(proxy, metadata, broadcastCode)

            _broadcastStatusText.value = "Starting Auracast broadcast..."
            Log.i(TAG, "Requested startBroadcast: $broadcastName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Auracast broadcast", e)
            _broadcastStatusText.value = "Error starting Auracast: ${e.localizedMessage}"
            false
        }
    }

    /**
     * Stops the active Auracast LE Audio broadcast.
     */
    fun stopBroadcast(): Boolean {
        val proxy = leBroadcastProxy ?: return false
        val broadcastId = currentBroadcastId ?: return false

        return try {
            val stopMethod = proxy.javaClass.getMethod("stopBroadcast", Int::class.javaPrimitiveType)
            stopMethod.invoke(proxy, broadcastId)
            _broadcastStatusText.value = "Stopping Auracast broadcast..."
            Log.i(TAG, "Requested stopBroadcast ID: $broadcastId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Auracast broadcast", e)
            false
        }
    }

    /**
     * Releases proxies and callbacks.
     */
    fun release() {
        try {
            stopBroadcast()
            val proxy = leBroadcastProxy
            val dynamicCallback = dynamicCallbackProxy
            if (proxy != null && dynamicCallback != null) {
                val callbackClass = Class.forName("android.bluetooth.BluetoothLeBroadcast\$Callback")
                val unregisterMethod = proxy.javaClass.getMethod("unregisterCallback", callbackClass)
                unregisterMethod.invoke(proxy, dynamicCallback)
            }
        } catch (_: Exception) {}

        val adapter = bluetoothAdapter
        val proxy = leBroadcastProxy as? BluetoothProfile
        if (adapter != null && proxy != null) {
            adapter.closeProfileProxy(LE_AUDIO_BROADCAST_PROFILE, proxy)
        }
        leBroadcastProxy = null
        dynamicCallbackProxy = null
        currentBroadcastId = null
    }
}
