package com.visionoverlay.app

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Shared visual tokens keep the loading, camera, and settings screens consistent. */
object AppUi {
    const val CHANNEL = "https://t.me/BNSTR"
    const val SOURCE = "https://github.com/fsq04116-oss/bn-android-vision-tracker"
    val background = Color.rgb(242, 242, 247)
    val surface = Color.WHITE
    val text = Color.rgb(28, 28, 30)
    val muted = Color.rgb(99, 99, 102)
    val accent = Color.rgb(0, 102, 214)
    val secondary = Color.rgb(232, 232, 237)
    val cameraMaterial = Color.argb(210, 36, 36, 38)

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun panel(context: Context, color: Int = surface, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
    }
    fun label(context: Context, value: String, size: Float = 14f, color: Int = text) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        setLineSpacing(dp(context, 3).toFloat(), 1f)
    }
    fun heading(context: Context, value: String, size: Float = 28f) = label(context, value, size).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = -.02f
    }
    fun button(context: Context, value: String, primary: Boolean = false, action: () -> Unit) = Button(context).apply {
        text = value; textSize = 14f; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) Color.WHITE else accent)
        background = RippleDrawable(ColorStateList.valueOf(0x18007AFF), panel(context, if (primary) accent else secondary, 14), null)
        minHeight = dp(context, 48); minimumHeight = dp(context, 48)
        setPadding(dp(context, 16), 0, dp(context, 16), 0)
        setOnClickListener { action() }
    }
    fun cameraButton(context: Context, value: String, primary: Boolean = false, action: () -> Unit) =
        button(context, value, primary, action).apply {
            if (!primary) {
                setTextColor(Color.WHITE)
                background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), panel(context, Color.TRANSPARENT, 14), null)
            }
        }

    // Apply safe areas to chrome, keeping the video/camera free to fill its viewport.
    fun safeArea(view: View, padding: Int = 16) {
        val base = dp(view.context, padding)
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            target.setPadding(base + safe.left, base + safe.top, base + safe.right, base + safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun reveal(view: View, show: Boolean) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.visibility = if (show) View.VISIBLE else View.GONE; view.alpha = 1f; return
        }
        if (show && view.visibility != View.VISIBLE) { view.alpha = 0f; view.visibility = View.VISIBLE }
        view.animate().alpha(if (show) 1f else 0f).setDuration(180L)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                view.visibility = if (show) View.VISIBLE else View.GONE
                view.alpha = 1f
            }.start()
    }
    fun openLink(context: Context, url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "请安装浏览器或 Telegram 后打开", Toast.LENGTH_LONG).show()
        }
    }
}
