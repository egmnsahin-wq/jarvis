package com.jarvis.assistant.speaker

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A small, dependency-free "voice fingerprint": median pitch (F0) + average and spread of
 * MFCC coefficients over the voiced part of a recording. It is NOT a biometric-grade speaker
 * model — it's a lightweight heuristic that separates a handful of household voices reasonably
 * well, and can confuse similar voices or be thrown off by noise/mic changes.
 *
 * Feature layout (25 floats): [0] = log2(F0)*8, [1..12] = mean MFCC c1..c12, [13..24] = their std.
 */
object VoiceFingerprint {
    const val SAMPLE_RATE = 16000
    const val FEATURE_SIZE = 25

    private const val FRAME = 512
    private const val HOP = 256
    private const val N_MEL = 24
    private const val N_COEF = 13
    private const val MIN_VOICED_FRAMES = 40   // ~0.65 s of actual speech
    private const val MIN_PITCHED_FRAMES = 8

    private val hann = FloatArray(FRAME) { (0.5 - 0.5 * cos(2.0 * PI * it / (FRAME - 1))).toFloat() }
    private val melBank: Array<FloatArray> = buildMelBank()
    private val dctMatrix: Array<FloatArray> = Array(N_COEF) { k ->
        FloatArray(N_MEL) { n -> (sqrt(2.0 / N_MEL) * cos(PI * k * (n + 0.5) / N_MEL)).toFloat() }
    }

    /** Returns null when there isn't enough clear speech to fingerprint (too short, too quiet, unvoiced). */
    fun extract(pcm: ShortArray): FloatArray? {
        if (pcm.size < FRAME * 4) return null
        val x = FloatArray(pcm.size) { pcm[it] / 32768f }

        val nFrames = (x.size - FRAME) / HOP + 1
        val rms = FloatArray(nFrames)
        for (f in 0 until nFrames) {
            var s = 0f
            val off = f * HOP
            for (i in 0 until FRAME) { val v = x[off + i]; s += v * v }
            rms[f] = sqrt(s / FRAME)
        }
        val sorted = rms.sortedArray()
        val p90 = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.size - 1)]
        if (p90 < 0.012f) return null // basically silence
        val threshold = max(0.008f, p90 * 0.3f)
        val voiced = (0 until nFrames).filter { rms[it] >= threshold }
        if (voiced.size < MIN_VOICED_FRAMES) return null

        val re = FloatArray(FRAME)
        val im = FloatArray(FRAME)
        val logMel = FloatArray(N_MEL)
        val coefs = ArrayList<FloatArray>()
        val pitches = ArrayList<Float>()

        for ((idx, f) in voiced.withIndex()) {
            val off = f * HOP
            for (i in 0 until FRAME) {
                val cur = x[off + i]
                val prev = if (off + i > 0) x[off + i - 1] else 0f
                re[i] = (cur - 0.97f * prev) * hann[i]
                im[i] = 0f
            }
            fft(re, im)
            for (m in 0 until N_MEL) {
                var e = 0f
                val w = melBank[m]
                for (b in 0..FRAME / 2) {
                    val wb = w[b]
                    if (wb != 0f) e += wb * (re[b] * re[b] + im[b] * im[b])
                }
                logMel[m] = ln(e + 1e-6f)
            }
            val c = FloatArray(N_COEF)
            for (k in 0 until N_COEF) {
                var s = 0f
                val row = dctMatrix[k]
                for (n in 0 until N_MEL) s += row[n] * logMel[n]
                c[k] = s
            }
            coefs.add(c)
            if (idx % 2 == 0) pitchOf(x, off)?.let { pitches.add(it) }
        }

        if (pitches.size < MIN_PITCHED_FRAMES) return null
        pitches.sort()
        val f0 = pitches[pitches.size / 2]

        val out = FloatArray(FEATURE_SIZE)
        out[0] = (ln(f0) / ln(2f)) * 8f
        for (k in 1 until N_COEF) {
            var mean = 0f
            for (c in coefs) mean += c[k]
            mean /= coefs.size
            var v = 0f
            for (c in coefs) { val d = c[k] - mean; v += d * d }
            out[k] = mean
            out[12 + k] = sqrt(v / coefs.size)
        }
        return out
    }

    /** Weighted Euclidean distance; smaller = more similar. Typical same-voice ≈ 2–5, different voices ≈ 7+. */
    fun distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return Float.MAX_VALUE
        var s = 0f
        for (i in a.indices) {
            val w = if (i >= 13) 0.5f else 1f // spread features are noisier, count them half
            val d = (a[i] - b[i]) * w
            s += d * d
        }
        return sqrt(s)
    }

    // ---------- internals ----------

    private fun pitchOf(x: FloatArray, off: Int): Float? {
        var mean = 0f
        for (i in 0 until FRAME) mean += x[off + i]
        mean /= FRAME
        var r0 = 0f
        for (i in 0 until FRAME) { val v = x[off + i] - mean; r0 += v * v }
        if (r0 < 1e-6f) return null
        val minLag = SAMPLE_RATE / 400
        val maxLag = SAMPLE_RATE / 70
        var best = 0f
        var bestLag = 0
        for (lag in minLag..maxLag) {
            var r = 0f
            for (i in 0 until FRAME - lag) r += (x[off + i] - mean) * (x[off + i + lag] - mean)
            val norm = r / r0
            if (norm > best) { best = norm; bestLag = lag }
        }
        return if (best > 0.35f && bestLag > 0) SAMPLE_RATE.toFloat() / bestLag else null
    }

    private fun hzToMel(f: Double) = 2595.0 * log10(1.0 + f / 700.0)
    private fun melToHz(m: Double) = 700.0 * (10.0.pow(m / 2595.0) - 1.0)

    private fun buildMelBank(): Array<FloatArray> {
        val bins = FRAME / 2 + 1
        val melMin = hzToMel(80.0)
        val melMax = hzToMel(5000.0)
        val points = DoubleArray(N_MEL + 2) { melToHz(melMin + (melMax - melMin) * it / (N_MEL + 1)) }
        val binFreq = DoubleArray(bins) { it * SAMPLE_RATE.toDouble() / FRAME }
        return Array(N_MEL) { m ->
            val lo = points[m]
            val mid = points[m + 1]
            val hi = points[m + 2]
            FloatArray(bins) { b ->
                val f = binFreq[b]
                when {
                    f <= lo || f >= hi -> 0f
                    f <= mid -> ((f - lo) / (mid - lo)).toFloat()
                    else -> ((hi - f) / (hi - mid)).toFloat()
                }
            }
        }
    }

    /** In-place iterative radix-2 FFT; array size must be a power of two. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlenRe = cos(ang).toFloat()
            val wlenIm = sin(ang).toFloat()
            val half = len / 2
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vRe = re[b] * wRe - im[b] * wIm
                    val vIm = re[b] * wIm + im[b] * wRe
                    re[b] = re[a] - vRe
                    im[b] = im[a] - vIm
                    re[a] = re[a] + vRe
                    im[a] = im[a] + vIm
                    val nwRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nwRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
