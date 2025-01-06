package com.example.contactsender

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class BFSKReceiver(
    private val context: Context,
    private val onBitReceived: (Char) -> Unit,
    private val onContactReceived: (String) -> Unit
) {
    private val freq0 = 20000
    private val freq1 = 20500
    private val sampleRate = 44100
    private val bitDurationMs = 100

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    var isListening = false
        private set

    private val decoder = BFSKDecoder(freq0, freq1, sampleRate, bitDurationMs) { rawBit ->
        onBitReceived(rawBit)
        onSingleBitDecoded(rawBit)
    }

    private val tripleBuffer = StringBuilder()
    private var tripleCount = 0

    private val bitAccum = StringBuilder()
    private var parseState = 0
    private var totalLength = -1
    private var tempContact = ""

    fun startListening() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        if (isListening) return
        isListening = true

        // Inizializziamo SheetsHelper:
        // (assicurati di aver copiato il file credentials in assets e impostato il nome giusto)
        SheetsHelper.init(context)

        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufSize
        )
        audioRecord?.startRecording()

        recordJob = CoroutineScope(Dispatchers.Default).launch {
            val buffer = ShortArray(minBufSize)
            while (isActive && isListening) {
                val readCount = audioRecord?.read(buffer, 0, minBufSize) ?: 0
                if (readCount > 0) {
                    decoder.decodeSamples(buffer, readCount)
                }
            }
        }
    }

    fun stopListening() {
        isListening = false
        recordJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun onSingleBitDecoded(rawBit: Char) {
        tripleBuffer.append(rawBit)
        tripleCount++
        if (tripleCount == 3) {
            val majority = majorityBit(tripleBuffer[0], tripleBuffer[1], tripleBuffer[2])
            tripleBuffer.clear()
            tripleCount = 0
            parseBit(majority)
        }
    }

    private fun majorityBit(a: Char, b: Char, c: Char): Char {
        val zeros = listOf(a, b, c).count { it == '0' }
        return if (zeros >= 2) '0' else '1'
    }

    private fun parseBit(bit: Char) {
        bitAccum.append(bit)

        while (true) {
            when (parseState) {
                0 -> {
                    val idx = bitAccum.indexOf("10101010")
                    if (idx >= 0) {
                        bitAccum.delete(0, idx + 8)
                        parseState = 1
                        totalLength = -1
                    } else return
                }
                1 -> {
                    if (bitAccum.length < 16) return
                    val lenBits = bitAccum.substring(0, 16)
                    bitAccum.delete(0, 16)
                    val lenVal = lenBits.toInt(2)
                    totalLength = lenVal
                    parseState = 2
                }
                2 -> {
                    val needed = totalLength * 8
                    if (bitAccum.length < needed) return
                    val payloadBits = bitAccum.substring(0, needed)
                    bitAccum.delete(0, needed)
                    val bytes = bitsToByteArray(payloadBits)
                    tempContact = bytes.toString(Charsets.US_ASCII)
                    parseState = 3
                }
                3 -> {
                    if (bitAccum.length < 8) return
                    val suffix = bitAccum.substring(0, 8)
                    if (suffix == "11110000") {
                        bitAccum.delete(0, 8)
                        parseState = 0
                        // contatto completato
                        onContactReceived(tempContact)
                        // Salviamo su Google Sheets
                        SheetsHelper.appendRowToSheet(tempContact)
                        tempContact = ""
                    } else {
                        bitAccum.deleteCharAt(0)
                    }
                }
            }
        }
    }

    private fun bitsToByteArray(bits: String): ByteArray {
        val n = bits.length / 8
        val out = ByteArray(n)
        for (i in 0 until n) {
            val bStr = bits.substring(i * 8, i * 8 + 8)
            out[i] = bStr.toInt(2).toByte()
        }
        return out
    }
}