package com.ludo.king

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object SoundEngine {
    var isMuted = false

    private fun playBuffer(samples: ShortArray, sampleRate: Int = 22050) {
        if (isMuted) return
        CoroutineScope(Dispatchers.IO).launch {
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
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                delay((samples.size * 1000L) / sampleRate + 60L)
                track.release()
            } catch (_: Exception) {}
        }
    }

    // গুটি চলার পপ-পপ সাউন্ড
    fun playStep() {
        val sr = 22050
        val n = (0.065 * sr).toInt()
        val buf = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val freq = 350.0 + 490.0 * sin(t * PI * 0.7)
            val env = sin(t * PI) * exp(-2.4 * t)
            buf[i] = (sin(phase) * env * 0.85 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            phase += 2.0 * PI * freq / sr
        }
        playBuffer(buf, sr)
    }

    // ডাইস ঘোরার র‍্যাটল সাউন্ড
    fun playDiceRoll() {
        val sr = 22050
        val n = (0.28 * sr).toInt()
        val buf = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val burst = if (((t * 18).toInt() % 2) == 0) 1.0 else 0.15
            val noise = (Random.nextDouble() * 2.0 - 1.0) * burst * exp(-1.8 * t)
            buf[i] = (noise * 0.65 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        playBuffer(buf, sr)
    }

    // ছক্কা ও সেফ জোনের বেল সাউন্ড
    fun playHomeOrSix() {
        val sr = 22050
        val n = (0.24 * sr).toInt()
        val buf = ShortArray(n)
        var p1 = 0.0
        var p2 = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val env = exp(-4.0 * t)
            val s = (sin(p1) * 0.6 + sin(p2) * 0.4) * env
            buf[i] = (s * 0.85 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            p1 += 2.0 * PI * 880.0 / sr
            p2 += 2.0 * PI * 1318.5 / sr
        }
        playBuffer(buf, sr)
    }

    fun playSafeZone() = playHomeOrSix()

    // গুটি হোমে (পাকা ঘরে) ঢুকলে ঝাকানাকা জয়োল্লাস সাউন্ড!
    fun playTokenHomeFanfare() {
        if (isMuted) return
        CoroutineScope(Dispatchers.IO).launch {
            val sr = 22050
            val chordNotes = listOf(523.25, 659.25, 783.99, 1046.50, 1318.51)
            for ((idx, freq) in chordNotes.withIndex()) {
                val dur = if (idx == chordNotes.lastIndex) 0.34 else 0.095
                val n = (dur * sr).toInt()
                val buf = ShortArray(n)
                var p1 = 0.0
                var p2 = 0.0
                for (i in 0 until n) {
                    val t = i.toDouble() / n
                    val shimmer = 1.0 + 0.18 * sin(2.0 * PI * 28.0 * t)
                    val env = sin(t * PI * 0.85) * exp(-2.2 * t)
                    val sig = (sin(p1) * 0.65 + sin(p2) * 0.35) * env * shimmer
                    buf[i] = (sig * 0.92 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                    p1 += 2.0 * PI * freq / sr
                    p2 += 2.0 * PI * (freq * 1.5) / sr
                }
                playBuffer(buf, sr)
                delay(75L)
            }
        }
    }

    // গুটি কাটলে আসল তীক্ষ্ণ "শাঁআআ" হুশ সাউন্ড
    fun playCaptureWhoosh() {
        val sr = 22050
        val n = (0.48 * sr).toInt()
        val buf = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val freq = 1550.0 * exp(-2.5 * t) + 130.0
            val wind = (Random.nextDouble() * 2.0 - 1.0) * 0.30 * sin(t * PI)
            val env = sin(t * PI * 0.85) * exp(-1.3 * t)
            val sig = (sin(phase) * 0.72 + wind) * env
            buf[i] = (sig * 0.96 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            phase += 2.0 * PI * freq / sr
        }
        playBuffer(buf, sr)
    }

    // ম্যাচ জয়ের ফ্যানফেয়ার
    fun playWinFanfare() = playTokenHomeFanfare()
}
