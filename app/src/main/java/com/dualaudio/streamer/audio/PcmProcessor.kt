package com.dualaudio.streamer.audio

import kotlin.math.sqrt

/**
 * Utility functions for real-time 16-bit PCM audio buffer processing:
 * - Volume attenuation / amplification with saturation clipping.
 * - RMS (Root Mean Square) level calculation for audio visualizer meters.
 */
object PcmProcessor {

    /**
     * Applies volume scaling factor to an array of 16-bit PCM samples (ShortArray).
     *
     * @param input Source short buffer
     * @param output Destination short buffer
     * @param volume Multiplier between 0.0f (mute) and 1.0f (full volume) or higher.
     * @param length Number of samples to process
     */
    fun applyVolume(
        input: ShortArray,
        output: ShortArray,
        volume: Float,
        length: Int = input.size
    ) {
        if (volume <= 0.001f) {
            // Muted: zero out output
            output.fill(0, 0, length)
            return
        }

        if (volume in 0.999f..1.001f) {
            // Unity gain: fast copy
            System.arraycopy(input, 0, output, 0, length)
            return
        }

        for (i in 0 until length) {
            val scaled = (input[i] * volume).toInt()
            // Clip to 16-bit signed integer limits [-32768, 32767]
            output[i] = scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Calculates the normalized Root Mean Square (RMS) amplitude of a 16-bit PCM buffer.
     * Output is normalized between 0.0f and 1.0f for UI visualizers.
     */
    fun calculateNormalizedRms(buffer: ShortArray, length: Int): Float {
        if (length <= 0) return 0f

        var sumSquare = 0.0
        // Sub-sample every 4th sample for high performance during real-time rendering
        val step = 4
        var count = 0

        var i = 0
        while (i < length) {
            val sample = buffer[i].toDouble()
            sumSquare += sample * sample
            count++
            i += step
        }

        if (count == 0) return 0f
        val rms = sqrt(sumSquare / count)
        // Normalize against max 16-bit amplitude (32767.0)
        return (rms / 32767.0).toFloat().coerceIn(0f, 1f)
    }
}

