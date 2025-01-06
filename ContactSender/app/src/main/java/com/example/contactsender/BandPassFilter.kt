package com.example.contactsender

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Biquad passband filter semplificato
 */
class BandPassFilter(
    sampleRate: Double,
    freqCenter: Double,
    qFactor: Double
) {
    // Coefficienti
    private var a0 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0

    // Memoria
    private var in1 = 0.0
    private var in2 = 0.0
    private var out1 = 0.0
    private var out2 = 0.0

    init {
        val omega = 2.0 * PI * freqCenter / sampleRate
        val alpha = sin(omega) / (2.0 * qFactor)
        val cosw = cos(omega)

        val b0 = alpha
        val b1_ = 0.0
        val b2_ = -alpha
        val a0_ = 1.0 + alpha
        val a1_ = -2.0 * cosw
        val a2_ = 1.0 - alpha

        // Normalizza
        a0 = b0 / a0_
        a1 = b1_ / a0_
        a2 = b2_ / a0_
        b1 = a1_ / a0_
        b2 = a2_ / a0_
    }

    fun processSample(input: Double): Double {
        val out = a0 * input + a1 * in1 + a2 * in2 - b1 * out1 - b2 * out2
        in2 = in1
        in1 = input
        out2 = out1
        out1 = out
        return out
    }
}