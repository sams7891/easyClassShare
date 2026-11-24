package com.easyclassshare.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The full-screen "Eyes on me" message, drawn over every app while a teacher has it on.
 * Needs the "Display over other apps" permission; without it [show] returns false.
 */
class AttentionOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var title: TextView? = null
    private var subtitle: TextView? = null

    val isShowing: Boolean get() = view != null

    fun show(text: String, from: String): Boolean {
        if (!canShow(context)) return false
        val existing = view
        if (existing != null) {
            title?.text = text
            subtitle?.text = from
            subtitle?.visibility = if (from.isBlank()) View.GONE else View.VISIBLE
            return true
        }
        val root = buildView(text, from)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return try {
            windowManager.addView(root, params)
            view = root
            true
        } catch (e: RuntimeException) {
            // Permission taken away in the meantime, or the window manager refused.
            false
        }
    }

    fun hide() {
        val v = view ?: return
        runCatching { windowManager.removeView(v) }
        view = null
        title = null
        subtitle = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildView(text: String, from: String): View {
        val title = TextView(context).apply {
            this.text = text
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(context).apply {
            this.text = from
            setTextColor(TEXT_SOFT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            visibility = if (from.isBlank()) View.GONE else View.VISIBLE
        }
        this.title = title
        this.subtitle = subtitle
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            addView(title)
            addView(subtitle)
        }
        return object : FrameLayout(context) {
            // Swallow the back button while the teacher has the screen.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
        }.apply {
            setBackgroundColor(BACKGROUND)
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> true }
            addView(
                column,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val BACKGROUND = 0xFF23303B.toInt()
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val TEXT_SOFT = 0xFFB9C3CC.toInt()

        fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
