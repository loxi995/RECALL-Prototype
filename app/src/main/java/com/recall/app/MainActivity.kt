package com.recall.app

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private enum class PlayState { IDLE, PLAYING, PAUSED }

    private lateinit var prefs: SharedPreferences

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var capturedText: TextView
    private lateinit var alwaysSwitch: Switch
    private lateinit var switchStateLabel: TextView
    private lateinit var durationBtn: Button
    private lateinit var recoverBtn: Button
    private lateinit var playBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var balanceBigText: TextView
    private lateinit var balanceSubText: TextView
    private lateinit var buyBtn: Button

    private var player: MediaPlayer? = null
    private var playState = PlayState.IDLE
    private var durationSec = 60
    private var pulseAnim: ObjectAnimator? = null

    private val handler = Handler(Looper.getMainLooper())
    private val refresh: Runnable = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Billing.ensureInit(this)
        buildUi()
        updateBalance()
        if (!prefs.getBoolean(KEY_TERMS, false)) {
            showDisclosure()
        } else {
            ensurePermissionsAndStart()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BG)
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }
        scroll.addView(
            root,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Header
        root.addView(TextView(this).apply {
            text = "RECALL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = -0.02f
            setPadding(0, 0, 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = "Always-on audio buffer · recover anything you missed"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(24))
        })

        // Balance card
        val balanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0A1A2A.toInt(), 0xFF000000.toInt())
            ).apply {
                shape = GradientDrawable.RECTANGLE
                setStroke(dp(1), COLOR_ACCENT_DIM)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(18), dp(14), dp(14), dp(14))
        }
        balanceCard.addView(TextView(this).apply {
            text = "YOUR BALANCE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(6))
        })
        val balanceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val balanceCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        balanceBigText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = -0.02f
        }
        balanceCol.addView(balanceBigText)
        balanceSubText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, dp(2), 0, 0)
        }
        balanceCol.addView(balanceSubText)
        balanceRow.addView(balanceCol)

        buyBtn = accentBtn("BUY MINUTES") { showPackPicker() }
        balanceRow.addView(buyBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)
        ).apply { marginStart = dp(12) })
        balanceCard.addView(balanceRow)

        root.addView(balanceCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        // Status card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), COLOR_DIVIDER)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_STATUS_OFF)
            }
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                rightMargin = dp(10)
            }
        }
        statusRow.addView(statusDot)
        statusText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
        }
        statusRow.addView(statusText)
        statusCard.addView(statusRow)
        capturedText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
        }
        statusCard.addView(capturedText)
        root.addView(statusCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        // Always listening card — clear ON/OFF badge
        val switchCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), COLOR_DIVIDER)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(18), dp(14), dp(14), dp(14))
        }
        val switchLabels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        switchLabels.addView(TextView(this).apply {
            text = "Always listening"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
        })
        switchStateLabel = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
            setPadding(0, dp(2), 0, 0)
        }
        switchLabels.addView(switchStateLabel)
        switchCard.addView(switchLabels)

        alwaysSwitch = Switch(this).apply {
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(
                    COLOR_STATUS_LIVE,
                    0xFF4A4A4A.toInt()
                )
            )
            scaleX = 1.3f
            scaleY = 1.3f
        }
        switchCard.addView(alwaysSwitch, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(12) })
        root.addView(switchCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })

        // Duration section
        root.addView(TextView(this).apply {
            text = "How far back"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(8))
        })
        durationBtn = secondaryBtn(formatDuration(durationSec)) { showDurationPicker() }
        root.addView(durationBtn)

        // Primary action: Recover
        recoverBtn = primaryBtn("RECOVER AUDIO") { recoverAudio() }
        root.addView(recoverBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(16) })

        // Playback (state-aware)
        playBtn = secondaryBtn("PLAY LAST RECOVERY") { togglePlayRecovery() }
        root.addView(playBtn)

        // Save to device
        saveBtn = secondaryBtn("SAVE TO DEVICE") { saveRecoveryToDevice() }
        root.addView(saveBtn)

        // Footer
        root.addView(hairline(dp(28), dp(16)))
        root.addView(TextView(this).apply {
            text = "Audio stays on this device.\nNothing is uploaded or shared."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(dp(4).toFloat(), 1f)
        })

        setContentView(scroll)

        // Switch state + listener
        alwaysSwitch.isChecked = prefs.getBoolean(KEY_ENABLED, true)
        alwaysSwitch.setOnCheckedChangeListener { btn, checked ->
            onSwitchChanged(btn, checked)
        }

        // Entry animation
        scroll.alpha = 0f
        scroll.translationY = dp(20).toFloat()
        scroll.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setStartDelay(40)
            .setInterpolator(DecelerateInterpolator())
            .start()

        updateStatus()
    }

    private fun primaryBtn(label: String, onClick: () -> Unit): Button {
        val radius = dp(28).toFloat()
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(COLOR_BTN_PRIMARY)
            cornerRadius = radius
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = radius
        }
        return Button(this).apply {
            text = label
            setTextColor(COLOR_BTN_PRIMARY_FG)
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_PRIMARY_RIPPLE),
                content,
                mask
            )
            stateListAnimator = null
            isAllCaps = true
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.04f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setOnClickListener { onClick() }
        }
    }

    private fun secondaryBtn(label: String, onClick: () -> Unit): Button {
        val radius = dp(28).toFloat()
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), COLOR_DIVIDER)
            cornerRadius = radius
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = radius
        }
        return Button(this).apply {
            text = label
            setTextColor(COLOR_TEXT)
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE),
                content,
                mask
            )
            stateListAnimator = null
            isAllCaps = true
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.04f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(10) }
        }
    }

    private fun accentBtn(label: String, onClick: () -> Unit): Button {
        val radius = dp(21).toFloat()
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(COLOR_ACCENT)
            cornerRadius = radius
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = radius
        }
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_ACCENT_RIPPLE),
                content,
                mask
            )
            stateListAnimator = null
            isAllCaps = true
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.06f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(14), 0, dp(14), 0)
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { onClick() }
        }
    }

    private fun hairline(top: Int, bottom: Int): View = View(this).apply {
        setBackgroundColor(COLOR_DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1)
        ).apply { topMargin = top; bottomMargin = bottom }
    }

    private fun showDurationPicker() {
        val dialogRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }

        val headers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        headers.addView(TextView(this).apply {
            text = "Minutes"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headers.addView(TextView(this).apply {
            text = "Seconds"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        dialogRoot.addView(headers)

        val pickers = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        val minutesPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 720 // up to 12 hours
            value = (durationSec / 60).coerceIn(0, 720)
            wrapSelectorWheel = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val secondsPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 59
            value = (durationSec % 60).coerceIn(0, 59)
            wrapSelectorWheel = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setFormatter { String.format(Locale.US, "%02d", it) }
        }
        pickers.addView(minutesPicker)
        pickers.addView(secondsPicker)
        dialogRoot.addView(pickers)

        dialogRoot.addView(TextView(this).apply {
            text = "Minimum 30 seconds. Maximum 12 hours (buffer size)."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })

        AlertDialog.Builder(this)
            .setTitle("How far back?")
            .setView(dialogRoot)
            .setPositiveButton("Set") { _, _ ->
                val total = minutesPicker.value * 60 + secondsPicker.value
                if (total < 30) {
                    durationSec = 30
                    Toast.makeText(this, "Minimum is 30 seconds — set to 30.", Toast.LENGTH_SHORT).show()
                } else {
                    durationSec = total.coerceAtMost(12 * 3600)
                }
                durationBtn.text = formatDuration(durationSec)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Microphone access")
            .setMessage(
                "RECALL continuously captures audio from your microphone while listening is ON. " +
                "This powers the recovery feature — the ability to get back the last up-to-12 hours " +
                "of ambient sound.\n\n" +
                "HOW YOUR AUDIO IS HANDLED\n" +
                "• Stored only on this device, in RECALL's private app storage\n" +
                "• Overwritten automatically every 12 hours (rolling buffer)\n" +
                "• Never uploaded, transmitted, or shared with anyone\n" +
                "• Deleted entirely when you uninstall the app\n\n" +
                "YOUR RESPONSIBILITY\n" +
                "Audio-recording laws vary by region. In many places it is illegal to record a " +
                "conversation without the consent of every participant. You are solely responsible " +
                "for complying with the laws that apply to you.\n\n" +
                "By tapping Agree & Continue you accept the Privacy Policy and Terms of Service."
            )
            .setPositiveButton("Agree & Continue") { _, _ ->
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
        when (requestCode) {
            REQ_MIC -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ensurePermissionsAndStart()
                } else {
                    Toast.makeText(
                        this,
                        "Microphone access is required for RECALL to listen.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            REQ_NOTIF -> ensurePermissionsAndStart()
            REQ_WRITE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveRecoveryToDevice()
                } else {
                    Toast.makeText(
                        this,
                        "Storage permission denied, cannot save.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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
        val running = AudioCaptureService.isRunning

        statusText.text = when {
            !enabled -> "Listening is off"
            running -> "Listening"
            else -> "Starting up…"
        }
        val dotColor = if (enabled && running) COLOR_STATUS_LIVE else COLOR_STATUS_OFF
        (statusDot.background as? GradientDrawable)?.setColor(dotColor)

        if (enabled && running) {
            if (pulseAnim == null) {
                pulseAnim = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.35f, 1f).apply {
                    duration = 1600
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        } else {
            pulseAnim?.cancel()
            pulseAnim = null
            statusDot.alpha = 1f
        }

        switchStateLabel.text = if (enabled) "ON" else "OFF"
        switchStateLabel.setTextColor(
            if (enabled) COLOR_STATUS_LIVE else COLOR_TEXT_MUTED
        )

        val secs = AudioCaptureService.capturedSeconds(this)
        capturedText.text = "Captured so far · ${formatDuration(secs)}"
    }

    private fun updateBalance() {
        val trial = Billing.trialRemainingSec(this)
        val paid = Billing.paidBalanceSec(this)
        val total = trial + paid
        val m = total / 60
        val s = total % 60
        balanceBigText.text = String.format(Locale.US, "%d min %02d sec", m, s)
        balanceBigText.setTextColor(
            if (total == 0) 0xFFFF6B6B.toInt() else COLOR_TEXT
        )
        balanceSubText.text = when {
            trial > 0 && paid == 0 -> "Free trial · ${trial} sec included"
            trial > 0 && paid > 0 -> "Trial ${trial}s + Paid ${paid / 60}m ${paid % 60}s"
            trial == 0 && paid > 0 -> "Paid balance · ${paid / 60} min ${paid % 60} sec"
            else -> "No minutes left — tap BUY MINUTES"
        }
    }

    private fun showPackPicker() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        container.addView(TextView(this).apply {
            text = "Demo mode — tapping a pack simulates a purchase and credits your account instantly. Google Play billing will be wired in before public launch."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(14))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        var dialogRef: AlertDialog? = null

        for (pack in Billing.PACKS) {
            val highlightColor = if (pack.highlight) COLOR_ACCENT else COLOR_DIVIDER
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = RippleDrawable(
                    ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE),
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(if (pack.highlight) 2 else 1), highlightColor)
                        cornerRadius = dp(14).toFloat()
                    },
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.WHITE)
                        cornerRadius = dp(14).toFloat()
                    }
                )
                setPadding(dp(16), dp(14), dp(16), dp(14))
                isClickable = true
                isFocusable = true
            }
            val leftCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            leftCol.addView(TextView(this).apply {
                text = "${pack.minutes} minutes"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
            })
            pack.badge?.let {
                leftCol.addView(TextView(this).apply {
                    text = it.uppercase(Locale.US)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (pack.highlight) COLOR_ACCENT else COLOR_STATUS_LIVE)
                    letterSpacing = 0.12f
                    setPadding(0, dp(2), 0, 0)
                })
            }
            row.addView(leftCol)
            row.addView(TextView(this).apply {
                text = pack.priceUsd
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
            })
            row.setOnClickListener {
                Billing.addMinutes(this, pack.minutes)
                updateBalance()
                Toast.makeText(
                    this,
                    "+${pack.minutes} minutes added (demo).",
                    Toast.LENGTH_SHORT
                ).show()
                dialogRef?.dismiss()
            }
            container.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }

        val scroll = ScrollView(this).apply { addView(container) }
        dialogRef = AlertDialog.Builder(this)
            .setTitle("Buy minutes")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()
        dialogRef.show()
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
        if (s > 0 && h == 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s).append(" sec")
        }
        if (sb.isEmpty()) sb.append("0 seconds")
        return sb.toString()
    }

    private fun recoverAudio() {
        val wantedSec = durationSec
        val availSec = AudioCaptureService.capturedSeconds(this)
        if (availSec <= 0) {
            Toast.makeText(
                this,
                "No audio captured yet. Wait a few seconds after turning listening on.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val need = minOf(wantedSec, availSec)
        if (!Billing.canCover(this, need)) {
            val have = Billing.effectiveSec(this)
            Toast.makeText(
                this,
                "Need ${formatDuration(need)} of minutes — you have ${formatDuration(have)}. Top up to continue.",
                Toast.LENGTH_LONG
            ).show()
            showPackPicker()
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
                Billing.deduct(this, got)
                updateBalance()
                Toast.makeText(
                    this,
                    "Recovered ${formatDuration(got)} — ${formatDuration(got)} deducted. Ready to play or save.",
                    Toast.LENGTH_LONG
                ).show()
                player?.release()
                player = null
                playState = PlayState.IDLE
                updatePlayButton()
            } else {
                Toast.makeText(this, "Nothing to recover yet.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "recovery failed", e)
            Toast.makeText(this, "Recovery failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun togglePlayRecovery() {
        when (playState) {
            PlayState.IDLE -> startPlayback()
            PlayState.PLAYING -> pausePlayback()
            PlayState.PAUSED -> resumePlayback()
        }
    }

    private fun startPlayback() {
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
                    playState = PlayState.IDLE
                    updatePlayButton()
                    true
                }
                setOnCompletionListener {
                    playState = PlayState.IDLE
                    updatePlayButton()
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
            playState = PlayState.PLAYING
            updatePlayButton()
        } catch (e: Exception) {
            Log.e(TAG, "startPlayback failed", e)
            Toast.makeText(
                this,
                "Playback failed: ${e.javaClass.simpleName}: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            playState = PlayState.IDLE
            updatePlayButton()
        }
    }

    private fun pausePlayback() {
        try {
            player?.pause()
            playState = PlayState.PAUSED
            updatePlayButton()
        } catch (e: Exception) {
            Log.e(TAG, "pause failed", e)
        }
    }

    private fun resumePlayback() {
        try {
            player?.start()
            playState = PlayState.PLAYING
            updatePlayButton()
        } catch (e: Exception) {
            Log.e(TAG, "resume failed", e)
        }
    }

    private fun updatePlayButton() {
        playBtn.text = when (playState) {
            PlayState.IDLE -> "PLAY LAST RECOVERY"
            PlayState.PLAYING -> "PAUSE"
            PlayState.PAUSED -> "RESUME"
        }
    }

    private fun saveRecoveryToDevice() {
        val source = File(getExternalFilesDir(null), "recall-recovery.wav")
        if (!source.exists()) {
            Toast.makeText(this, "Recover audio first.", Toast.LENGTH_SHORT).show()
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val filename = "RECALL-recovery-$timestamp.wav"
        var savedUri: Uri? = null
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/RECALL")
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                }
                val uri = contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw RuntimeException("MediaStore insert returned null")
                savedUri = uri
                val out = contentResolver.openOutputStream(uri)
                    ?: throw RuntimeException("openOutputStream returned null")
                out.use { o -> source.inputStream().use { it.copyTo(o) } }
            } else {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQ_WRITE
                    )
                    return
                }
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "RECALL"
                )
                dir.mkdirs()
                val dst = File(dir, filename)
                source.inputStream().use { input ->
                    dst.outputStream().use { input.copyTo(it) }
                }
                savedUri = Uri.fromFile(dst)
            }
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
            Toast.makeText(
                this,
                "Save failed: ${e.javaClass.simpleName}: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(
            this,
            "Saved to Music/RECALL. Opening…",
            Toast.LENGTH_SHORT
        ).show()

        // Launch the user's default audio player directly. First time they'll see
        // the system "Open with" chooser — picking any player + tapping "Always"
        // sets it as default, and every future save opens straight in that app.
        savedUri?.let { uri ->
            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/wav")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                startActivity(playIntent)
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "File saved to Music/RECALL. Open any audio app to play it.",
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
        pulseAnim?.cancel()
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
        const val REQ_WRITE = 12

        const val COLOR_BG = 0xFF000000.toInt()
        const val COLOR_TEXT = 0xFFFFFFFF.toInt()
        const val COLOR_TEXT_MUTED = 0xFF71767B.toInt()
        const val COLOR_DIVIDER = 0xFF2F3336.toInt()
        const val COLOR_STATUS_LIVE = 0xFF00BA7C.toInt()
        const val COLOR_STATUS_OFF = 0xFF71767B.toInt()
        const val COLOR_BTN_PRIMARY = 0xFFFFFFFF.toInt()
        const val COLOR_BTN_PRIMARY_FG = 0xFF000000.toInt()
        const val COLOR_BTN_PRIMARY_RIPPLE = 0x33000000
        const val COLOR_BTN_SECONDARY_RIPPLE = 0x33FFFFFF
        const val COLOR_ACCENT = 0xFF1D9BF0.toInt()
        const val COLOR_ACCENT_DIM = 0xFF1A4766.toInt()
        const val COLOR_ACCENT_RIPPLE = 0x33FFFFFF
    }
}
