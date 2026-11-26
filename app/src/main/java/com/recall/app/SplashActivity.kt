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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var handedOff = false
    private var caretAnim: ObjectAnimator? = null

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
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(150))
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
        }

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            alpha = 0f
        }

        val name = TextView(this).apply {
            text = ""
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.34f
        }
        val caret = TextView(this).apply {
            text = "▍" // thin vertical block caret
            setTextColor(0xFF1D9BF0.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(3), 0, 0, 0)
            alpha = 0f
        }
        nameRow.addView(name)
        nameRow.addView(caret)

        root.addView(logo)
        root.addView(nameRow)
        setContentView(root)

        // 1. Logo pops in with overshoot + fade — ~280ms
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.6f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.6f, 1f)
            )
            duration = 280
            interpolator = OvershootInterpolator(1.2f)
            start()
        }

        // 2. Reveal the name row + caret, then start typewriter
        handler.postDelayed({
            if (handedOff) return@postDelayed
            nameRow.animate()
                .alpha(1f)
                .setDuration(140)
                .setInterpolator(DecelerateInterpolator())
                .start()
            startCaretBlink(caret)
        }, 240)

        // 3. Fast typewriter — ~38ms/char
        val target = "RECALL"
        val stepMs = 38L
        var i = 0
        val typeStep = object : Runnable {
            override fun run() {
                if (handedOff) return
                if (i <= target.length) {
                    name.text = target.substring(0, i)
                    i++
                    handler.postDelayed(this, stepMs)
                } else {
                    // small pop when complete
                    nameRow.animate()
                        .scaleX(1.04f).scaleY(1.04f)
                        .setDuration(90)
                        .withEndAction {
                            nameRow.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(110)
                                .start()
                        }
                        .start()
                    // fade caret, then cross-fade out
                    handler.postDelayed({
                        caretAnim?.cancel()
                        caret.animate().alpha(0f).setDuration(120).start()
                    }, 160)
                    handler.postDelayed({ handOff() }, 360)
                }
            }
        }
        handler.postDelayed(typeStep, 360)
    }

    private fun startCaretBlink(caret: TextView) {
        caretAnim = ObjectAnimator.ofFloat(caret, "alpha", 1f, 0.15f).apply {
            duration = 420
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
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
        caretAnim?.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
