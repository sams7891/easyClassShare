package com.easyclassshare.app.service

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** The permissions and switches a student device needs, and where to turn them on. */
object DevicePermissions {

    /** New links show up as notifications (only asked on Android 13 and newer). */
    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** "Display over other apps", for "Eyes on me" over other apps. */
    fun overlayAllowed(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** The accessibility switch that lets "Eyes on me" also block the notification shade. */
    fun lockEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val mine = ComponentName(context, LockService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    /** Not stopped by battery saving, so the device stays connected to its class. */
    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun allGranted(context: Context): Boolean =
        notificationsAllowed(context) && overlayAllowed(context) && lockEnabled(context) && batteryUnrestricted(context)

    fun openOverlaySettings(context: Context) = open(
        context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    )

    fun openAccessibilitySettings(context: Context) = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** App info, where "Allow restricted settings" is on Android 13+ for apps not installed from a store. */
    fun openAppInfo(context: Context) = open(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    @Suppress("BatteryLife") // A classroom device has to stay connected to receive links and "Eyes on me".
    fun openBatterySettings(context: Context) = open(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    private fun open(context: Context, vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Try the next, more general page.
            } catch (e: SecurityException) {
                // Same.
            }
        }
    }
}
