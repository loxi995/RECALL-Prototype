package com.recall.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var captured: TextView
    private lateinit var alwaysSwitch: Switch
    private lateinit var durationSpinner: Spinner
    private var player: MediaPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val refresh: Runnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 5_000)
        }
    }

    private val presetSeconds = intArrayOf(
        30, 60, 300, 900, 1800, 3600, 7200, 14400, 28800, 43200
    )
    private val presetLabels = arrayOf(
        "30 seconds", "1 minute", "5 minutes", "15 minutes", "30 minutes",
        "1 hour", "2 hours", "4 hours", "8 hours", "12 hours"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        buildUi()
        if (!prefs.getBoolean(KEY_TERMS, false)) {
            showDisclosure()
        } else {
            ensurePermissionsAndStart()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.WHITE) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }
        scroll.addView(
            root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(TextView(this).apply {
            text = "RECALL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF111827.toInt())
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Always-on audio buffer. Recover any moment you forgot to record."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xFF4B5563.toInt())
            setPadding(0, 0, 0, dp(24))
        })

        status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(status)

        captured = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xFF4B5563.toInt())
            setPadding(0, 0, 0, dp(24))
        }
        root.addView(captured)

        root.addView(divider())

        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(8))
        }
        switchRow.addView(TextView(this).apply {
            text = "Always listening"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF111827.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        })
        alwaysSwitch = Switch(this)
        switchRow.addView(alwaysSwitch)
        root.addView(switchRow)
        root.addView(TextView(this).apply {
            text = "Keeps a rolling 12-hour audio buffer on this device. If you turn this off, you will not be able to recover anything you miss while off."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(divider())

        root.addView(TextView(this).apply {
            text = "How far back do you want to recover?"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF111827.toInt())
            setPadding(0, dp(16), 0, dp(8))
        })

        durationSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                presetLabels
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setSelection(5)
        }
        root.addView(
            durationSpinner,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        )

        root.addView(bigButton(
            "RECOVER AUDIO",
            0xFF111827.toInt(), Color.WHITE
        ) { recoverAudio() })
        root.addView(bigButton(
            "PLAY LAST RECOVERY",
            0xFFE5E7EB.toInt(), 0xFF111827.toInt()
        ) { playRecovered() })
        root.addView(bigButton(
            "SAVE RECOVERY TO DEVICE",
            0xFFE5E7EB.toInt(), 0xFF111827.toInt()
        ) { saveRecoveryToDevice() })

        root.addView(divider())
        root.addView(TextView(this).apply {
            text = "Audio stays on this device. Nothing is uploaded or shared."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, dp(16), 0, 0)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        setContentView(scroll)

        alwaysSwitch.isChecked = prefs.getBoolean(KEY_ENABLED, true)
        alwaysSwitch.setOnCheckedChangeListener { btn, checked ->
            onSwitchChanged(btn, checked)
        }
    }

    private fun bigButton(label: String, bg: Int, fg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(fg)
            setBackgroundColor(bg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ).apply { topMargin = dp(8) }
        }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(0xFFE5E7EB.toInt())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { topMargin = dp(8); bottomMargin = dp(8) }
    }

    private fun showDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Before you start")
            .setMessage(
                "RECALL continuously records audio in your surroundings while listening is ON.\n\n" +
                "All audio stays on your device only. Nothing is uploaded, shared, or sent anywhere.\n\n" +
                "You are responsible for complying with recording laws in your location. In some places, recording a conversation without everyone's consent is illegal."
            )
            .setPositiveButton("I understand") { _, _ ->
                prefs.edit().putBoolean(KEY_TERMS, true).apply()
                ensurePermissionsAndStart()
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun ensurePermissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            return
        }
        if (prefs.getBoolean(KEY_ENABLED, true)) {
            startCaptureService()
        }
        maybePromptBatteryOpt()
        updateStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ensurePermissionsAndStart()
            } else {
                Toast.makeText(
                    this,
                    "Microphone access is required for RECALL to listen.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == REQ_NOTIF) {
            ensurePermissionsAndStart()
        }
    }

    private fun maybePromptBatteryOpt() {
        if (prefs.getBoolean(KEY_ASKED_BATT, false)) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            prefs.edit().putBoolean(KEY_ASKED_BATT, true).apply()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Keep RECALL running in the background")
            .setMessage(
                "Samsung aggressively kills background apps to save battery. For RECALL to keep listening when the screen is off, exempt it from battery optimization."
            )
            .setPositiveButton("Open settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {}
                }
                prefs.edit().putBoolean(KEY_ASKED_BATT, true).apply()
            }
            .setNegativeButton("Later") { _, _ -> }
            .show()
    }

    private fun onSwitchChanged(btn: CompoundButton, checked: Boolean) {
        if (checked) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startCaptureService()
            } else {
                ensurePermissionsAndStart()
            }
            updateStatus()
        } else {
            setSwitchSilently(true)
            AlertDialog.Builder(this)
                .setTitle("Turn RECALL off?")
                .setMessage(
                    "If you turn RECALL off, you will not be able to recover audio from any conversation, meeting, class session, or moment you forget to record manually.\n\nAre you sure?"
                )
                .setPositiveButton("Turn off") { _, _ ->
                    prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                    stopCaptureService()
                    setSwitchSilently(false)
                    updateStatus()
                }
                .setNegativeButton("Keep listening") { _, _ -> }
                .show()
        }
    }

    private fun setSwitchSilently(checked: Boolean) {
        alwaysSwitch.setOnCheckedChangeListener(null)
        alwaysSwitch.isChecked = checked
        alwaysSwitch.setOnCheckedChangeListener { b, c -> onSwitchChanged(b, c) }
    }

    private fun startCaptureService() {
        try {
            val intent = Intent(this, AudioCaptureService::class.java)
                .setAction(AudioCaptureService.ACTION_START)
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
            Toast.makeText(
                this,
                "Could not start listening: ${e.javaClass.simpleName}: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopCaptureService() {
        try {
            val intent = Intent(this, AudioCaptureService::class.java)
                .setAction(AudioCaptureService.ACTION_STOP)
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "stopService failed", e)
        }
    }

    private fun updateStatus() {
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        status.text = when {
            !enabled -> "Listening is OFF"
            AudioCaptureService.isRunning -> "Listening · recording in background"
            else -> "Listening · starting up…"
        }
        status.setTextColor(if (enabled) 0xFF059669.toInt() else 0xFF9CA3AF.toInt())
        val secs = AudioCaptureService.capturedSeconds(this)
        captured.text = "Captured so far: ${formatDuration(secs)}"
    }

    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "0 seconds"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val sb = StringBuilder()
        if (h > 0) sb.append(h).append(" hr")
        if (m > 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(m).append(" min")
        }
        if (h == 0 && s > 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s).append(" sec")
        }
        if (sb.isEmpty()) sb.append(s).append(" sec")
        return sb.toString()
    }

    private fun recoverAudio() {
        val idx = durationSpinner.selectedItemPosition.coerceAtLeast(0)
        val wantedSec = presetSeconds[idx]
        val availSec = AudioCaptureService.capturedSeconds(this)
        if (availSec <= 0) {
            Toast.makeText(
                this,
                "No audio captured yet. Wait a few seconds after turning listening on.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (availSec < wantedSec) {
            Toast.makeText(
                this,
                "Only ${formatDuration(availSec)} captured so far. Recovering what's available.",
                Toast.LENGTH_LONG
            ).show()
        }
        try {
            val out = File(getExternalFilesDir(null), "recall-recovery.wav")
            val got = AudioCaptureService.exportLastSeconds(this, wantedSec, out)
            if (got > 0) {
                Toast.makeText(
                    this,
                    "Recovered ${formatDuration(got)}. Ready to play.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Nothing to recover yet.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Recovery failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun playRecovered() {
        val file = File(getExternalFilesDir(null), "recall-recovery.wav")
        if (!file.exists()) {
            Toast.makeText(this, "Recover audio first.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(
                        this@MainActivity,
                        "Playback error: $what/$extra",
                        Toast.LENGTH_LONG
                    ).show()
                    true
                }
                setOnCompletionListener {
                    Toast.makeText(
                        this@MainActivity,
                        "Playback finished.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(
                this,
                "Playing recovered audio. Make sure volume is up.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "playback failed", e)
            Toast.makeText(
                this,
                "Playback failed: ${e.javaClass.simpleName}: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveRecoveryToDevice() {
        val file = File(getExternalFilesDir(null), "recall-recovery.wav")
        if (!file.exists()) {
            Toast.makeText(this, "Recover audio first.", Toast.LENGTH_SHORT).show()
            return
        }
        val timestamp = java.text.SimpleDateFormat(
            "yyyyMMdd-HHmmss",
            java.util.Locale.US
        ).format(java.util.Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, "RECALL-recovery-$timestamp.wav")
        }
        try {
            startActivityForResult(intent, REQ_SAVE)
        } catch (e: Exception) {
            Log.e(TAG, "save picker failed", e)
            Toast.makeText(
                this,
                "No file-picker app available on this device.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SAVE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val source = File(getExternalFilesDir(null), "recall-recovery.wav")
                contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(this, "Saved.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                Toast.makeText(
                    this,
                    "Save failed: ${e.javaClass.simpleName}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecallActivity"
        const val PREFS = "recall_prefs"
        const val KEY_TERMS = "accepted_terms"
        const val KEY_ENABLED = "enabled"
        const val KEY_ASKED_BATT = "asked_battery_opt"
        const val REQ_MIC = 10
        const val REQ_NOTIF = 11
        const val REQ_SAVE = 20
    }
}
