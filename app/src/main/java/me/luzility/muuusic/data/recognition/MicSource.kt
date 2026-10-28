package me.luzility.muuusic.data.recognition

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission

class MicSource private constructor(private val recorder: AudioRecord) : PcmSource {
    private var closed = false

    override fun read(buffer: ShortArray): Int = recorder.read(buffer, 0, buffer.size)

    override fun close() {
        if (closed) return
        closed = true
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        recorder.release()
    }

    companion object {
        private const val READ_SIZE = 1024

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun open(): MicSource? {
            val minBuffer = AudioRecord.getMinBufferSize(
                ShazamSignature.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                ShazamSignature.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, READ_SIZE * 4),
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return null
            }
            recorder.startRecording()
            return MicSource(recorder)
        }
    }
}
