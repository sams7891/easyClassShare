package com.easyclassshare.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.easyclassshare.app.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional accessibility service that makes "Eyes on me" hold on student devices.
 *
 * A normal "display over other apps" window can't cover the notification shade, so
 * students could pull it down and get into the device settings. An accessibility overlay
 * is drawn above the status bar and the shade, and while "Eyes on me" is on this
 * service also closes the shade and leaves the Settings app if they open anyway.
 *
 * While a teacher has sound off it also swallows the volume buttons, so the sound can't
 * come back even for a moment.
 *
 * Otherwise it does nothing, and it never reads screen content.
 */
class LockService : AccessibilityService() {
    private var overlay: AttentionOverlay? = null
    private val sound by lazy { SoundEnforcer(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppSettings.wrap(base))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = AttentionOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        _instance.value = this
    }

    /** Covers the whole screen, status bar included. Returns false if that didn't work. */
    fun showAttention(text: String, from: String): Boolean = overlay?.show(text, from) ?: false

    fun hideAttention() {
        overlay?.hide()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || overlay?.isShowing != true) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        when {
            pkg == packageName -> Unit
            pkg == SYSTEM_UI -> closeShade()
            pkg in SETTINGS_APPS -> {
                closeShade()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        }
    }

    /** Volume buttons do nothing while the teacher has sound off. All other keys pass. */
    override fun onKeyEvent(event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> sound.isMuted
        else -> false
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        overlay?.hide()
        overlay = null
        if (_instance.value === this) _instance.value = null
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
        private val SETTINGS_APPS = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.huawei.systemmanager",
        )

        private val _instance = MutableStateFlow<LockService?>(null)

        /** The running service, or null while the switch in the accessibility settings is off. */
        val instance: StateFlow<LockService?> = _instance.asStateFlow()
    }
}
