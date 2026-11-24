package com.easyclassshare.app.online

import android.content.Context
import android.content.SharedPreferences
import com.easyclassshare.app.service.SoundEnforcer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers which class this device joined, so the setup only has to be done once. */
object LocalSetup {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    private val _membership = MutableStateFlow<Membership?>(null)
    val membership: StateFlow<Membership?> = _membership.asStateFlow()

    /** True after a teacher removed this device, until the user has seen the message. */
    private val _wasRemoved = MutableStateFlow(false)
    val wasRemoved: StateFlow<Boolean> = _wasRemoved.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _membership.value = read()
    }

    private fun read(): Membership? {
        val classId = prefs.getString(KEY_CLASS, null) ?: return null
        val role = runCatching { Role.valueOf(prefs.getString(KEY_ROLE, null).orEmpty()) }.getOrNull() ?: return null
        return Membership(classId, role, prefs.getString(KEY_NAME, null).orEmpty())
    }

    fun save(membership: Membership) {
        prefs.edit()
            .putString(KEY_CLASS, membership.classId)
            .putString(KEY_ROLE, membership.role.name)
            .putString(KEY_NAME, membership.name)
            .putLong(KEY_LAST_MESSAGE, 0L)
            .apply()
        _wasRemoved.value = false
        _membership.value = membership
    }

    fun clear() {
        prefs.edit().remove(KEY_CLASS).remove(KEY_ROLE).remove(KEY_NAME).apply()
        _membership.value = null
        SoundEnforcer(appContext).restore()
    }

    /** Called when the class no longer lists this device. */
    @Synchronized
    fun markRemoved(membership: Membership) {
        if (_membership.value != membership) return
        clear()
        _wasRemoved.value = true
    }

    fun dismissRemovedNotice() {
        _wasRemoved.value = false
    }

    /** Server time of the newest message already announced on this device (0 = none yet). */
    var lastAnnouncedMessageTime: Long
        get() = prefs.getLong(KEY_LAST_MESSAGE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_MESSAGE, value).apply()

    private const val PREFS_NAME = "online_class"
    private const val KEY_CLASS = "class_id"
    private const val KEY_ROLE = "role"
    private const val KEY_NAME = "name"
    private const val KEY_LAST_MESSAGE = "last_message_time"
}
