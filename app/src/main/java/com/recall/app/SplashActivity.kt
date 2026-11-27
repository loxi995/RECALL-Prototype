package com.recall.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var handedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(0xFF0A0A0A.toInt())
                cornerRadius = dp(26).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(130), dp(130))
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
        }

        // Whole-word RECALL — pre-rendered, then faded in (no typewriter)
        val name = TextView(this).apply {
            text = "RECALL"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.34f
            alpha = 0f
            translationY = dp(8).toFloat()
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            layoutParams = lp
        }

        root.addView(logo)
        root.addView(name)
        setContentView(root)

        // 1. Logo pops in with overshoot + fade (~280ms)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.6f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.6f, 1f)
            )
            duration = 280
            interpolator = OvershootInterpolator(1.1f)
            start()
        }

        // 2. RECALL fades in as a whole word with a subtle lift (~420ms)
        handler.postDelayed({
            if (handedOff) return@postDelayed
            name.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, 220)

        // 3. Hold briefly after the word is in, then hand off (~1000ms total)
        handler.postDelayed({ handOff() }, 980)
    }

    private fun handOff() {
        if (handedOff) return
        handedOff = true
        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
