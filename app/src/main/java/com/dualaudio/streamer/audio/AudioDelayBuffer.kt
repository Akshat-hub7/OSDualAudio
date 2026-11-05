package com.dualaudio.streamer.audio

/**
 * High-performance circular FIFO delay buffer for 16-bit PCM audio.
 * Used to compensate for Bluetooth latency differences (sync offset) between headsets.
 *
 * @param sampleRate Audio sample rate (e.g. 48000 or 44100 Hz)
 * @param channels Number of interleaved audio channels (e.g. 2 for stereo)
 * @param maxDelayMs Maximum supported delay in milliseconds (default 1000 ms)
 */
class AudioDelayBuffer(
    private val sampleRate: Int = 48000,
    private val channels: Int = 2,
    maxDelayMs: Int = 1000
) {
    // Total number of 16-bit samples in max buffer
    private val capacitySamples = ((sampleRate.toLong() * channels * maxDelayMs) / 1000).toInt()
    private val ringBuffer = ShortArray(capacitySamples)

    private var writeIndex = 0
    private var currentDelaySamples = 0
    private var isPrimed = false

    /**
     * Resets the ring buffer and state.
     */
    @Synchronized
    fun reset() {
        ringBuffer.fill(0)
        writeIndex = 0
        currentDelaySamples = 0
        isPrimed = false
    }

    /**
     * Calculates the target delay in samples corresponding to the desired delay in milliseconds.
     */
    private fun delayMsToSamples(delayMs: Int): Int {
        val clampedMs = delayMs.coerceIn(0, 1000)
        val rawSamples = ((sampleRate.toLong() * channels * clampedMs) / 1000).toInt()
        // Ensure sample count aligns with channel frame boundaries
        return (rawSamples / channels) * channels
    }

    /**
     * Processes input audio buffer, delaying it by [delayMs] and writing to [output].
     *
     * @param input Source 16-bit PCM buffer
     * @param output Destination buffer where delayed audio is placed
     * @param length Number of samples to process
     * @param targetDelayMs Desired delay in milliseconds (0 to 1000)
     */
    @Synchronized
    fun process(
        input: ShortArray,
        output: ShortArray,
        length: Int,
        targetDelayMs: Int
    ) {
        val targetDelaySamples = delayMsToSamples(targetDelayMs)

        // If no delay requested and buffer is not primed with previous delay, pass through
        if (targetDelaySamples == 0 && currentDelaySamples == 0) {
            System.arraycopy(input, 0, output, 0, length)
            return
        }

        // Smoothly adjust current delay toward target delay to avoid audible clicks
        val step = channels * 2
        if (currentDelaySamples < targetDelaySamples) {
            currentDelaySamples = (currentDelaySamples + step).coerceAtMost(targetDelaySamples)
        } else if (currentDelaySamples > targetDelaySamples) {
            currentDelaySamples = (currentDelaySamples - step).coerceAtLeast(targetDelaySamples)
        }

        for (i in 0 until length) {
            // Write incoming sample to circular buffer
            ringBuffer[writeIndex] = input[i]

            // Calculate delayed read index
            var readIndex = writeIndex - currentDelaySamples
            if (readIndex < 0) {
                readIndex += capacitySamples
            }

            output[i] = ringBuffer[readIndex]

            // Advance write index
            writeIndex = (writeIndex + 1) % capacitySamples
        }
    }
}

