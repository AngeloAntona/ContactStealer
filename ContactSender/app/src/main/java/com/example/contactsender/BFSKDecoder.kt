package com.example.contactsender

class BFSKDecoder(
    freq0: Int,
    freq1: Int,
    sampleRate: Int,
    private val bitDurationMs: Int,
    private val onBitDecoded: (Char) -> Unit
) {
    private val samplesPerBit = (sampleRate * bitDurationMs / 1000)
    private val sampleBuffer = mutableListOf<Short>()
    private val bitBuffer = StringBuilder()

    // Filtri band-pass con q=5.0
    private val filter0 = BandPassFilter(sampleRate.toDouble(), freq0.toDouble(), 5.0)
    private val filter1 = BandPassFilter(sampleRate.toDouble(), freq1.toDouble(), 5.0)

    fun decodeSamples(buffer: ShortArray, size: Int): String? {
        for (i in 0 until size) {
            sampleBuffer.add(buffer[i])
        }

        var output: String? = null

        while (sampleBuffer.size >= samplesPerBit) {
            val block = sampleBuffer.subList(0, samplesPerBit).toShortArray()
            sampleBuffer.subList(0, samplesPerBit).clear()

            resetFilters()

            var energy0 = 0.0
            var energy1 = 0.0

            for (sample in block) {
                val s = sample.toDouble()
                val out0 = filter0.processSample(s)
                val out1 = filter1.processSample(s)
                energy0 += out0 * out0
                energy1 += out1 * out1
            }

            val bit = if (energy0 > energy1) '0' else '1'
            bitBuffer.append(bit)
            onBitDecoded(bit)
        }

        if (bitBuffer.isNotEmpty()) {
            output = bitBuffer.toString()
        }
        return output
    }

    private fun resetFilters() {
        filter0::class.java.getDeclaredField("in1").apply { isAccessible = true; setDouble(filter0, 0.0) }
        filter0::class.java.getDeclaredField("in2").apply { isAccessible = true; setDouble(filter0, 0.0) }
        filter0::class.java.getDeclaredField("out1").apply { isAccessible = true; setDouble(filter0, 0.0) }
        filter0::class.java.getDeclaredField("out2").apply { isAccessible = true; setDouble(filter0, 0.0) }

        filter1::class.java.getDeclaredField("in1").apply { isAccessible = true; setDouble(filter1, 0.0) }
        filter1::class.java.getDeclaredField("in2").apply { isAccessible = true; setDouble(filter1, 0.0) }
        filter1::class.java.getDeclaredField("out1").apply { isAccessible = true; setDouble(filter1, 0.0) }
        filter1::class.java.getDeclaredField("out2").apply { isAccessible = true; setDouble(filter1, 0.0) }
    }
}