package com.example.contactreader.com.example.contactreader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sin

object BFSKTransmitter {

    private const val freq0 = 18000
    private const val freq1 = 18500
    private const val sampleRate = 44100

    // Durata di ogni bit
    private const val bitDurationMs = 100

    // Lunghezza fade in/out in millisecondi
    private const val fadeMs = 5

    private var sendingJob: Job? = null

    /**
     * Invia un singolo contatto con BFSK:
     * preambolo (10101010), 16 bit di length, payload, suffisso (11110000).
     */
    fun transmitSingleContact(contact: String) {
        sendingJob?.cancel()
        sendingJob = CoroutineScope(Dispatchers.Default).launch {
            val bitString = buildFrame(contact)
            playBitString(bitString)
        }
    }

    private fun buildFrame(data: String): String {
        val prefix = "10101010"    // 8 bit
        val suffix = "11110000"    // 8 bit

        val payloadBytes = data.toByteArray(Charsets.US_ASCII)
        val length = payloadBytes.size.coerceAtMost(65535)

        val lengthHigh = (length shr 8) and 0xFF
        val lengthLow = length and 0xFF

        val lengthBits = byteToBitString(lengthHigh.toByte()) + byteToBitString(lengthLow.toByte())

        val payloadBits = payloadBytes.joinToString("") { byteToBitString(it) }

        return prefix + lengthBits + payloadBits + suffix
    }

    private fun byteToBitString(b: Byte): String {
        val sb = StringBuilder()
        for (i in 7 downTo 0) {
            sb.append((b.toInt() shr i) and 1)
        }
        return sb.toString()
    }

    private fun playBitString(bitString: String) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(sampleRate)
            .build()

        audioTrack.play()

        for (bit in bitString) {
            val freq = if (bit == '0') freq0 else freq1
            val toneData = generateToneWithFade(freq, bitDurationMs, fadeMs)
            audioTrack.write(toneData, 0, toneData.size)
        }

        // Pausa finale
        val pause = generateToneWithFade(freq0, 50, fadeMs, amplitude = 0.0)
        audioTrack.write(pause, 0, pause.size)

        audioTrack.stop()
        audioTrack.release()
    }

    /**
     * Genera tono sinusoidale con fade in/out (ramp) per ridurre i click.
     * fadeMs = ms di fade in, fade out
     */
    private fun generateToneWithFade(
        freq: Int,
        durationMs: Int,
        fadeMs: Int,
        amplitude: Double = 1.0
    ): ShortArray {
        val totalSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = ShortArray(totalSamples)

        // Numero campioni di fade
        val fadeSamples = (sampleRate * fadeMs / 1000.0).toInt()

        val angularFreq = 2.0 * Math.PI * freq / sampleRate

        for (i in 0 until totalSamples) {
            val raw = sin(i * angularFreq) * amplitude

            // Fade In
            val fadeInFactor = if (i < fadeSamples) i.toDouble() / fadeSamples else 1.0
            // Fade Out
            val fadeOutFactor = if (i > totalSamples - fadeSamples) {
                (totalSamples - i).toDouble() / fadeSamples
            } else 1.0

            val env = fadeInFactor.coerceAtMost(fadeOutFactor)
            val sampleValue = raw * env

            samples[i] = (sampleValue * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return samples
    }
}