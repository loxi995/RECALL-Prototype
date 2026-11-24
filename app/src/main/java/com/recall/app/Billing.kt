package com.recall.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Local-only balance + trial state. Backed by SharedPreferences for the demo.
 *
 * Production path (post-Play launch):
 *   • Replace addMinutes() with a Google Play IAP + RevenueCat purchase flow.
 *   • Mirror balance server-side in Supabase, keyed by Google Sign-in account id.
 *   • Keep this local cache as a fast read + offline fallback.
 *
 * Mechanics:
 *   • 60-second free trial, used first.
 *   • Recoveries consume from trial, then from paid balance.
 *   • Nothing expires.
 *   • Minimum recovery length (30 s) is enforced upstream by the duration picker.
 */
object Billing {
    private const val PREFS = "recall_billing"
    private const val KEY_TRIAL_REMAINING = "trial_remaining_sec"
    private const val KEY_PAID_BALANCE = "paid_balance_sec"
    private const val KEY_INITIALIZED = "initialized"

    const val TRIAL_SECONDS = 60

    data class Pack(
        val productId: String,  // matches the Play IAP SKU we'll create
        val minutes: Int,
        val priceUsd: String,
        val badge: String? = null,
        val highlight: Boolean = false,
    )

    val PACKS: List<Pack> = listOf(
        Pack("recall_minutes_10",  10, "$0.99",  "Starter"),
        Pack("recall_minutes_30",  30, "$2.49",  "Save 16%"),
        Pack("recall_minutes_60",  60, "$3.99",  "Best Value", highlight = true),
        Pack("recall_minutes_180", 180, "$8.99", "Save 50%"),
        Pack("recall_minutes_600", 600, "$19.99", "Best Deal"),
    )

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureInit(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean(KEY_INITIALIZED, false)) {
            p.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putInt(KEY_TRIAL_REMAINING, TRIAL_SECONDS)
                .putInt(KEY_PAID_BALANCE, 0)
                .apply()
        }
    }

    fun trialRemainingSec(c: Context): Int =
        prefs(c).getInt(KEY_TRIAL_REMAINING, TRIAL_SECONDS)

    fun paidBalanceSec(c: Context): Int =
        prefs(c).getInt(KEY_PAID_BALANCE, 0)

    fun effectiveSec(c: Context): Int =
        trialRemainingSec(c) + paidBalanceSec(c)

    fun canCover(c: Context, seconds: Int): Boolean =
        effectiveSec(c) >= seconds

    /**
     * Deducts [seconds] from the trial balance first, then from paid balance.
     * Returns true if the deduction succeeded (user had enough minutes).
     * Call ONLY after a recovery that produced audio.
     */
    fun deduct(c: Context, seconds: Int): Boolean {
        if (seconds <= 0) return true
        val p = prefs(c)
        val trial = p.getInt(KEY_TRIAL_REMAINING, TRIAL_SECONDS)
        val paid = p.getInt(KEY_PAID_BALANCE, 0)
        if (trial + paid < seconds) return false
        val fromTrial = minOf(trial, seconds)
        val fromPaid = seconds - fromTrial
        p.edit()
            .putInt(KEY_TRIAL_REMAINING, trial - fromTrial)
            .putInt(KEY_PAID_BALANCE, paid - fromPaid)
            .apply()
        return true
    }

    /**
     * Credits [minutes] to the paid balance (demo mode).
     * In production this is called from the Play billing purchase callback,
     * after server-side receipt validation.
     */
    fun addMinutes(c: Context, minutes: Int) {
        val p = prefs(c)
        val current = p.getInt(KEY_PAID_BALANCE, 0)
        p.edit().putInt(KEY_PAID_BALANCE, current + minutes * 60).apply()
    }
}
