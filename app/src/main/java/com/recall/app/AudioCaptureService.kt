package com.recall.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Rolling audio buffer using MediaRecorder + Opus (24 kbps / 16 kHz mono) stored
 * in rotating OGG chunks. Default buffer window is 24 hours; the user can extend
 * via buffer tiers (see [Billing.BUFFER_TIERS]).
 *
 * Why chunks (not a single ring file):
 *   • Opus frames are variable-length, so a byte-addressed ring doesn't give us
 *     "the last N seconds" directly.
 *   • Each OGG chunk is a self-contained stream, trivially deletable when it
 *     ages past the buffer window.
 *
 * Why rotate on export:
 *   • The chunk currently being written cannot be decoded until MediaRecorder
 *     stops and finalizes the OGG trailer. We force-rotate when the user hits
 *     RECOVER so the latest audio (down to ~200 ms before the tap) is included.
 */
class AudioCaptureService : Service() {
    @Volatile private var recording = false
    private var recorder: MediaRecorder? = null
    private var currentChunkStartMs: Long = 0L
    private var currentChunkFile: File? = null
    private val rotateHandler = Handler(Looper.getMainLooper())
    private val rotateRunnable = Runnable { if (recording) rotateChunk(reason = "scheduled") }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

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

        // Migration: delete legacy raw PCM ring from pre-1.3 installs.
        try {
            val legacy = File(filesDir, "recall-ring.pcm")
            if (legacy.exists()) legacy.delete()
        } catch (_: Exception) {}

        chunksDir().mkdirs()
        recording = true
        isRunning = true
        WatchdogScheduler.ensureSchedules(this)
        WatchdogScheduler.recordHeartbeat(this)
        rotateChunk(reason = "start")
    }

    @Synchronized
    private fun rotateChunk(reason: String) {
        rotateHandler.removeCallbacks(rotateRunnable)
        finalizeRecorder()

        try {
            val now = System.currentTimeMillis()
            val f = File(chunksDir(), "c-$now.ogg")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            r.setAudioEncodingBitRate(BITRATE)
            r.setAudioSamplingRate(SAMPLE_RATE_HZ)
            r.setAudioChannels(1)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            currentChunkFile = f
            currentChunkStartMs = now
            Log.d(TAG, "rotated ($reason) → ${f.name}")
        } catch (e: Exception) {
            Log.e(TAG, "rotate failed", e)
            recording = false
            isRunning = false
            stopSelf()
            return
        }

        cleanupOldChunks()
        // Watchdog uses this to detect whether the service has died silently.
        WatchdogScheduler.recordHeartbeat(this)
        rotateHandler.postDelayed(rotateRunnable, CHUNK_MS)
    }

    @Synchronized
    private fun finalizeRecorder() {
        val r = recorder ?: return
        try { r.stop() } catch (_: Exception) {}
        try { r.release() } catch (_: Exception) {}
        recorder = null
    }

    /**
     * Finalize the currently-recording chunk so its OGG trailer is written and
     * it becomes decodable, then start a fresh chunk. Called from the recovery
     * code path so that audio up to ~now is included.
     */
    @Synchronized
    fun forceRotateForExport() {
        if (!recording) return
        rotateChunk(reason = "export")
    }

    private fun cleanupOldChunks() {
        val cutoff = System.currentTimeMillis() - bufferHours(this) * 3600L * 1000L
        chunksDir().listFiles()?.forEach { f ->
            val ts = chunkTimestamp(f) ?: return@forEach
            if (ts < cutoff) {
                try { f.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun stopCapture() {
        rotateHandler.removeCallbacks(rotateRunnable)
        finalizeRecorder()
        recording = false
        isRunning = false
        currentChunkFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun chunksDir(): File = File(filesDir, CHUNKS_DIR)

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
        instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.recall.app.START"
        const val ACTION_STOP = "com.recall.app.STOP"

        // Must match MainActivity.PREFS — buffer_hours lives in the main app prefs.
        const val PREFS = "recall_prefs"
        const val KEY_BUFFER_HOURS = "buffer_hours"
        const val DEFAULT_BUFFER_HOURS = 24
        const val MAX_BUFFER_HOURS = 168 // 7 days

        private const val TAG = "RecallService"
        private const val CHANNEL_ID = "recall_recording"
        private const val NOTIFICATION_ID = 41
        private const val CHUNKS_DIR = "chunks"
        private const val SAMPLE_RATE_HZ = 16000
        private const val BITRATE = 24000
        // 2-minute chunks: ~0.17 % audio lost at rotation; ≤720 files at 24 hr.
        private const val CHUNK_MS = 2L * 60L * 1000L

        @Volatile var isRunning = false
        @Volatile private var instance: AudioCaptureService? = null

        fun bufferHours(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_BUFFER_HOURS, DEFAULT_BUFFER_HOURS)

        fun setBufferHours(context: Context, hours: Int) {
            val clamped = hours.coerceIn(1, MAX_BUFFER_HOURS)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_BUFFER_HOURS, clamped).apply()
        }

        fun maxSeconds(context: Context): Int = bufferHours(context) * 3600

        fun capturedSeconds(context: Context): Int {
            val chunks = listChunks(context)
            if (chunks.isEmpty()) return 0
            val oldest = chunks.first().startMs
            val now = System.currentTimeMillis()
            return ((now - oldest) / 1000).toInt()
                .coerceAtLeast(0)
                .coerceAtMost(maxSeconds(context))
        }

        /**
         * Export the most recent [seconds] of audio as a WAV file to [output].
         * Returns the actual number of seconds written (0 on failure / no audio).
         */
        fun exportLastSeconds(context: Context, seconds: Int, output: File): Int {
            // Flush the in-progress chunk so its OGG trailer is written and it
            // becomes decodable. No-op if the service isn't running.
            instance?.forceRotateForExport()

            val chunks = listChunks(context)
            if (chunks.isEmpty()) return 0

            val now = System.currentTimeMillis()
            val wantMs = seconds.coerceAtLeast(1)
                .coerceAtMost(maxSeconds(context)).toLong() * 1000L
            val startWantMs = now - wantMs

            // Keep chunks that overlap [startWantMs, now]. Each chunk starts at
            // chunk.startMs; the following chunk's start marks its end.
            val relevant = mutableListOf<ChunkInfo>()
            for (i in chunks.indices) {
                val chunk = chunks[i]
                val nextStart = chunks.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
                if (nextStart > startWantMs) relevant.add(chunk)
            }
            if (relevant.isEmpty()) return 0

            var outputSampleRate = 0
            var outputChannels = 0
            val pcmBuffer = ByteArrayOutputStream()

            for ((i, chunk) in relevant.withIndex()) {
                val trimStartMs = if (i == 0) {
                    (startWantMs - chunk.startMs).coerceAtLeast(0L)
                } else 0L
                val decoded = try {
                    decodeOggOpusToPcm(chunk.file, trimStartMs)
                } catch (e: Exception) {
                    Log.e(TAG, "decode ${chunk.file.name} failed", e); null
                } ?: continue
                if (outputSampleRate == 0) {
                    outputSampleRate = decoded.sampleRate
                    outputChannels = decoded.channels
                }
                pcmBuffer.write(decoded.pcm)
            }

            val pcmBytes = pcmBuffer.toByteArray()
            if (pcmBytes.isEmpty() || outputSampleRate == 0) return 0

            try {
                output.parentFile?.mkdirs()
                output.outputStream().use { out ->
                    out.write(wavHeader(pcmBytes.size, outputSampleRate, outputChannels))
                    out.write(pcmBytes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "WAV write failed", e)
                return 0
            }

            val bytesPerSec = outputSampleRate * outputChannels * 2
            return (pcmBytes.size / bytesPerSec).coerceAtMost(maxSeconds(context))
        }

        private data class ChunkInfo(val file: File, val startMs: Long)

        private fun listChunks(context: Context): List<ChunkInfo> {
            val dir = File(context.filesDir, CHUNKS_DIR)
            if (!dir.exists()) return emptyList()
            return dir.listFiles()?.mapNotNull { f ->
                val ts = chunkTimestamp(f) ?: return@mapNotNull null
                if (f.length() == 0L) return@mapNotNull null
                ChunkInfo(f, ts)
            }?.sortedBy { it.startMs } ?: emptyList()
        }

        private fun chunkTimestamp(f: File): Long? {
            val name = f.name
            if (!name.startsWith("c-") || !name.endsWith(".ogg")) return null
            return name.removePrefix("c-").removeSuffix(".ogg").toLongOrNull()
        }

        private data class DecodedPcm(
            val pcm: ByteArray,
            val sampleRate: Int,
            val channels: Int
        )

        private fun decodeOggOpusToPcm(input: File, trimStartMs: Long = 0L): DecodedPcm? {
            if (!input.exists() || input.length() == 0L) return null
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(input.absolutePath)
            } catch (e: Exception) {
                extractor.release(); return null
            }
            var trackIdx = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { trackIdx = i; break }
            }
            if (trackIdx < 0) { extractor.release(); return null }

            val inFormat = extractor.getTrackFormat(trackIdx)
            extractor.selectTrack(trackIdx)
            val mime = inFormat.getString(MediaFormat.KEY_MIME)!!

            val codec: MediaCodec = try {
                MediaCodec.createDecoderByType(mime).apply {
                    configure(inFormat, null, null, 0)
                    start()
                }
            } catch (e: Exception) {
                extractor.release(); return null
            }

            val output = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            val timeoutUs = 10_000L
            var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE_HZ)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)

            try {
                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inIdx = codec.dequeueInputBuffer(timeoutUs)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(
                                    inIdx, 0, size, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, timeoutUs)
                    if (outIdx >= 0) {
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(outIdx)!!
                            val chunk = ByteArray(info.size)
                            buf.position(info.offset)
                            buf.get(chunk, 0, info.size)
                            output.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = codec.outputFormat
                        sampleRate = newFormat.getInteger(
                            MediaFormat.KEY_SAMPLE_RATE, sampleRate
                        )
                        channels = newFormat.getInteger(
                            MediaFormat.KEY_CHANNEL_COUNT, channels
                        )
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                try { codec.release() } catch (_: Exception) {}
                extractor.release()
            }

            var pcmBytes = output.toByteArray()
            if (trimStartMs > 0 && sampleRate > 0 && pcmBytes.isNotEmpty()) {
                val trimBytes = (trimStartMs * sampleRate / 1000L * channels * 2)
                    .toInt().coerceIn(0, pcmBytes.size)
                pcmBytes = pcmBytes.copyOfRange(trimBytes, pcmBytes.size)
            }
            return DecodedPcm(pcmBytes, sampleRate, channels)
        }

        private fun wavHeader(dataLength: Int, sampleRate: Int, channels: Int): ByteArray {
            val total = dataLength + 36
            val byteRate = sampleRate * channels * 2
            val blockAlign = channels * 2
            val h = ByteArray(44)
            fun putInt(off: Int, v: Int) {
                for (i in 0..3) h[off + i] = (v shr (8 * i)).toByte()
            }
            fun putShort(off: Int, v: Int) {
                h[off] = v.toByte(); h[off + 1] = (v shr 8).toByte()
            }
            "RIFF".toByteArray().copyInto(h, 0); putInt(4, total)
            "WAVE".toByteArray().copyInto(h, 8); "fmt ".toByteArray().copyInto(h, 12)
            putInt(16, 16); putShort(20, 1); putShort(22, channels); putInt(24, sampleRate)
            putInt(28, byteRate); putShort(32, blockAlign); putShort(34, 16)
            "data".toByteArray().copyInto(h, 36); putInt(40, dataLength)
            return h
        }
    }
}
