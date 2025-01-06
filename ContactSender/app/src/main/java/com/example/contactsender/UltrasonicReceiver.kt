package com.example.contactsender

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class UltrasonicReceiver(
    private val context: Context
) {
    private val freq0 = 18000
    private val freq1 = 18500
    private val sampleRate = 44100

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var audioRecord: AudioRecord? = null
    private var listenJob: Job? = null

    var isListening = false
        private set

    // Callback finale
    var onAllContactsReceived: ((String) -> Unit)? = null
    // Callback parziale
    var onChunkReceived: ((chunkIndex: Int, totalChunks: Int) -> Unit)? = null

    private val receivedChunks = mutableMapOf<Int, String>()
    private var totalChunks = 0

    fun startListening() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) return
        if (isListening) return
        isListening = true

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord?.startRecording()

        listenJob = CoroutineScope(Dispatchers.Default).launch {
            val buffer = ShortArray(bufferSize)
            // BFSKDecoder con filtri bandpass
            val decoder = BFSKDecoder(
                freq0, freq1, sampleRate,
                bitDurationMs = 100
            ) { bit: Char ->
                // Se vuoi debug in tempo reale:
                // println("Bit: $bit")
            }

            while (isActive && isListening) {
                val readCount = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readCount > 0) {
                    val frames = decoder.decodeSamples(buffer, readCount)
                    frames?.let { parseFrames(it) }
                }
            }
        }
    }

    fun stopListening() {
        isListening = false
        listenJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    /**
     * Parse del frame BFSK:
     *  - preambolo 10101010 (8 bit)
     *  - chunkIndex (16 bit)
     *  - totalChunks (16 bit)
     *  - length (16 bit)
     *  - payload
     *  - suffisso 11110000 (8 bit)
     */
    private fun parseFrames(bitStream: String) {
        var currentIndex = 0
        while (true) {
            val start = bitStream.indexOf("10101010", currentIndex)
            if (start < 0) break
            var pos = start + 8

            // chunkIndex
            if (bitStream.length < pos + 16) break
            val chunkIndexBits = bitStream.substring(pos, pos + 16)
            pos += 16
            val cIndex = bitStringToInt(chunkIndexBits)

            // totalChunks
            if (bitStream.length < pos + 16) break
            val totalChunksBits = bitStream.substring(pos, pos + 16)
            pos += 16
            val tChunks = bitStringToInt(totalChunksBits)
            totalChunks = tChunks

            // length
            if (bitStream.length < pos + 16) break
            val lengthBits = bitStream.substring(pos, pos + 16)
            pos += 16
            val payloadLen = bitStringToInt(lengthBits)

            // payload
            val payloadBitsSize = payloadLen * 8
            if (bitStream.length < pos + payloadBitsSize) break
            val payloadBits = bitStream.substring(pos, pos + payloadBitsSize)
            pos += payloadBitsSize
            val payloadBytes = bitsToByteArray(payloadBits)
            val payloadString = payloadBytes.toString(Charsets.US_ASCII)

            // suffisso
            if (bitStream.length < pos + 8) break
            val suffix = bitStream.substring(pos, pos + 8)
            pos += 8
            if (suffix != "11110000") {
                currentIndex = start + 1
                continue
            }

            // Trovato frame
            receivedChunks[cIndex] = payloadString
            onChunkReceived?.invoke(cIndex, totalChunks)

            if (receivedChunks.size == totalChunks) {
                val all = receivedChunks.toSortedMap().values.joinToString(";")
                onAllContactsReceived?.invoke(all)
            }

            currentIndex = pos
        }
    }

    private fun bitStringToInt(bits: String): Int {
        var result = 0
        for (ch in bits) {
            result = (result shl 1) or (ch - '0')
        }
        return result
    }

    private fun bitsToByteArray(bits: String): ByteArray {
        val count = bits.length / 8
        val arr = ByteArray(count)
        for (i in 0 until count) {
            val byteStr = bits.substring(i*8, i*8+8)
            arr[i] = byteStr.toInt(2).toByte()
        }
        return arr
    }

    fun uploadContactsToServer(contacts: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val body = contacts.toRequestBody()
                val req = Request.Builder()
                    .url("https://myserver.example/api/contacts")
                    .post(body)
                    .build()
                client.newCall(req).execute().use {
                    // ...
                }
            } catch (_: Exception) {
            }
        }
    }
}