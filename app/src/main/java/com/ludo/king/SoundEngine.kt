package com.ludo.king

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object SoundEngine {
    var isMuted = false
    private const val SAMPLE_RATE = 22050

    fun playDiceRoll() {
        if (isMuted) return
        CoroutineScope(Dispatchers.IO).launch {
            val durationMs = 340
            val numSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val clickPulse = if ((i / 650) % 2 == 0) exp(-((i % 650) / 140.0)) else 0.0
                val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.5 + sin(2.0 * PI * 320.0 * t) * 0.5
                buffer[i] = (noise * clickPulse * 18000).toInt().coerceIn(-32767, 32767).toShort()
            }
            playBuffer(buffer)
        }
    }

    fun playStep() {
        if (isMuted) return
        CoroutineScope(Dispatchers.IO).launch {
            val durationMs = 65
            val numSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-t * 55.0)
                val freq = 520.0 + 280.0 * exp(-t * 40.0)
                val sample = sin(2.0 * PI * freq * t) * env
                buffer[i] = (sample * 20000).toInt().coerceIn(-32767, 32767).toShort()
            }
            playBuffer(buffer)
        }
    }

    fun playCapture() {
        if (isMuted) return
        CoroutineScope(Dispatchers.IO).launch {
            val durationMs = 380
            val numSamples = (SAMPLE_RATE * durationMs) / 1000
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-t * 7.0)
                val freq = 680.0 - 460.0 * (t / 0.38)
                val sample = (sin(2.0 * PI * freq * t) + 0.4 * sin(2.0 * PI * freq * 1.5 * t)) * env
                buffer[i] = (sample * 22000).toInt().coerceIn(-32767, 32767).toShort()
            }
            playBuffer(buffer)
        }
    }

    fun playHomeOrSix() {
        if (isMuted) return
        CoroutineScope(Dispatchers.IO).launch {
            val notes = listOf(523.25, 659.25, 783.99) // C5, E5, G5
            for (freq in notes) {
                val numSamples = (SAMPLE_RATE * 85) / 1000
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = exp(-t * 18.0)
                    buffer[i] = (sin(2.0 * PI * freq * t) * env * 18000).toInt().toShort()
                }
                playBuffer(buffer)
            }
        }
    }

    fun playWinFanfare() {
        if (isMuted) return
        CoroutineScope(Dispatchers.IO).launch {
            val notes = listOf(523.25, 659.25, 783.99, 1046.50)
            for ((idx, freq) in notes.withIndex()) {
                val dur = if (idx == 3) 400 else 120
                val numSamples = (SAMPLE_RATE * dur) / 1000
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = exp(-t * 6.0)
                    buffer[i] = (sin(2.0 * PI * freq * t) * env * 22000).toInt().toShort()
                }
                playBuffer(buffer)
            }
        }
    }

    private fun playBuffer(buffer: ShortArray) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep((buffer.size * 1000L / SAMPLE_RATE) + 20)
            track.release()
        } catch (_: Exception) {}
    }
}