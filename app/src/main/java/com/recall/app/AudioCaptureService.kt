package com.recall.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

class AudioCaptureService : Service() {
    @Volatile private var recording = false
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture()
            else -> startCapture()
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (recording) return
        createChannel()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return
        }

        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
        val lastCapacity = saved.getInt("capacity", 0)
        if (lastCapacity != CAPACITY_BYTES) {
            saved.edit()
                .putInt("writePosition", 0)
                .putInt("bytesCaptured", 0)
                .putInt("capacity", CAPACITY_BYTES)
                .commit()
            writePosition = 0
            bytesCaptured = 0
            try { File(filesDir, RING_FILE).delete() } catch (_: Exception) {}
        } else {
            writePosition = saved.getInt("writePosition", 0)
            bytesCaptured = saved.getInt("bytesCaptured", 0)
        }

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) { stopSelf(); return }
        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL, ENCODING,
                maxOf(minBuffer, CHUNK_BYTES * 2)
            )
        } catch (_: SecurityException) {
            stopSelf(); return
        }
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release(); stopSelf(); return
        }
        recorder = audio
        recording = true
        isRunning = true
        audio.startRecording()

        worker = thread(name = "RecallAudioCapture") {
            try {
                val ring = File(filesDir, RING_FILE)
                RandomAccessFile(ring, "rw").use { file ->
                    // No upfront setLength: the file grows naturally as writePosition advances
                    // and stays at CAPACITY_BYTES once the ring has wrapped. Avoids a 1.38 GB
                    // allocation on first start that can stall or fail on low-storage devices.
                    val buffer = ByteArray(CHUNK_BYTES)
                    var flushTicks = 0
                    while (recording) {
                        val count = audio.read(buffer, 0, buffer.size)
                        if (count > 0) synchronized(LOCK) {
                            val first = minOf(count, CAPACITY_BYTES - writePosition)
                            file.seek(writePosition.toLong())
                            file.write(buffer, 0, first)
                            if (first < count) {
                                file.seek(0)
                                file.write(buffer, first, count - first)
                            }
                            writePosition = (writePosition + count) % CAPACITY_BYTES
                            bytesCaptured = (bytesCaptured + count).coerceAtMost(CAPACITY_BYTES)
                            flushTicks++
                            if (flushTicks >= FLUSH_EVERY_N_CHUNKS) {
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putInt("writePosition", writePosition)
                                    .putInt("bytesCaptured", bytesCaptured)
                                    .apply()
                                flushTicks = 0
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "capture loop crashed", e)
                recording = false
                isRunning = false
            }
        }
    }

    private fun stopCapture() {
        recording = false
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        try { worker?.join(1500) } catch (_: Exception) {}
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
        val channel = NotificationChannel(
            CHANNEL_ID, "RECALL",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Background service"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RECALL")
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentIntent(pending)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (recording) stopCapture()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.recall.app.START"
        const val ACTION_STOP = "com.recall.app.STOP"

        private const val TAG = "RecallService"
        private const val CHANNEL_ID = "recall_recording"
        private const val NOTIFICATION_ID = 41
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_BYTES = 4096
        // 12 hours of 16 kHz mono 16-bit PCM = 1,382,400,000 bytes (~1.29 GiB, sparse on ext4).
        private const val CAPACITY_BYTES = SAMPLE_RATE * 2 * 3600 * 12
        private const val RING_FILE = "recall-ring.pcm"
        private const val PREFS = "recall_buffer"
        private const val FLUSH_EVERY_N_CHUNKS = 40

        val LOCK = Any()
        @Volatile var isRunning = false
        @Volatile var writePosition = 0
        @Volatile var bytesCaptured = 0

        fun capturedSeconds(context: Context): Int {
            val bytes = if (isRunning) bytesCaptured else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt("bytesCaptured", 0)
            }
            return bytes / (SAMPLE_RATE * 2)
        }

        fun maxSeconds(): Int = CAPACITY_BYTES / (SAMPLE_RATE * 2)

        /**
         * Export the most recent [seconds] of audio as a WAV file to [output].
         * Returns the actual number of seconds written (0 if nothing was captured).
         */
        fun exportLastSeconds(context: Context, seconds: Int, output: File): Int = synchronized(LOCK) {
            if (!isRunning) {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                writePosition = prefs.getInt("writePosition", writePosition)
                bytesCaptured = prefs.getInt("bytesCaptured", bytesCaptured)
            }
            val wanted = seconds.coerceIn(1, maxSeconds()) * SAMPLE_RATE * 2
            if (bytesCaptured == 0) return 0
            val actual = minOf(wanted, bytesCaptured)
            val ring = File(context.filesDir, RING_FILE)
            if (!ring.exists()) return 0
            // With lazy growth, the file is only >= bytesCaptured in size. Reads beyond
            // that would be invalid, but we never issue them: `actual <= bytesCaptured`,
            // and the start point is computed modulo CAPACITY_BYTES so wrap-reads only
            // trigger once the ring has filled, by which point file.length() == CAPACITY_BYTES.
            val start = (writePosition - actual).mod(CAPACITY_BYTES)
            val pcm = ByteArray(actual)
            RandomAccessFile(ring, "r").use { file ->
                val first = minOf(actual, CAPACITY_BYTES - start)
                file.seek(start.toLong())
                file.readFully(pcm, 0, first)
                if (first < actual) {
                    file.seek(0)
                    file.readFully(pcm, first, actual - first)
                }
            }
            output.parentFile?.mkdirs()
            RandomAccessFile(output, "rw").use { file ->
                file.setLength(0)
                file.write(wavHeader(actual))
                file.write(pcm)
            }
            return actual / (SAMPLE_RATE * 2)
        }

        private fun wavHeader(dataLength: Int): ByteArray {
            val total = dataLength + 36
            val byteRate = SAMPLE_RATE * 2
            val h = ByteArray(44)
            fun putInt(offset: Int, value: Int) {
                for (i in 0..3) h[offset + i] = (value shr (8 * i)).toByte()
            }
            fun putShort(offset: Int, value: Int) {
                h[offset] = value.toByte()
                h[offset + 1] = (value shr 8).toByte()
            }
            "RIFF".toByteArray().copyInto(h, 0); putInt(4, total)
            "WAVE".toByteArray().copyInto(h, 8); "fmt ".toByteArray().copyInto(h, 12)
            putInt(16, 16); putShort(20, 1); putShort(22, 1); putInt(24, SAMPLE_RATE)
            putInt(28, byteRate); putShort(32, 2); putShort(34, 16)
            "data".toByteArray().copyInto(h, 36); putInt(40, dataLength)
            return h
        }
    }
}
