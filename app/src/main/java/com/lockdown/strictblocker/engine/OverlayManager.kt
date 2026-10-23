package com.lockdown.strictblocker.engine

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lockdown.strictblocker.R
import com.lockdown.strictblocker.core.Prefs
import com.lockdown.strictblocker.core.formatRemaining

/**
 * The full-screen blocking screen, drawn as a system overlay so it can sit
 * on top of any app or browser. Black vault door, lime padlock breathing,
 * reason, and the Strict Mode countdown when one is running.
 * There is deliberately no close button.
 */
object OverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var countdownView: TextView? = null
    private var currentReason: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            updateCountdownText()
            if (overlayView != null) mainHandler.postDelayed(this, 1_000)
        }
    }

    fun isShowing(): Boolean = overlayView != null

    fun show(context: Context, reason: String) {
        mainHandler.post {
            if (overlayView != null) {
                if (currentReason != reason) {
                    currentReason = reason
                    // Refresh the reason line on the existing view.
                    (overlayView?.findViewById<TextView>(REASON_VIEW_ID))?.text = reason
                }
                return@post
            }
            val appContext = context.applicationContext
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = buildView(appContext, reason)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            runCatching { wm.addView(view, params) }
                .onSuccess {
                    overlayView = view
                    currentReason = reason
                    mainHandler.removeCallbacks(ticker)
                    mainHandler.post(ticker)
                }
        }
    }

    fun hide() {
        mainHandler.post {
            val view = overlayView ?: return@post
            overlayView = null
            countdownView = null
            currentReason = null
            mainHandler.removeCallbacks(ticker)
            val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            runCatching { wm.removeView(view) }
        }
    }

    private fun updateCountdownText() {
        val tv = countdownView ?: return
        val ctx = tv.context
        val prefs = Prefs.get(ctx)
        tv.text = if (prefs.isStrictActive()) {
            "Available in ${formatRemaining(prefs.strictRemainingMillis())}"
        } else {
            "Strict Mode is off — edit your list in LOCKDOWN"
        }
    }

    private fun buildView(context: Context, reason: String): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isClickable = true
            isFocusable = false
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val lock = ImageView(context).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            layoutParams = LinearLayout.LayoutParams(dp(context, 140), dp(context, 140))
        }
        // Breathing pulse on the padlock.
        val pulse = ScaleAnimation(
            1f, 1.07f, 1f, 1.07f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1400
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        lock.startAnimation(pulse)

        val title = TextView(context).apply {
            text = "BLOCKED BY LOCKDOWN"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.BOLD
            )
            setPadding(0, dp(context, 28), 0, 0)
        }
        val reasonView = TextView(context).apply {
            id = REASON_VIEW_ID
            text = reason
            setTextColor(Color.parseColor("#9A9A9E"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(context, 32), dp(context, 10), dp(context, 32), 0)
        }
        val countdown = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFF00"))
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, dp(context, 22), 0, 0)
        }
        countdownView = countdown
        updateCountdownText()

        column.addView(lock)
        column.addView(title)
        column.addView(reasonView)
        column.addView(countdown)
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        root.addView(column, lp)

        // Fade-in so the vault door feels solid, not glitchy.
        root.startAnimation(AlphaAnimation(0f, 1f).apply { duration = 120 })
        return root
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private const val REASON_VIEW_ID = 0x4C4F434B // "LOCK"
}
