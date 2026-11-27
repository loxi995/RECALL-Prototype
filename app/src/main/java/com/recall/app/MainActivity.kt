package com.recall.app

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
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
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
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
    private lateinit var bufferTitleText: TextView
    private lateinit var bufferSubText: TextView
    private lateinit var alertsTitleText: TextView
    private lateinit var alertsSubText: TextView

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

    // Bottom-nav tab state (0 = Home, 1 = Search, 2 = Profile)
    private var currentTab: Int = 0
    private lateinit var tabHome: View
    private lateinit var tabSearch: View
    private lateinit var tabProfile: View
    private lateinit var navHomeBtn: LinearLayout
    private lateinit var navSearchBtn: LinearLayout
    private lateinit var navProfileBtn: LinearLayout
    // Horizontally-paging container holding the 3 tab views side by side
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: TabPagerAdapter

    // Theme-aware color accessors. Existing code referencing COLOR_BG etc.
    // continues to work — Kotlin resolves the property on `this`, ignoring the
    // older companion-level constants of the same name (which have been renamed).
    private fun isDarkTheme(): Boolean =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_THEME, DEFAULT_DARK_THEME)

    private val COLOR_BG: Int get() = if (isDarkTheme()) DARK_BG else LIGHT_BG
    private val COLOR_TEXT: Int get() = if (isDarkTheme()) DARK_TEXT else LIGHT_TEXT
    private val COLOR_TEXT_MUTED: Int get() =
        if (isDarkTheme()) DARK_TEXT_MUTED else LIGHT_TEXT_MUTED
    private val COLOR_DIVIDER: Int get() =
        if (isDarkTheme()) DARK_DIVIDER else LIGHT_DIVIDER
    private val COLOR_BTN_PRIMARY: Int get() =
        if (isDarkTheme()) DARK_BTN_PRIMARY else LIGHT_BTN_PRIMARY
    private val COLOR_BTN_PRIMARY_FG: Int get() =
        if (isDarkTheme()) DARK_BTN_PRIMARY_FG else LIGHT_BTN_PRIMARY_FG
    private val COLOR_BTN_PRIMARY_RIPPLE: Int get() =
        if (isDarkTheme()) DARK_BTN_PRIMARY_RIPPLE else LIGHT_BTN_PRIMARY_RIPPLE
    private val COLOR_BTN_SECONDARY_RIPPLE: Int get() =
        if (isDarkTheme()) DARK_BTN_SECONDARY_RIPPLE else LIGHT_BTN_SECONDARY_RIPPLE
    private val COLOR_ACCENT_DIM: Int get() =
        if (isDarkTheme()) DARK_ACCENT_DIM else LIGHT_ACCENT_DIM
    private val COLOR_ACCENT_RIPPLE: Int get() =
        if (isDarkTheme()) DARK_ACCENT_RIPPLE else LIGHT_ACCENT_RIPPLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        currentTab = savedInstanceState?.getInt(STATE_CURRENT_TAB, 0) ?: 0
        Billing.ensureInit(this)
        buildUi()
        updateBalance()
        updateBuffer()
        updateAlerts()
        WatchdogScheduler.ensureSchedules(this)
        if (!prefs.getBoolean(KEY_TERMS, false)) {
            showDisclosure()
        } else {
            ensurePermissionsAndStart()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_TAB, currentTab)
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

        // Balance card — theme-aware gradient
        val balanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val gradientColors = if (isDarkTheme()) {
                intArrayOf(0xFF0A1A2A.toInt(), 0xFF000000.toInt())
            } else {
                intArrayOf(0xFFE8F4FD.toInt(), 0xFFFFFFFF.toInt())
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                gradientColors
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

        balanceCard.addView(balanceRow)
        balanceCard.isClickable = true
        balanceCard.isFocusable = true
        balanceCard.setOnClickListener { showPackPicker() }

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
        ).apply { bottomMargin = dp(16) })

        // Buffer window card — how far back the service keeps audio on the device
        val bufferCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), COLOR_DIVIDER)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(18), dp(14), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { showBufferTierPicker() }
        }
        val bufferCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        bufferCol.addView(TextView(this).apply {
            text = "BUFFER WINDOW"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(4))
        })
        bufferTitleText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
        }
        bufferCol.addView(bufferTitleText)
        bufferSubText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, dp(2), 0, 0)
        }
        bufferCol.addView(bufferSubText)
        bufferCard.addView(bufferCol)
        bufferCard.addView(TextView(this).apply {
            text = "EXTEND ▸"
            setTextColor(COLOR_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.14f
            setPadding(dp(10), 0, dp(2), 0)
        })
        root.addView(bufferCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        // Alerts card — event-based notifications (watchdog + weekly check-in)
        val alertsCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), COLOR_DIVIDER)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(18), dp(14), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { showAlertsPicker() }
        }
        val alertsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        alertsCol.addView(TextView(this).apply {
            text = "ALERTS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(4))
        })
        alertsTitleText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
        }
        alertsCol.addView(alertsTitleText)
        alertsSubText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, dp(2), 0, 0)
        }
        alertsCol.addView(alertsSubText)
        alertsCard.addView(alertsCol)
        alertsCard.addView(TextView(this).apply {
            text = "CHANGE ▸"
            setTextColor(COLOR_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.14f
            setPadding(dp(10), 0, dp(2), 0)
        })
        root.addView(alertsCard, LinearLayout.LayoutParams(
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

        // Alternate flow: pick an explicit start / end time instead of "last N"
        root.addView(secondaryBtn("OR PICK A SPECIFIC TIME RANGE") { showTimeRangePicker() })

        // Playback (state-aware)
        playBtn = secondaryBtn("PLAY LAST RECOVERY") { togglePlayRecovery() }
        root.addView(playBtn)

        // Save to device
        saveBtn = secondaryBtn("SAVE TO DEVICE") { saveRecoveryToDevice() }
        root.addView(saveBtn)

        // How to use link
        val helpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(22), 0, 0)
        }
        helpRow.addView(TextView(this).apply {
            text = "HOW TO USE RECALL"
            setTextColor(COLOR_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 0.14f
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE), null, null
            )
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { showHelpDialog() }
        })
        root.addView(helpRow)

        // Footer
        root.addView(hairline(dp(28), dp(16)))
        root.addView(TextView(this).apply {
            text = "Audio stays on this device.\nNothing is uploaded or shared."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(dp(4).toFloat(), 1f)
        })

        // Horizontal pager: Home + Search + Profile — ViewPager2 handles page
        // sizing, touch arbitration (vertical scroll vs horizontal swipe),
        // physics-based snap and fling for free.
        tabHome = scroll
        tabSearch = buildSearchContent()
        tabProfile = buildProfileContent()

        pagerAdapter = TabPagerAdapter(listOf(tabHome, tabSearch, tabProfile))
        viewPager = ViewPager2(this).apply {
            adapter = pagerAdapter
            // Keep all pages alive so swiping doesn't re-inflate.
            offscreenPageLimit = pagerAdapter.itemCount - 1
            // Default orientation is HORIZONTAL — swipe left/right.
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentTab = position
                    tintNavTab(navHomeBtn, 0)
                    tintNavTab(navSearchBtn, 1)
                    tintNavTab(navProfileBtn, 2)
                }
            })
            setCurrentItem(currentTab, false)
        }

        val rootFrame = FrameLayout(this).apply { setBackgroundColor(COLOR_BG) }
        rootFrame.addView(
            viewPager,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Floating top-right BUY MINUTES pill — visible across all tabs
        buyBtn = buyPill { showPackPicker() }
        rootFrame.addView(
            buyBtn,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(52)
                rightMargin = dp(20)
            }
        )

        // Bottom navigation bar — vertical root wraps content + nav
        val bottomNav = buildBottomNav()
        val verticalRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
        }
        verticalRoot.addView(
            rootFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        verticalRoot.addView(
            bottomNav,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
            )
        )
        setContentView(verticalRoot)
        switchToTab(currentTab)

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
        buyBtn.alpha = 0f
        buyBtn.animate()
            .alpha(1f)
            .setStartDelay(260)
            .setDuration(300)
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

    /** Floating top-right neon-stroked BUY MINUTES pill. */
    private fun buyPill(onClick: () -> Unit): Button {
        val radius = dp(12).toFloat()
        val fillColor = if (isDarkTheme()) 0xCC000000.toInt() else 0xCCFFFFFF.toInt()
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(dp(2), COLOR_ACCENT)
            cornerRadius = radius
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = radius
        }
        return Button(this).apply {
            text = "+ BUY MIN"
            setTextColor(COLOR_ACCENT)
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_ACCENT_RIPPLE),
                content,
                mask
            )
            stateListAnimator = null
            isAllCaps = true
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.14f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(12), 0, dp(12), 0)
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { onClick() }
        }
    }

    /**
     * Theme-aware rounded dialog background + window blur behind (API 31+).
     *
     * Also caps the dialog's height after it's measured so there's always a
     * visible tappable gap at the top/bottom — tapping outside the window
     * dismisses the dialog (the standard Android convention).
     */
    private fun styledDialog(contentView: View): Dialog {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(contentView)
        dlg.setCancelable(true)
        dlg.setCanceledOnTouchOutside(true)
        dlg.window?.let { w ->
            val bgColor = if (isDarkTheme()) 0xF2000000.toInt() else 0xFAFFFFFF.toInt()
            w.setBackgroundDrawable(GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bgColor)
                setStroke(dp(1), COLOR_DIVIDER)
                cornerRadius = dp(22).toFloat()
            })
            val lp = w.attributes
            // Narrower + shorter leaves tap-outside dead zones on all four sides.
            lp.width = (resources.displayMetrics.widthPixels * 0.86f).toInt()
            w.attributes = lp
            w.setDimAmount(0.7f)
            if (Build.VERSION.SDK_INT >= 31) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attrs = w.attributes
                try {
                    val f = WindowManager.LayoutParams::class.java.getField("blurBehindRadius")
                    f.setInt(attrs, 40)
                    w.attributes = attrs
                } catch (_: Exception) { /* feature not available on this build */ }
            }
        }
        // Only cap when the content is taller than 75 % of the screen — small
        // dialogs (turn-off confirm etc.) keep their natural size.
        dlg.setOnShowListener {
            val decor = dlg.window?.decorView ?: return@setOnShowListener
            decor.post {
                val maxH = (resources.displayMetrics.heightPixels * 0.75f).toInt()
                if (decor.height > maxH) {
                    val attrs = dlg.window?.attributes ?: return@post
                    attrs.height = maxH
                    dlg.window?.attributes = attrs
                }
            }
        }
        return dlg
    }

    /** Red destructive button for confirm-off style dialogs. */
    private fun dangerBtn(label: String, onClick: () -> Unit): Button {
        val radius = dp(24).toFloat()
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xFFE0245E.toInt())
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
                ColorStateList.valueOf(0x33FFFFFF),
                content,
                mask
            )
            stateListAnimator = null
            isAllCaps = true
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.04f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
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
        val bufferHrs = AudioCaptureService.bufferHours(this)
        val maxMinutes = bufferHrs * 60
        val minutesPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = maxMinutes
            value = (durationSec / 60).coerceIn(0, maxMinutes)
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
            text = "Minimum 30 seconds. Maximum ${formatBufferHours(bufferHrs)} (buffer window)."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })

        // Allow the internal EditText inside each NumberPicker to receive typed input
        // and make the typed value commit on confirm (must call clearFocus first).
        minutesPicker.descendantFocusability = NumberPicker.FOCUS_BEFORE_DESCENDANTS
        secondsPicker.descendantFocusability = NumberPicker.FOCUS_BEFORE_DESCENDANTS

        AlertDialog.Builder(this)
            .setTitle("How far back?")
            .setView(dialogRoot)
            .setPositiveButton("Set") { _, _ ->
                // Commit any in-progress typed value before reading .value
                minutesPicker.clearFocus()
                secondsPicker.clearFocus()
                val total = minutesPicker.value * 60 + secondsPicker.value
                if (total < 30) {
                    durationSec = 30
                    Toast.makeText(this, "Minimum is 30 seconds — set to 30.", Toast.LENGTH_SHORT).show()
                } else {
                    durationSec = total.coerceAtMost(AudioCaptureService.bufferHours(this) * 3600)
                }
                durationBtn.text = formatDuration(durationSec)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDisclosure() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
        }

        content.addView(TextView(this).apply {
            text = "WELCOME TO RECALL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(12))
        })

        content.addView(TextView(this).apply {
            text = "RECALL continuously captures audio from your microphone while listening is ON. This powers the recovery feature — the ability to get back the last several hours (or days) of ambient sound on demand."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(16))
        })

        content.addView(TextView(this).apply {
            text = "HOW YOUR AUDIO IS HANDLED"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(6))
        })
        content.addView(TextView(this).apply {
            text = "• Stored only on this device, in RECALL's private app storage\n" +
                "• Overwritten automatically on a rolling schedule\n" +
                "• Never uploaded, transmitted, or shared with anyone\n" +
                "• Deleted entirely when you uninstall the app"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, 0, 0, dp(16))
        })

        content.addView(TextView(this).apply {
            text = "YOUR RESPONSIBILITY"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(6))
        })
        content.addView(TextView(this).apply {
            text = "Audio-recording laws vary by region. In many places it is illegal to record a conversation without the consent of every participant. You are solely responsible for complying with the laws that apply to you."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, 0, 0, dp(18))
        })

        // Clickable links to the full legal documents, opened in the browser
        val linkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }
        linkRow.addView(legalLink("PRIVACY POLICY", PRIVACY_POLICY_URL).apply {
            setPadding(0, dp(6), dp(14), dp(6))
        })
        linkRow.addView(legalLink("TERMS OF SERVICE", TERMS_URL).apply {
            setPadding(dp(14), dp(6), 0, dp(6))
        })
        content.addView(linkRow)

        content.addView(TextView(this).apply {
            text = "By tapping Agree & Continue you accept the Privacy Policy and Terms of Service."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(20))
        })

        var dlgRef: Dialog? = null
        // Stacked buttons — primary agree on top, ghost exit below. Full-width
        // keeps the "I AGREE" label on one line on every screen size.
        val agreeBtn = primaryBtn("I AGREE") {
            prefs.edit().putBoolean(KEY_TERMS, true).apply()
            dlgRef?.dismiss()
            ensurePermissionsAndStart()
        }
        agreeBtn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        )
        content.addView(agreeBtn)

        val exitBtn = secondaryBtn("Exit") {
            dlgRef?.dismiss()
            finish()
        }
        exitBtn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ).apply { topMargin = dp(10) }
        content.addView(exitBtn)

        val scroll = ScrollView(this).apply { addView(content) }
        dlgRef = styledDialog(scroll)
        dlgRef.setCancelable(false)
        dlgRef.show()
    }

    // ────────────────────────────────────────────────────────────
    // Bottom navigation + tab content builders
    // ────────────────────────────────────────────────────────────

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(COLOR_BG)
        }
        // Thin divider across the top edge of the nav
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
        }
        wrapper.addView(View(this).apply {
            setBackgroundColor(COLOR_DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        })
        navHomeBtn = buildNavTab("HOME", R.drawable.ic_nav_home, 0)
        navSearchBtn = buildNavTab("SEARCH", R.drawable.ic_nav_search, 1)
        navProfileBtn = buildNavTab("PROFILE", R.drawable.ic_nav_profile, 2)
        nav.addView(navHomeBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ))
        nav.addView(navSearchBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ))
        nav.addView(navProfileBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ))
        wrapper.addView(nav, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        return wrapper
    }

    private fun buildNavTab(label: String, iconRes: Int, index: Int): LinearLayout {
        val isActive = (currentTab == index)
        val tint = if (isActive) COLOR_ACCENT else COLOR_TEXT_MUTED
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE), null, null
            )
            setOnClickListener { switchToTab(index) }
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(tint)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            })
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(tint)
                letterSpacing = 0.14f
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun switchToTab(index: Int) {
        val clamped = index.coerceIn(0, pagerAdapter.itemCount - 1)
        // ViewPager2's own OnPageChangeCallback will update currentTab + nav tint.
        viewPager.setCurrentItem(clamped, true)
    }

    /**
     * Adapter that hosts pre-built tab Views (not re-inflated templates). Each
     * position gets its own view type so RecyclerView keeps a dedicated holder
     * per page and never recycles between them.
     */
    private class TabPagerAdapter(
        private val pages: List<View>
    ) : RecyclerView.Adapter<TabPagerAdapter.VH>() {
        class VH(val root: FrameLayout) : RecyclerView.ViewHolder(root)

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val root = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val page = pages[viewType]
            (page.parent as? ViewGroup)?.removeView(page)
            root.addView(page, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            return VH(root)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            // No-op: page content is bound in onCreateViewHolder since each
            // position has its own view type.
        }

        override fun getItemCount(): Int = pages.size
    }

    private fun tintNavTab(btn: LinearLayout, index: Int) {
        val isActive = (currentTab == index)
        val color = if (isActive) COLOR_ACCENT else COLOR_TEXT_MUTED
        (btn.getChildAt(0) as? ImageView)?.imageTintList = ColorStateList.valueOf(color)
        (btn.getChildAt(1) as? TextView)?.setTextColor(color)
    }

    private fun buildSearchContent(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BG)
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }
        scroll.addView(root, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(TextView(this).apply {
            text = "SEARCH"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = -0.02f
            setPadding(0, 0, 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = "Find a specific moment in your rolling buffer"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(24))
        })

        // Quick action: open the time-range picker
        root.addView(primaryBtn("PICK A TIME RANGE") { showTimeRangePicker() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ))

        root.addView(TextView(this).apply {
            text = "Pick a start and end time in your local timezone. RECALL returns exactly that slice from the on-device buffer."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(14), 0, dp(26))
        })

        root.addView(hairline(dp(0), dp(16)))
        root.addView(TextView(this).apply {
            text = "SAVED RECOVERIES"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(10))
        })
        root.addView(TextView(this).apply {
            text = "WAV files you've exported via SAVE TO DEVICE appear in your Music folder under RECALL. Open any file manager or music app to browse them — this list view is coming soon."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        return scroll
    }

    private fun buildProfileContent(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BG)
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(32))
        }
        scroll.addView(root, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(TextView(this).apply {
            text = "PROFILE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = -0.02f
            setPadding(0, 0, 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = "Account, settings, and quick links"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(24))
        })

        // Theme toggle card
        val themeCard = LinearLayout(this).apply {
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
        val themeCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        themeCol.addView(TextView(this).apply {
            text = "APPEARANCE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(4))
        })
        themeCol.addView(TextView(this).apply {
            text = if (isDarkTheme()) "Dark mode" else "Light mode"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
        })
        themeCol.addView(TextView(this).apply {
            text = "Toggle to switch between dark and light themes"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, dp(2), 0, 0)
        })
        themeCard.addView(themeCol)
        themeCard.addView(Switch(this).apply {
            isChecked = isDarkTheme()
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(COLOR_ACCENT, 0xFF4A4A4A.toInt())
            )
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_DARK_THEME, checked).apply()
                recreate()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(12) })
        root.addView(themeCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })

        // Quick links
        root.addView(profileLinkRow("How to use RECALL") { showHelpDialog() })
        root.addView(profileLinkRow("Buy minutes") { showPackPicker() })
        root.addView(profileLinkRow("Extend buffer window") { showBufferTierPicker() })
        root.addView(profileLinkRow("Configure alerts") { showAlertsPicker() })
        root.addView(hairline(dp(20), dp(20)))

        // Legal
        root.addView(profileLinkRow("Privacy policy") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }
            catch (_: Exception) {}
        })
        root.addView(profileLinkRow("Terms of service") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))) }
            catch (_: Exception) {}
        })

        // Version footer
        root.addView(hairline(dp(28), dp(16)))
        root.addView(TextView(this).apply {
            text = try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                "RECALL v${pi.versionName}\nAudio stays on this device."
            } catch (_: Exception) {
                "RECALL\nAudio stays on this device."
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(dp(4).toFloat(), 1f)
        })
        return scroll
    }

    private fun profileLinkRow(label: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE),
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(12).toFloat()
                },
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.WHITE)
                    cornerRadius = dp(12).toFloat()
                }
            )
            setPadding(dp(14), dp(16), dp(14), dp(16))
            addView(TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            addView(TextView(context).apply {
                text = "▸"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(COLOR_TEXT_MUTED)
            })
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
    }

    private fun showHelpDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
        }

        content.addView(TextView(this).apply {
            text = "HOW TO USE RECALL"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(6))
        })
        content.addView(TextView(this).apply {
            text = "RECALL is an audio time machine. It quietly keeps a rolling buffer of ambient sound so you can go back and grab anything you missed."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(18))
        })

        val steps = listOf(
            Triple(
                "TURN ON LISTENING",
                "Toggle the Always Listening switch to ON. RECALL starts quietly capturing audio in the background. The green dot in the status bar is an Android privacy indicator — not something RECALL controls.",
                R.drawable.help_1_switch
            ),
            Triple(
                "LET IT CAPTURE",
                "Keep the app running in the background. You can lock your phone, open other apps — RECALL keeps listening. 'Captured so far' shows how much audio is in the buffer.",
                R.drawable.help_2_capture
            ),
            Triple(
                "PICK HOW FAR BACK",
                "Tap 'How far back' and spin the wheels to choose a duration (30 seconds up to your buffer limit). You can also tap a number to type it directly.",
                R.drawable.help_3_duration
            ),
            Triple(
                "RECOVER THE AUDIO",
                "Tap RECOVER AUDIO. RECALL grabs that slice from the buffer, decodes it, and makes it ready to play.",
                R.drawable.help_4_recover
            ),
            Triple(
                "PLAY & SAVE",
                "Tap PLAY LAST RECOVERY to listen. Tap SAVE TO DEVICE to write it as a WAV file in your Music/RECALL folder — permanent and shareable.",
                R.drawable.help_5_playsave
            ),
            Triple(
                "BUY MINUTES",
                "Each recovery costs minutes from your balance. New users get a 60-second free trial. Tap +BUY MIN at the top-right to add more.",
                R.drawable.help_6_buymin
            ),
            Triple(
                "EXTEND YOUR BUFFER",
                "Default is 24 hours of audio kept on-device. Tap the BUFFER WINDOW card to extend up to 7 days (optional upgrade).",
                R.drawable.help_7_buffer
            ),
        )

        for ((i, step) in steps.withIndex()) {
            val stepContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(22))
            }
            val textRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val num = TextView(this).apply {
                text = "${i + 1}"
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(COLOR_ACCENT)
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    topMargin = dp(2); rightMargin = dp(12)
                }
            }
            textRow.addView(num)
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            col.addView(TextView(this).apply {
                text = step.first
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
                letterSpacing = 0.1f
                setPadding(0, 0, 0, dp(2))
            })
            col.addView(TextView(this).apply {
                text = step.second
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(COLOR_TEXT_MUTED)
                setLineSpacing(dp(3).toFloat(), 1f)
            })
            textRow.addView(col)
            stepContainer.addView(textRow)

            // Mockup image with arrow showing exactly where to tap
            stepContainer.addView(ImageView(this).apply {
                setImageResource(step.third)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                    leftMargin = dp(40) // align under the text column (past the number circle)
                }
            })

            content.addView(stepContainer)
        }

        content.addView(TextView(this).apply {
            text = "TIPS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, dp(4), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "• Add RECALL to your battery-optimization whitelist so Samsung doesn't kill the service in your pocket.\n" +
                "• Audio never leaves your phone. Uninstalling the app deletes every byte instantly.\n" +
                "• You are responsible for recording laws in your region — don't use RECALL on people who haven't consented."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, 0, 0, dp(18))
        })

        var dlgRef: Dialog? = null
        val closeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        closeRow.addView(TextView(this).apply {
            text = "GOT IT"
            setTextColor(COLOR_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            letterSpacing = 0.14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE), null, null
            )
            setOnClickListener { dlgRef?.dismiss() }
        })
        content.addView(closeRow)

        val scroll = ScrollView(this).apply { addView(content) }
        dlgRef = styledDialog(scroll)
        dlgRef.show()
    }

    /** Small accent-colored tappable label that opens [url] in the browser. */
    private fun legalLink(label: String, url: String): TextView = TextView(this).apply {
        text = label
        setTextColor(COLOR_ACCENT)
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.14f
        isClickable = true
        isFocusable = true
        background = RippleDrawable(
            ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE),
            null,
            null
        )
        setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Install a browser to view this document.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
            // Re-enabling clears the "intentionally off" timer so the weekly
            // check-in won't fire for an off-period that has ended.
            WatchdogScheduler.clearDisabled(this)
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startCaptureService()
            } else {
                ensurePermissionsAndStart()
            }
            updateStatus()
        } else {
            setSwitchSilently(true)
            showTurnOffDialog()
        }
    }

    private fun showTurnOffDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
        }
        content.addView(TextView(this).apply {
            text = "TURN RECALL OFF?"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(12))
        })
        content.addView(TextView(this).apply {
            text = "If you turn RECALL off, you won't be able to recover audio from any conversation, meeting, class, or moment you forget to record."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(22))
        })

        var dlgRef: Dialog? = null
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val keep = secondaryBtn("Keep listening") { dlgRef?.dismiss() }
        keep.layoutParams = LinearLayout.LayoutParams(
            0, dp(48), 1f
        ).apply { marginEnd = dp(10) }
        row.addView(keep)

        val off = dangerBtn("Turn off") {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            // Start the weekly-check-in clock from now so the alert (if opted in)
            // only nudges the user after 7 full days off.
            WatchdogScheduler.recordDisabled(this)
            stopCaptureService()
            setSwitchSilently(false)
            updateStatus()
            dlgRef?.dismiss()
        }
        off.layoutParams = LinearLayout.LayoutParams(
            0, dp(48), 1f
        )
        row.addView(off)
        content.addView(row)

        dlgRef = styledDialog(content)
        dlgRef.show()
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

    private fun updateBuffer() {
        val hours = AudioCaptureService.bufferHours(this)
        bufferTitleText.text = formatBufferHours(hours)
        val tier = Billing.BUFFER_TIERS.find { it.hours == hours }
        bufferSubText.text = when {
            tier == null -> "Custom window · tap to change"
            tier.hours == Billing.BUFFER_TIERS[0].hours -> "Default · tap to extend further"
            else -> "Extended · tap to change"
        }
    }

    private fun updateAlerts() {
        val watchdog = WatchdogScheduler.watchdogEnabled(this)
        val weekly = WatchdogScheduler.weeklyEnabled(this)
        alertsTitleText.text = when {
            watchdog && weekly -> "Watchdog + Weekly"
            watchdog -> "Watchdog on"
            weekly -> "Weekly check-in"
            else -> "All silent"
        }
        alertsSubText.text = when {
            watchdog && weekly -> "Covers outages + long-off nudge"
            watchdog -> "Pings you only if RECALL stops unexpectedly"
            weekly -> "Weekly nudge if listening is off"
            else -> "No alerts — tap to configure"
        }
    }

    private fun showAlertsPicker() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(16))
        }
        content.addView(TextView(this).apply {
            text = "ALERTS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "RECALL only notifies you when something actually goes wrong — never \"come back and use the app\" nags."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(18))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        content.addView(alertSwitchRow(
            title = "Watchdog alert",
            body = "Notify me immediately if RECALL stops listening (OS killed the service, background limit hit, etc.).",
            checked = WatchdogScheduler.watchdogEnabled(this),
            onChange = { on ->
                WatchdogScheduler.setWatchdogEnabled(this, on)
                updateAlerts()
            }
        ))
        content.addView(alertSwitchRow(
            title = "Weekly check-in",
            body = "If I've had RECALL off for 7 days, nudge me once to turn it back on.",
            checked = WatchdogScheduler.weeklyEnabled(this),
            onChange = { on ->
                WatchdogScheduler.setWeeklyEnabled(this, on)
                updateAlerts()
            }
        ))

        var dlgRef: Dialog? = null
        val closeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }
        closeRow.addView(TextView(this).apply {
            text = "DONE"
            setTextColor(COLOR_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            letterSpacing = 0.14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE), null, null
            )
            setOnClickListener { dlgRef?.dismiss() }
        })
        content.addView(closeRow)

        val scroll = ScrollView(this).apply { addView(content) }
        dlgRef = styledDialog(scroll)
        dlgRef.show()
    }

    private fun alertSwitchRow(
        title: String,
        body: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), COLOR_DIVIDER)
                cornerRadius = dp(14).toFloat()
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        col.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
        })
        col.addView(TextView(this).apply {
            text = body
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(col)
        row.addView(Switch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(COLOR_ACCENT, 0xFF4A4A4A.toInt())
            )
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(10) })
        wrapper.addView(row)
        return wrapper
    }

    private fun formatBufferHours(hours: Int): String = when {
        hours >= 24 && hours % 24 == 0 -> {
            val days = hours / 24
            if (days == 1) "1 day of audio" else "$days days of audio"
        }
        else -> if (hours == 1) "1 hour of audio" else "$hours hours of audio"
    }

    private fun showBufferTierPicker() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(16))
        }
        container.addView(TextView(this).apply {
            text = "EXTEND BUFFER"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(4))
        })
        container.addView(TextView(this).apply {
            text = "How far back should RECALL keep audio on this device? Demo mode — tapping a tier unlocks it instantly."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(16))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        var dialogRef: Dialog? = null
        val currentHours = AudioCaptureService.bufferHours(this)

        for (tier in Billing.BUFFER_TIERS) {
            val isCurrent = tier.hours == currentHours
            val strokeColor = when {
                isCurrent -> COLOR_STATUS_LIVE
                tier.highlight -> COLOR_ACCENT
                else -> COLOR_DIVIDER
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = RippleDrawable(
                    ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE),
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(if (isCurrent || tier.highlight) 2 else 1), strokeColor)
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
                text = tier.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
            })
            tier.badge?.let {
                leftCol.addView(TextView(this).apply {
                    text = if (isCurrent) "ACTIVE" else it.uppercase(Locale.US)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(
                        when {
                            isCurrent -> COLOR_STATUS_LIVE
                            tier.highlight -> COLOR_ACCENT
                            else -> COLOR_TEXT_MUTED
                        }
                    )
                    letterSpacing = 0.12f
                    setPadding(0, dp(2), 0, 0)
                })
            }
            row.addView(leftCol)
            row.addView(TextView(this).apply {
                text = tier.priceUsd
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(COLOR_TEXT)
            })
            row.setOnClickListener {
                if (tier.hours != currentHours) {
                    AudioCaptureService.setBufferHours(this, tier.hours)
                    updateBuffer()
                    Toast.makeText(
                        this,
                        "Buffer extended to ${formatBufferHours(tier.hours)}.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialogRef?.dismiss()
            }
            container.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }

        val closeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }
        closeRow.addView(TextView(this).apply {
            text = "CLOSE"
            setTextColor(COLOR_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            letterSpacing = 0.14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE), null, null
            )
            setOnClickListener { dialogRef?.dismiss() }
        })
        container.addView(closeRow)

        val scroll = ScrollView(this).apply { addView(container) }
        dialogRef = styledDialog(scroll)
        dialogRef.show()
    }

    private fun showPackPicker() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(16))
        }
        container.addView(TextView(this).apply {
            text = "BUY MINUTES"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(4))
        })
        container.addView(TextView(this).apply {
            text = "Demo mode — tapping a pack simulates a purchase and credits your balance instantly. Google Play Billing will be wired in before public launch."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(0, 0, 0, dp(16))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        var dialogRef: Dialog? = null

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

        // Close button at the bottom
        val closeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }
        val closeBtn = TextView(this).apply {
            text = "CLOSE"
            setTextColor(COLOR_ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            letterSpacing = 0.14f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(COLOR_BTN_SECONDARY_RIPPLE),
                null,
                null
            )
            setOnClickListener { dialogRef?.dismiss() }
        }
        closeRow.addView(closeBtn)
        container.addView(closeRow)

        val scroll = ScrollView(this).apply { addView(container) }
        dialogRef = styledDialog(scroll)
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

    /**
     * Picker for the "recover by specific time range" flow. Uses the user's
     * device-local timezone for display; chunk logic stays in UTC epoch.
     */
    private fun showTimeRangePicker() {
        val now = System.currentTimeMillis()
        val bufferHrs = AudioCaptureService.bufferHours(this)
        val oldestAvailableMs = now - bufferHrs * 3600L * 1000L
        // Sensible default: last 15 minutes
        var startMs = now - 15 * 60 * 1000L
        var endMs = now

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(16))
        }
        content.addView(TextView(this).apply {
            text = "RECOVER TIME RANGE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "Pick the exact time window the audio you want is from. Times are in your local timezone."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, 0, 0, dp(18))
        })

        content.addView(TextView(this).apply {
            text = "START"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, 0, 0, dp(6))
        })
        val startBtn = secondaryBtn(formatDateTime(startMs)) { }
        content.addView(startBtn)

        content.addView(TextView(this).apply {
            text = "END"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_TEXT_MUTED)
            letterSpacing = 0.14f
            setPadding(0, dp(12), 0, dp(6))
        })
        val endBtn = secondaryBtn(formatDateTime(endMs)) { }
        content.addView(endBtn)

        val summary = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_MUTED)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(16), 0, dp(4))
        }
        content.addView(summary)

        fun refreshSummary() {
            startBtn.text = formatDateTime(startMs)
            endBtn.text = formatDateTime(endMs)
            val durSec = ((endMs - startMs) / 1000L).toInt()
            summary.text = when {
                endMs <= startMs -> "End must be after start."
                startMs < oldestAvailableMs ->
                    "Start is beyond the buffer window (${formatBufferHours(bufferHrs)}). " +
                        "The recovery will include only what's still on-device."
                endMs > now ->
                    "End is in the future — clamped to right now."
                durSec < 30 ->
                    "Pick at least 30 seconds."
                else ->
                    "Duration: ${formatDuration(durSec)} · Costs ${formatDuration(durSec)} from balance."
            }
            summary.setTextColor(
                when {
                    endMs <= startMs -> 0xFFFF6B6B.toInt()
                    durSec < 30 -> 0xFFFF6B6B.toInt()
                    else -> COLOR_TEXT_MUTED
                }
            )
        }
        refreshSummary()

        startBtn.setOnClickListener {
            pickDateTime(startMs) { newMs ->
                startMs = newMs
                refreshSummary()
            }
        }
        endBtn.setOnClickListener {
            pickDateTime(endMs) { newMs ->
                endMs = newMs
                refreshSummary()
            }
        }

        var dlgRef: Dialog? = null
        val recoverBtn = primaryBtn("RECOVER THIS RANGE") {
            if (endMs <= startMs || (endMs - startMs) < 30_000L) {
                Toast.makeText(
                    this,
                    "Pick a range of at least 30 seconds with end after start.",
                    Toast.LENGTH_SHORT
                ).show()
                return@primaryBtn
            }
            dlgRef?.dismiss()
            recoverFromRange(startMs, endMs)
        }
        recoverBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { topMargin = dp(16) }
        content.addView(recoverBtn)

        val cancelBtn = secondaryBtn("Cancel") { dlgRef?.dismiss() }
        cancelBtn.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        ).apply { topMargin = dp(10) }
        content.addView(cancelBtn)

        val scroll = ScrollView(this).apply { addView(content) }
        dlgRef = styledDialog(scroll)
        dlgRef.show()
    }

    /** Opens system Date then Time pickers; returns the combined UTC epoch. */
    private fun pickDateTime(initialMs: Long, onPick: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMs }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    this,
                    { _, hour, min ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, min)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        onPick(cal.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatDateTime(ms: Long): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(ms))
    }

    private fun recoverFromRange(startMs: Long, endMs: Long) {
        val durationSec = ((endMs - startMs) / 1000L).toInt()
        if (durationSec < 30) {
            Toast.makeText(this, "Range must be at least 30 seconds.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Billing.canCover(this, durationSec)) {
            val have = Billing.effectiveSec(this)
            Toast.makeText(
                this,
                "Need ${formatDuration(durationSec)} of minutes — you have ${formatDuration(have)}.",
                Toast.LENGTH_LONG
            ).show()
            showPackPicker()
            return
        }
        try {
            val out = File(getExternalFilesDir(null), "recall-recovery.wav")
            val got = AudioCaptureService.exportTimeRange(this, startMs, endMs, out)
            if (got > 0) {
                Billing.deduct(this, got)
                updateBalance()
                Toast.makeText(
                    this,
                    "Recovered ${formatDuration(got)} from ${formatDateTime(startMs)}.",
                    Toast.LENGTH_LONG
                ).show()
                player?.release()
                player = null
                playState = PlayState.IDLE
                updatePlayButton()
            } else {
                Toast.makeText(
                    this,
                    "No audio available for that range — it may be older than your buffer window.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "range recovery failed", e)
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

        // Theme preference key + default (true = dark)
        const val KEY_DARK_THEME = "dark_theme"
        const val DEFAULT_DARK_THEME = true

        // Saved-instance key for the currently-visible tab index
        const val STATE_CURRENT_TAB = "current_tab"

        // Dark palette
        const val DARK_BG = 0xFF000000.toInt()
        const val DARK_TEXT = 0xFFFFFFFF.toInt()
        const val DARK_TEXT_MUTED = 0xFF71767B.toInt()
        const val DARK_DIVIDER = 0xFF2F3336.toInt()
        const val DARK_BTN_PRIMARY = 0xFFFFFFFF.toInt()
        const val DARK_BTN_PRIMARY_FG = 0xFF000000.toInt()
        const val DARK_BTN_PRIMARY_RIPPLE = 0x33000000
        const val DARK_BTN_SECONDARY_RIPPLE = 0x33FFFFFF
        const val DARK_ACCENT_DIM = 0xFF1A4766.toInt()
        const val DARK_ACCENT_RIPPLE = 0x33FFFFFF

        // Light palette (near-white bg, dark text)
        const val LIGHT_BG = 0xFFF7F8FA.toInt()
        const val LIGHT_TEXT = 0xFF0F1419.toInt()
        const val LIGHT_TEXT_MUTED = 0xFF536471.toInt()
        const val LIGHT_DIVIDER = 0xFFDFE4EB.toInt()
        const val LIGHT_BTN_PRIMARY = 0xFF0F1419.toInt()
        const val LIGHT_BTN_PRIMARY_FG = 0xFFFFFFFF.toInt()
        const val LIGHT_BTN_PRIMARY_RIPPLE = 0x33FFFFFF
        const val LIGHT_BTN_SECONDARY_RIPPLE = 0x33000000
        const val LIGHT_ACCENT_DIM = 0xFFCFE4F6.toInt()
        const val LIGHT_ACCENT_RIPPLE = 0x33000000

        // Theme-neutral (identical in both modes)
        const val COLOR_STATUS_LIVE = 0xFF00BA7C.toInt()
        const val COLOR_STATUS_OFF = 0xFF71767B.toInt()
        const val COLOR_ACCENT = 0xFF1D9BF0.toInt()

        // Legal document URLs. GitHub renders Markdown in the browser, so these
        // work without GitHub Pages. Once Pages is enabled, swap to:
        //   https://loxi995.github.io/RECALL-Prototype/privacy-policy
        //   https://loxi995.github.io/RECALL-Prototype/terms-of-service
        const val PRIVACY_POLICY_URL =
            "https://github.com/loxi995/RECALL-Prototype/blob/main/legal/privacy-policy.md"
        const val TERMS_URL =
            "https://github.com/loxi995/RECALL-Prototype/blob/main/legal/terms-of-service.md"
    }
}
