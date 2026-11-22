package com.recall.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var durationLabel: TextView
    private lateinit var durationSeek: SeekBar
    private var selectedSeconds = 60
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshStatus()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 32)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        fun text(value: String, size: Float, bold: Boolean = false): TextView = TextView(this).apply {
            text = value; textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF111827.toInt())
            setPadding(0, 12, 0, 12)
        }
        root.addView(text("RECALL", 28f, true))
        status = text("Recording is off", 18f, true)
        root.addView(status)
        root.addView(text("RECALL keeps a temporary rolling audio buffer on this phone. Recording is visible through Android's microphone indicator and notification.", 14f))

        val start = Button(this).apply { text = "Start recording"; setOnClickListener { startCapture() } }
        root.addView(start)
        val stop = Button(this).apply { text = "Stop recording"; setOnClickListener { stopCapture() } }
        root.addView(stop)

        root.addView(text("Choose how much recent audio to recover", 18f, true))
        durationLabel = text("1 min 0 sec", 16f, true)
        root.addView(durationLabel)
        durationSeek = SeekBar(this).apply {
            max = 300
            progress = 60
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedSeconds = (progress + 1).coerceAtMost(300)
                    durationLabel.text = "${selectedSeconds / 60} min ${selectedSeconds % 60} sec"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(durationSeek)
        root.addView(Button(this).apply {
            text = "Recover audio"
            setOnClickListener { recoverAudio() }
        })
        root.addView(Button(this).apply {
            text = "Play recovered audio"
            setOnClickListener { playRecovered() }
        })
        root.addView(text("Prototype only · maximum buffer: 5 minutes · audio stays on-device", 12f))
        setContentView(root)
    }

    private fun startCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10); return
        }
        val intent = Intent(this, AudioCaptureService::class.java).setAction(AudioCaptureService.ACTION_START)
        startForegroundService(intent)
        status.text = "Recording requested · check Android notification"
        Toast.makeText(this, "Recording started when the notification appears", Toast.LENGTH_LONG).show()
    }

    private fun stopCapture() {
        startService(Intent(this, AudioCaptureService::class.java).setAction(AudioCaptureService.ACTION_STOP))
        status.text = "Recording stopped"
    }

    private fun refreshStatus() {
        status.text = if (AudioCaptureService.isRunning) "Recording is active" else "Recording is off"
    }

    private fun recoverAudio() {
        try {
            val out = File(getExternalFilesDir(null), "recall-recovery.wav")
            val recovered = AudioCaptureService.exportLastSeconds(this, selectedSeconds, out)
            if (recovered) {
                Toast.makeText(this, "Recovered audio saved inside app storage", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Not enough audio captured yet. Start recording and try again.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Recovery failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun playRecovered() {
        val file = File(getExternalFilesDir(null), "recall-recovery.wav")
        if (!file.exists()) { Toast.makeText(this, "Recover audio first", Toast.LENGTH_SHORT).show(); return }
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    override fun onDestroy() { player?.release(); super.onDestroy() }
}
