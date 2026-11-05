package com.dualaudio.streamer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.os.Process
import android.util.Log
import com.dualaudio.streamer.data.RoutingConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Secondary Fallback Audio Routing Engine:
 * Uses Android MediaProjection and AudioPlaybackCaptureConfiguration to capture
 * system audio at the OS level, duplicated into two concurrent AudioTrack instances
 * routed directly to two Bluetooth earphones via AudioTrack.setPreferredDevice().
 */
class AudioCaptureEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val audioManager: AudioManager,
    private var config: RoutingConfig,
    private val onAmplitudeChanged: ((Float) -> Unit)? = null
) {

    companion object {
        private const val TAG = "AudioCaptureEngine"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val isRunning = AtomicBoolean(false)
    private var captureThread: Thread? = null

    // Audio recording & playback handles
    private var audioRecord: AudioRecord? = null
    private var audioTrack1: AudioTrack? = null
    private var audioTrack2: AudioTrack? = null

    // Delay buffers for lip-sync / latency difference compensation
    private val delayBuffer1 = AudioDelayBuffer(sampleRate = SAMPLE_RATE, channels = 2)
    private val delayBuffer2 = AudioDelayBuffer(sampleRate = SAMPLE_RATE, channels = 2)

    // Dynamic runtime parameters
    @Volatile private var volume1 = config.volume1
    @Volatile private var volume2 = config.volume2
    @Volatile private var delayMs1 = config.delayMs1
    @Volatile private var delayMs2 = config.delayMs2

    /**
     * Starts the audio capture and dual-track routing pipeline.
     */
    @Synchronized
    fun start(): Boolean {
        if (isRunning.get()) {
            Log.w(TAG, "AudioCaptureEngine is already running")
            return true
        }

        try {
            // 1. Build AudioPlaybackCaptureConfiguration
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            // 2. Calculate buffer size
            val minRecordBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_IN_CONFIG,
                AUDIO_ENCODING
            )
            val bufferSize = (minRecordBufferSize * 2).coerceAtLeast(4096)

            // 3. Initialize AudioRecord
            val audioFormatIn = AudioFormat.Builder()
                .setEncoding(AUDIO_ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_IN_CONFIG)
                .build()

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setAudioFormat(audioFormatIn)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord for internal audio capture")
                record.release()
                return false
            }
            audioRecord = record

            // 4. Initialize AudioTracks for each headset
            val devInfo1 = AudioDeviceInfoHelper.findAudioDeviceInfo(audioManager, config.deviceAddress1)
            val devInfo2 = AudioDeviceInfoHelper.findAudioDeviceInfo(audioManager, config.deviceAddress2)

            audioTrack1 = createAudioTrack(devInfo1, bufferSize)
            audioTrack2 = createAudioTrack(devInfo2, bufferSize)

            // 5. Start audio hardware playback & recording
            audioTrack1?.play()
            audioTrack2?.play()
            record.startRecording()

            isRunning.set(true)

            // 6. Launch high-priority audio processing loop
            captureThread = Thread({
                runAudioLoop(bufferSize)
            }, "DualAudioCaptureWorker").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.i(TAG, "AudioCaptureEngine started successfully: Slot1 -> ${devInfo1?.productName ?: "Default"}, Slot2 -> ${devInfo2?.productName ?: "Default"}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioCaptureEngine", e)
            stop()
            return false
        }
    }

    /**
     * Real-time audio pump thread executing at URGENT_AUDIO priority.
     */
    private fun runAudioLoop(bufferSizeBytes: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val sampleBufferSize = bufferSizeBytes / 2 // Short samples
        val rawBuffer = ShortArray(sampleBufferSize)
        val delayedBuffer1 = ShortArray(sampleBufferSize)
        val delayedBuffer2 = ShortArray(sampleBufferSize)
        val finalBuffer1 = ShortArray(sampleBufferSize)
        val finalBuffer2 = ShortArray(sampleBufferSize)

        var lastMeterUpdateMs = 0L

        while (isRunning.get()) {
            val record = audioRecord ?: break
            val readSamples = record.read(rawBuffer, 0, sampleBufferSize)

            if (readSamples > 0) {
                // UI Visualizer amplitude callback (throttled to ~30Hz)
                val now = System.currentTimeMillis()
                if (now - lastMeterUpdateMs > 33) {
                    val rms = PcmProcessor.calculateNormalizedRms(rawBuffer, readSamples)
                    onAmplitudeChanged?.invoke(rms)
                    lastMeterUpdateMs = now
                }

                // Process Stream 1: Apply delay offset then software volume
                delayBuffer1.process(rawBuffer, delayedBuffer1, readSamples, delayMs1)
                PcmProcessor.applyVolume(delayedBuffer1, finalBuffer1, volume1, readSamples)
                audioTrack1?.write(finalBuffer1, 0, readSamples)

                // Process Stream 2: Apply delay offset then software volume
                delayBuffer2.process(rawBuffer, delayedBuffer2, readSamples, delayMs2)
                PcmProcessor.applyVolume(delayedBuffer2, finalBuffer2, volume2, readSamples)
                audioTrack2?.write(finalBuffer2, 0, readSamples)

            } else if (readSamples < 0) {
                Log.w(TAG, "AudioRecord read returned error code: $readSamples")
                // Sleep briefly to prevent busy-spinning on error
                try { Thread.sleep(5) } catch (_: InterruptedException) {}
            }
        }

        Log.d(TAG, "Audio processing worker loop finished")
    }

    /**
     * Creates and configures an AudioTrack with target device assignment.
     */
    private fun createAudioTrack(deviceInfo: android.media.AudioDeviceInfo?, bufferSize: Int): AudioTrack {
        val audioFormatOut = AudioFormat.Builder()
            .setEncoding(AUDIO_ENCODING)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_OUT_CONFIG)
            .build()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(audioFormatOut)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (deviceInfo != null) {
            val success = track.setPreferredDevice(deviceInfo)
            Log.d(TAG, "setPreferredDevice ${deviceInfo.productName} on AudioTrack: $success")
        }

        return track
    }

    /**
     * Dynamically updates volume for both headset channels.
     */
    fun updateVolumes(vol1: Float, vol2: Float) {
        this.volume1 = vol1.coerceIn(0f, 1.5f)
        this.volume2 = vol2.coerceIn(0f, 1.5f)
    }

    /**
     * Dynamically updates sync delay offset in milliseconds.
     */
    fun updateDelays(delay1: Int, delay2: Int) {
        this.delayMs1 = delay1.coerceIn(0, 1000)
        this.delayMs2 = delay2.coerceIn(0, 1000)
    }

    /**
     * Updates target Bluetooth audio endpoints without stopping audio capture.
     */
    fun updatePreferredDevices(address1: String?, address2: String?) {
        val dev1 = AudioDeviceInfoHelper.findAudioDeviceInfo(audioManager, address1)
        val dev2 = AudioDeviceInfoHelper.findAudioDeviceInfo(audioManager, address2)

        audioTrack1?.setPreferredDevice(dev1)
        audioTrack2?.setPreferredDevice(dev2)
        Log.d(TAG, "Updated preferred devices: Track1 -> ${dev1?.productName}, Track2 -> ${dev2?.productName}")
    }

    /**
     * Safely halts and releases all audio and projection resources.
     */
    @Synchronized
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            return
        }

        try {
            captureThread?.interrupt()
            captureThread?.join(500)
        } catch (_: InterruptedException) {
        } finally {
            captureThread = null
        }

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }

        try {
            audioTrack1?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack1", e)
        } finally {
            audioTrack1 = null
        }

        try {
            audioTrack2?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack2", e)
        } finally {
            audioTrack2 = null
        }

        delayBuffer1.reset()
        delayBuffer2.reset()
        onAmplitudeChanged?.invoke(0f)

        Log.i(TAG, "AudioCaptureEngine stopped and released all audio tracks")
    }
}

