package com.recall.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

class AudioCaptureService : Service() {
    @Volatile private var recording = false
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    override fun onCreate() { super.onCreate(); isRunning = true }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture()
            ACTION_START -> startCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        if (recording) return
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
        writePosition = saved.getInt("writePosition", writePosition)
        bytesCaptured = saved.getInt("bytesCaptured", bytesCaptured)
        val sampleRate = SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        if (minBuffer <= 0) { stopSelf(); return }
        val audio = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, CHANNEL, ENCODING, maxOf(minBuffer, CHUNK_BYTES * 2))
        if (audio.state != AudioRecord.STATE_INITIALIZED) { audio.release(); stopSelf(); return }
        recorder = audio
        recording = true
        isRunning = true
        audio.startRecording()
        worker = thread(name = "RecallAudioCapture") {
            val ring = File(filesDir, RING_FILE)
            RandomAccessFile(ring, "rw").use { file ->
                file.setLength(CAPACITY_BYTES.toLong())
                val buffer = ByteArray(CHUNK_BYTES)
                while (recording) {
                    val count = audio.read(buffer, 0, buffer.size)
                    if (count > 0) synchronized(LOCK) {
                        val first = minOf(count, CAPACITY_BYTES - writePosition)
                        file.seek(writePosition.toLong()); file.write(buffer, 0, first)
                        if (first < count) { file.seek(0); file.write(buffer, first, count - first) }
                        writePosition = (writePosition + count) % CAPACITY_BYTES
                        bytesCaptured = (bytesCaptured + count).coerceAtMost(CAPACITY_BYTES)
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putInt("writePosition", writePosition)
                            .putInt("bytesCaptured", bytesCaptured)
                            .apply()
                    }
                }
            }
        }
    }

    private fun stopCapture() {
        recording = false
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        worker?.join(500)
        worker = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt("writePosition", writePosition)
            .putInt("bytesCaptured", bytesCaptured)
            .commit()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "RECALL recording", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("RECALL is recording")
            .setContentText("A rolling audio buffer is active on this device")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { if (recording) stopCapture(); isRunning = false; super.onDestroy() }

    companion object {
        const val ACTION_START = "com.recall.app.START"
        const val ACTION_STOP = "com.recall.app.STOP"
        private const val CHANNEL_ID = "recall_recording"
        private const val NOTIFICATION_ID = 41
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_BYTES = 4096
        private const val CAPACITY_BYTES = SAMPLE_RATE * 2 * 60 * 5
        private const val RING_FILE = "recall-ring.pcm"
        private const val PREFS = "recall_buffer"
        val LOCK = Any()
        @Volatile var isRunning = false
        @Volatile var writePosition = 0
        @Volatile var bytesCaptured = 0

        fun exportLastSeconds(context: Context, seconds: Int, output: File): Boolean = synchronized(LOCK) {
            if (!isRunning) {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                writePosition = prefs.getInt("writePosition", writePosition)
                bytesCaptured = prefs.getInt("bytesCaptured", bytesCaptured)
            }
            val wanted = seconds.coerceIn(1, 300) * SAMPLE_RATE * 2
            if (bytesCaptured < wanted) return false
            val ring = File(context.filesDir, RING_FILE)
            if (!ring.exists() || ring.length() < CAPACITY_BYTES) return false
            val start = (writePosition - wanted + CAPACITY_BYTES) % CAPACITY_BYTES
            val pcm = ByteArray(wanted)
            RandomAccessFile(ring, "r").use { file ->
                val first = minOf(wanted, CAPACITY_BYTES - start)
                file.seek(start.toLong()); file.readFully(pcm, 0, first)
                if (first < wanted) { file.seek(0); file.readFully(pcm, first, wanted - first) }
            }
            output.parentFile?.mkdirs()
            RandomAccessFile(output, "rw").use { file ->
                file.setLength(0)
                file.write(wavHeader(wanted))
                file.write(pcm)
            }
            true
        }

        private fun wavHeader(dataLength: Int): ByteArray {
            val total = dataLength + 36
            val rate = SAMPLE_RATE * 2
            val h = ByteArray(44)
            fun putInt(offset: Int, value: Int) { for (i in 0..3) h[offset + i] = (value shr (8 * i)).toByte() }
            fun putShort(offset: Int, value: Int) { h[offset] = value.toByte(); h[offset + 1] = (value shr 8).toByte() }
            "RIFF".toByteArray().copyInto(h, 0); putInt(4, total)
            "WAVE".toByteArray().copyInto(h, 8); "fmt ".toByteArray().copyInto(h, 12)
            putInt(16, 16); putShort(20, 1); putShort(22, 1); putInt(24, SAMPLE_RATE)
            putInt(28, rate); putShort(32, 2); putShort(34, 16)
            "data".toByteArray().copyInto(h, 36); putInt(40, dataLength)
            return h
        }
    }
}
