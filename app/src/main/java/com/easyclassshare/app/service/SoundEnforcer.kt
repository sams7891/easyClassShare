package com.easyclassshare.app.service

import android.content.Context
import android.media.AudioManager

/**
 * Turns the tablet's volume down to zero while a teacher has sound switched off,
 * and puts the old volume back when sound is allowed again.
 *
 * The "muted" state and the saved volumes live in SharedPreferences, so they
 * survive the app or tablet restarting.
 */
class SoundEnforcer(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isMuted: Boolean get() = prefs.getBoolean(KEY_MUTED, false)

    fun mute() {
        if (!isMuted) {
            val editor = prefs.edit()
            for (stream in STREAMS) editor.putInt(volumeKey(stream), audio.getStreamVolume(stream))
            editor.putBoolean(KEY_MUTED, true).apply()
        }
        enforce()
    }

    /** Puts any stream a kid turned back up down to zero again. */
    fun enforce() {
        if (!isMuted) return
        for (stream in STREAMS) {
            if (audio.getStreamVolume(stream) > 0) {
                // Some devices refuse some streams (e.g. ring volume needs Do Not Disturb access).
                runCatching { audio.setStreamVolume(stream, 0, 0) }
            }
        }
    }

    fun restore() {
        if (!isMuted) return
        for (stream in STREAMS) {
            val saved = prefs.getInt(volumeKey(stream), -1)
            if (saved > 0 && audio.getStreamVolume(stream) == 0) {
                runCatching { audio.setStreamVolume(stream, saved, 0) }
            }
        }
        prefs.edit().putBoolean(KEY_MUTED, false).apply()
    }

    private fun volumeKey(stream: Int) = "volume_$stream"

    private companion object {
        const val PREFS_NAME = "sound_enforcer"
        const val KEY_MUTED = "muted"
        val STREAMS = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
        )
    }
}
