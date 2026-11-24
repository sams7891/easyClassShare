package com.easyclassshare.app.online

import android.content.Context
import android.content.SharedPreferences
import com.easyclassshare.app.service.SoundEnforcer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers which classes this device is in, so the setup only has to be done once.
 *
 * A student tablet is in exactly one class. A teacher can be in several classes (and an
 * admin in several schools) and picks which one is shown ([membership] is the active one).
 */
object LocalSetup {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    private val _memberships = MutableStateFlow<List<Membership>>(emptyList())
    val memberships: StateFlow<List<Membership>> = _memberships.asStateFlow()

    private val _membership = MutableStateFlow<Membership?>(null)

    /** The class shown right now (a student's only class, or the teacher's chosen one). */
    val membership: StateFlow<Membership?> = _membership.asStateFlow()

    /** Name of the class a teacher just removed this device from, until the message was seen. */
    private val _removedFrom = MutableStateFlow<String?>(null)
    val removedFrom: StateFlow<String?> = _removedFrom.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = readList()
        _memberships.value = list
        _membership.value = pickActive(list, prefs.getString(KEY_ACTIVE, null))
    }

    @Synchronized
    fun save(membership: Membership) {
        val list = if (membership.role == Role.STUDENT) {
            listOf(membership)
        } else {
            // Teachers and school admins can have several classes and schools on one device.
            _memberships.value.filter { it.role != Role.STUDENT && !it.sameClass(membership) } + membership
        }
        if (membership.role == Role.STUDENT) {
            lastAnnouncedMessageTime = 0L
            prefs.edit().remove(KEY_OPENED).apply()
        }
        _removedFrom.value = null
        publish(list, membership.classId)
    }

    @Synchronized
    fun setActive(classId: String) {
        publish(_memberships.value, classId)
    }

    @Synchronized
    fun remove(membership: Membership) {
        val list = _memberships.value.filterNot { it.sameClass(membership) }
        val active = _membership.value?.takeUnless { it.sameClass(membership) }
        publish(list, active?.classId)
    }

    /** Called when a class no longer lists this device (a teacher removed it). */
    @Synchronized
    fun markRemoved(membership: Membership) {
        val known = _memberships.value.firstOrNull { it.sameClass(membership) } ?: return
        remove(known)
        _removedFrom.value = known.className
    }

    fun dismissRemovedNotice() {
        _removedFrom.value = null
    }

    /** Keeps the saved class name up to date when a teacher renames the class. */
    @Synchronized
    fun updateClassName(classId: String, className: String) {
        val list = _memberships.value
        if (list.none { it.classId == classId && it.className != className }) return
        publish(list.map { if (it.classId == classId) it.copy(className = className) else it }, _membership.value?.classId)
    }

    /** Server time of the newest message already announced on this device (0 = none yet). */
    var lastAnnouncedMessageTime: Long
        get() = prefs.getLong(KEY_LAST_MESSAGE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_MESSAGE, value).apply()

    /**
     * Remembers message ids this tablet already reported as opened, so each one is sent once.
     * Returns false if [messageId] was already reported.
     */
    @Synchronized
    fun rememberOpened(messageId: String): Boolean {
        val known = prefs.getString(KEY_OPENED, "").orEmpty().split(',').filter { it.isNotEmpty() }
        if (messageId in known) return false
        val updated = (known + messageId).takeLast(MAX_REMEMBERED_OPENED)
        prefs.edit().putString(KEY_OPENED, updated.joinToString(",")).apply()
        return true
    }

    @Synchronized
    fun forgetOpened(messageId: String) {
        val known = prefs.getString(KEY_OPENED, "").orEmpty().split(',').filter { it.isNotEmpty() && it != messageId }
        prefs.edit().putString(KEY_OPENED, known.joinToString(",")).apply()
    }

    private fun publish(list: List<Membership>, activeClassId: String?) {
        val active = pickActive(list, activeClassId)
        prefs.edit()
            .putString(KEY_LIST, toJson(list))
            .putString(KEY_ACTIVE, active?.classId)
            .apply()
        _memberships.value = list
        _membership.value = active
        // A device that is no longer a student anywhere gets its sound back.
        if (list.none { it.role == Role.STUDENT }) SoundEnforcer(appContext).restore()
    }

    private fun pickActive(list: List<Membership>, classId: String?): Membership? =
        list.firstOrNull { it.classId == classId } ?: list.firstOrNull()

    private fun Membership.sameClass(other: Membership) = classId == other.classId && role == other.role

    private fun readList(): List<Membership> {
        val raw = prefs.getString(KEY_LIST, null) ?: return readOldSingleClass()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val role = runCatching { Role.valueOf(o.getString("role")) }.getOrNull() ?: return@mapNotNull null
                Membership(
                    classId = o.getString("classId"),
                    role = role,
                    name = o.optString("name"),
                    className = o.optString("className"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Setups saved by 1.3.0-beta.1, which allowed only one class. */
    private fun readOldSingleClass(): List<Membership> {
        val classId = prefs.getString(OLD_KEY_CLASS, null) ?: return emptyList()
        val role = runCatching { Role.valueOf(prefs.getString(OLD_KEY_ROLE, null).orEmpty()) }.getOrNull()
            ?: return emptyList()
        return listOf(Membership(classId, role, prefs.getString(OLD_KEY_NAME, null).orEmpty()))
    }

    private fun toJson(list: List<Membership>): String {
        val array = JSONArray()
        for (m in list) {
            array.put(
                JSONObject()
                    .put("classId", m.classId)
                    .put("role", m.role.name)
                    .put("name", m.name)
                    .put("className", m.className),
            )
        }
        return array.toString()
    }

    private const val PREFS_NAME = "online_class"
    private const val KEY_LIST = "memberships"
    private const val KEY_ACTIVE = "active_class"
    private const val KEY_LAST_MESSAGE = "last_message_time"
    private const val KEY_OPENED = "opened_ids"
    private const val MAX_REMEMBERED_OPENED = 200
    private const val OLD_KEY_CLASS = "class_id"
    private const val OLD_KEY_ROLE = "role"
    private const val OLD_KEY_NAME = "name"
}
