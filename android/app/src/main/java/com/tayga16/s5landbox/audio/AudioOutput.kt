package com.tayga16.s5landbox.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.tayga16.s5landbox.data.EngineRepository
import kotlin.concurrent.thread

/**
 * Pulls the guest's 48 kHz stereo headphone output and writes it to an
 * [AudioTrack].
 *
 * The guest produces samples only while it has audio playing; when there is
 * nothing to render [EngineRepository.renderAudio] returns zero and the loop
 * idles, so an emulator sitting on SpringBoard does not burn a core on silence.
 */
class AudioOutput(private val repository: EngineRepository) {

    @Volatile private var running = false
    private var worker: Thread? = null
    private var track: AudioTrack? = null

    fun start() {
        // A previous worker that died (no output device, route change) must not
        // stop us from starting again when the user resumes the machine.
        if (worker?.isAlive == true) return
        running = true
        worker = thread(name = "s5l-audio", start = true) { loop() }
    }

    fun stop() {
        running = false
        try {
            track?.pause()
        } catch (_: Throwable) {
            // The device may already have been torn down.
        }
        worker?.interrupt()
        try {
            worker?.join(600)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        worker = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val sampleRate = 48_000
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val capacity = if (minimum > 0) minimum * 2 else 32_768

        val output = try {
            AudioTrack(
                attributes,
                format,
                capacity,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        } catch (_: Throwable) {
            running = false
            return
        }
        track = output

        try {
            output.play()
            val buffer = FloatArray(2_048)
            while (running) {
                val frames = repository.renderAudio(buffer, buffer.size / 2)
                if (frames <= 0) {
                    Thread.sleep(8)
                    continue
                }
                val floats = frames * 2
                var written = 0
                while (written < floats && running) {
                    val count = output.write(
                        buffer,
                        written,
                        floats - written,
                        AudioTrack.WRITE_BLOCKING,
                    )
                    if (count <= 0) break
                    written += count
                }
            }
        } catch (_: InterruptedException) {
            // stop() asked the loop to leave.
        } catch (_: Throwable) {
            // The output device disappeared or was taken by another app.
        } finally {
            running = false
            try {
                output.pause()
            } catch (_: Throwable) {
            }
            try {
                output.flush()
            } catch (_: Throwable) {
            }
            try {
                output.release()
            } catch (_: Throwable) {
            }
            track = null
        }
    }
}
