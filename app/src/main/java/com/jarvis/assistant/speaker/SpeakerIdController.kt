package com.jarvis.assistant.speaker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.PeopleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class EnrollResult(val fingerprints: List<FloatArray>, val error: String? = null)

/** Outcome of comparing one recording against every enrolled person. */
data class MatchResult(
    val acceptedId: String?,   // null = no enrolled person is close enough
    val closestId: String,
    val distance: Float,
    val limit: Float
)

/**
 * Records short mic clips (AudioRecord) and matches them against the enrolled people.
 *
 * Used in two places: (1) Settings — enrolling a person's voice and a "recognition test";
 * (2) alongside SpeechRecognizer — a parallel capture of whatever the user says so we can work
 * out WHO said it. If the OS gives the recognizer exclusive access to the mic on a particular
 * device, the parallel capture comes back silent and identification simply doesn't change the
 * current speaker (nothing breaks — Jarvis just keeps using the last known/forced person).
 */
object SpeakerIdController {
    private const val MAX_SECONDS = 12
    private const val SR = VoiceFingerprint.SAMPLE_RATE

    private val lock = Any()
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var capturing = false
    private val samples = ShortArray(SR * MAX_SECONDS)
    @Volatile private var count = 0

    fun startCapture(context: Context): Boolean {
        if (capturing) stopCapture()
        return synchronized(lock) { startLocked(context) }
    }

    @SuppressLint("MissingPermission")
    private fun startLocked(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuf, SR)
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release(); return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) { rec.release(); return false }

        record = rec
        count = 0
        capturing = true
        thread = Thread {
            val chunk = ShortArray(1024)
            while (capturing) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n < 0) break
                if (n == 0) continue
                val take = min(n, samples.size - count)
                if (take > 0) {
                    System.arraycopy(chunk, 0, samples, count, take)
                    count += take
                }
            }
        }.also { it.isDaemon = true; it.start() }
        return true
    }

    /** Stops the capture and returns everything recorded since [startCapture] (at most ~12 s). */
    fun stopCapture(): ShortArray {
        var t: Thread? = null
        var rec: AudioRecord? = null
        synchronized(lock) {
            capturing = false
            t = thread; rec = record
            thread = null; record = null
        }
        try { t?.join(400) } catch (e: InterruptedException) { }
        try { rec?.stop() } catch (e: Exception) { }
        try { rec?.release() } catch (e: Exception) { }
        return samples.copyOf(count.coerceIn(0, samples.size))
    }

    fun cancelCapture() {
        if (capturing) stopCapture()
    }

    // ---------- Enrollment ----------

    /** Turns one enrollment recording into up to 3 fingerprints (it's split into thirds for variety). */
    suspend fun buildEnrollment(pcm: ShortArray): EnrollResult = withContext(Dispatchers.Default) {
        if (pcm.size < SR * 2) {
            return@withContext EnrollResult(emptyList(), "Kayıt çok kısa ya da mikrofon ses alamadı. Arka plan dinleme açıksa kapatıp tekrar dene.")
        }
        val third = pcm.size / 3
        val prints = (0 until 3).mapNotNull { i ->
            VoiceFingerprint.extract(pcm.copyOfRange(i * third, if (i == 2) pcm.size else (i + 1) * third))
        }
        if (prints.isEmpty()) {
            EnrollResult(emptyList(), "Yeterli net konuşma algılanamadı. Mikrofona biraz daha yakın ve sürekli konuşarak tekrar dene.")
        } else EnrollResult(prints)
    }

    // ---------- Matching ----------

    private fun limitFor(context: Context, enrolledCount: Int): Float {
        val base = when (PeopleStore.getSensitivity(context)) {
            0 -> 10f   // esnek
            2 -> 5.5f  // sıkı
            else -> 7.5f
        }
        // With 2+ people enrolled "the closest one" is already a meaningful decision, so allow a bit more slack.
        return if (enrolledCount >= 2) base * 1.4f else base
    }

    /** Null when nobody has a voice enrolled or the clip has too little clear speech. */
    fun evaluate(context: Context, pcm: ShortArray): MatchResult? {
        val people = PeopleStore.getPeople(context).filter { it.fingerprints.isNotEmpty() }
        if (people.isEmpty()) return null
        val fp = VoiceFingerprint.extract(pcm) ?: return null

        var bestId = people.first().id
        var bestDist = Float.MAX_VALUE
        for (p in people) {
            for (print in p.fingerprints) {
                val d = VoiceFingerprint.distance(fp, print)
                if (d < bestDist) { bestDist = d; bestId = p.id }
            }
        }
        val limit = limitFor(context, people.size)
        return MatchResult(if (bestDist <= limit) bestId else null, bestId, bestDist, limit)
    }

    /**
     * Called with the audio captured alongside a spoken command. A person forced in Settings wins;
     * otherwise the match (or "nobody matched" => guest) becomes the current speaker. If the clip is
     * too short/silent to judge, the previous speaker is kept.
     */
    fun identifyAndApply(context: Context, pcm: ShortArray) {
        if (PeopleStore.getForcedSpeakerId(context) != null) return
        val result = evaluate(context, pcm) ?: return
        SpeakerContext.setIdentified(result.acceptedId)
    }
}
