package com.easyclassshare.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.online.Attention
import com.easyclassshare.app.online.ClassCodes
import com.easyclassshare.app.online.ClassRepository
import com.easyclassshare.app.online.CodeKind
import com.easyclassshare.app.online.DeviceInfo
import com.easyclassshare.app.online.InboxMessage
import com.easyclassshare.app.online.JoinException
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Membership
import com.easyclassshare.app.online.OnlineConfig
import com.easyclassshare.app.online.Recipients
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.online.SchoolClass
import com.easyclassshare.app.online.SchoolCodes
import com.easyclassshare.app.online.SchoolRef
import com.easyclassshare.app.online.SentMessage
import com.easyclassshare.app.online.SoundSettings
import com.easyclassshare.app.online.TeacherInfo
import com.easyclassshare.app.service.ClassService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * State and actions for online classes: the one-time setup, the start screen, schools,
 * the teacher dashboard and the student inbox.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassViewModel(application: Application) : AndroidViewModel(application) {
    val isConfigured: Boolean = OnlineConfig.isConfigured

    /** All classes and schools of this device. */
    val memberships: StateFlow<List<Membership>> = LocalSetup.memberships

    /** Name of a class a teacher just removed this device from (null = nothing to tell). */
    val removedFrom: StateFlow<String?> = LocalSetup.removedFrom

    private val _openSchool = MutableStateFlow<SchoolRef?>(null)

    /** The school shown on the school screen. */
    val openSchool: StateFlow<SchoolRef?> = _openSchool.asStateFlow()

    private val _openClass = MutableStateFlow<Membership?>(null)

    /** The class shown on the teacher dashboard (always role TEACHER, also when an admin opened it). */
    val current: StateFlow<Membership?> = _openClass.asStateFlow()

    val myUid: StateFlow<String?> = flow { if (isConfigured) emit(ClassRepository.uid()) }
        .catch { }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** This device is in the superAdmins list (set by hand in the Firebase console). */
    val isSuperAdmin: StateFlow<Boolean> = memberships
        .flatMapLatest { if (isConfigured) ClassRepository.isSuperAdmin().catch { emit(false) } else flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Every school, for the super admin. */
    val allSchools: StateFlow<List<SchoolRef>> = isSuperAdmin
        .flatMapLatest { if (it) ClassRepository.allSchools().catch { emit(emptyList()) } else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Follows [block] for the open class, or shows [initial] when there is none. */
    private fun <T> live(initial: T, block: (Membership) -> Flow<T>): StateFlow<T> =
        current.flatMapLatest { m -> if (m == null) flowOf(initial) else block(m).catch { emit(initial) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    // ---------------------------------------------------------------- teacher dashboard (open class)

    val className: StateFlow<String?> = live(null) { ClassRepository.className(it.classId) }
    val serverOffset: StateFlow<Long> = live(0L) { ClassRepository.serverTimeOffset() }
    val attention: StateFlow<Attention?> = live(null) { ClassRepository.attention(it.classId) }
    val sent: StateFlow<List<SentMessage>> = live(emptyList()) { ClassRepository.sentMessages(it.classId) }
    val sound: StateFlow<SoundSettings> = live(SoundSettings.DEFAULT) { ClassRepository.soundSettings(it.classId) }
    val devices: StateFlow<List<DeviceInfo>> = live(emptyList()) { ClassRepository.devices(it.classId) }
    val teachers: StateFlow<List<TeacherInfo>> = live(emptyList()) { ClassRepository.teachers(it.classId) }
    val codes: StateFlow<ClassCodes?> = live(null) { ClassRepository.codes(it.classId) }

    // ---------------------------------------------------------------- school screen (open school)

    private fun <T> school(initial: T, block: (schoolId: String) -> Flow<T>): StateFlow<T> =
        _openSchool.flatMapLatest { s -> if (s == null) flowOf(initial) else block(s.schoolId).catch { emit(initial) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    val schoolName: StateFlow<String?> = school(null) { ClassRepository.schoolName(it) }
    val schoolClasses: StateFlow<List<SchoolClass>> = school(emptyList()) { ClassRepository.schoolClasses(it) }
    val schoolAdmins: StateFlow<List<TeacherInfo>> = school(emptyList()) { ClassRepository.schoolAdmins(it) }
    val schoolCodes: StateFlow<SchoolCodes?> = school(null) { ClassRepository.schoolCodes(it) }

    // ---------------------------------------------------------------- student device (all its classes)

    val studentClasses: StateFlow<List<Membership>> = memberships
        .map { list -> list.filter { it.role == Role.STUDENT } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, memberships.value.filter { it.role == Role.STUDENT })

    /** Everything this student device received from all its classes, newest first. */
    val inbox: StateFlow<List<InboxMessage>> = studentClasses.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                list.map { m ->
                    ClassRepository.messages(m.classId)
                        .map { messages -> messages.map { InboxMessage(m.classId, m.className, it) } }
                        .catch { emit(emptyList()) }
                },
            ) { perClass -> perClass.flatMap { it }.sortedByDescending { it.message.id }.take(INBOX_LIMIT) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Sound is allowed only if every class of the device allows it. */
    val mySoundAllowed: StateFlow<Boolean> = studentClasses.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(true)
        } else {
            combine(list.map { ClassRepository.soundAllowedForMe(it.classId).catch { emit(true) } }) { all ->
                all.all { it }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    /** "Eyes on me" from any of the device's classes. */
    val studentAttention: StateFlow<Attention?> = studentClasses.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(null)
        } else {
            combine(list.map { ClassRepository.attention(it.classId).catch { emit(null) } }) { all ->
                all.firstOrNull { it != null }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The name a teacher gave this device in its first class. */
    val myDeviceName: StateFlow<String?> = studentClasses.flatMapLatest { list ->
        val first = list.firstOrNull()
        if (first == null) flowOf(null) else ClassRepository.myDeviceName(first.classId).catch { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // ---------------------------------------------------------------- UI state

    /** A class action (sound, codes, removing...) is running. Sending has its own flag, [sending]. */
    var busy by mutableStateOf(false)
        private set

    var sending by mutableStateOf(false)
        private set

    /** String resource of the last error to show, if any. */
    var error by mutableStateOf<Int?>(null)
        private set

    var draft by mutableStateOf("")
        private set

    var recipients by mutableStateOf<Recipients>(Recipients.Everyone)
        private set

    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    init {
        // Notice when a teacher or admin removes this device from any of its classes or schools.
        viewModelScope.launch {
            memberships.flatMapLatest { list ->
                list.map { m ->
                    flow {
                        ClassRepository.isStillMember(m).catch { }.collect { stillMember ->
                            if (!stillMember) emit(m)
                        }
                    }
                }.merge()
            }.collect { removed -> LocalSetup.markRemoved(removed) }
        }
        // Keep class and school names up to date for the start screen and the student inbox.
        viewModelScope.launch {
            memberships.flatMapLatest { list ->
                list.map { m ->
                    val names = if (m.role == Role.ADMIN) {
                        ClassRepository.schoolName(m.classId)
                    } else {
                        ClassRepository.className(m.classId)
                    }
                    names.catch { }.map { m.classId to it }
                }.merge()
            }.collect { (id, name) -> if (name != null) LocalSetup.updateClassName(id, name) }
        }
        // Close what is open when the device is no longer part of it.
        viewModelScope.launch {
            memberships.collect { list ->
                val school = _openSchool.value
                if (school != null && !school.asSuperAdmin &&
                    list.none { it.role == Role.ADMIN && it.classId == school.schoolId }
                ) {
                    _openSchool.value = null
                    _openClass.value = null
                }
                val opened = _openClass.value
                if (opened != null && _openSchool.value == null &&
                    list.none { it.role == Role.TEACHER && it.classId == opened.classId }
                ) {
                    _openClass.value = null
                }
            }
        }
    }

    fun clearError() {
        error = null
    }

    fun dismissRemovedNotice() = LocalSetup.dismissRemovedNotice()

    // ---------------------------------------------------------------- setup

    /** The teacher's or admin's name from an earlier class or school, to fill in next time. */
    val knownTeacherName: String
        get() = memberships.value.lastOrNull { it.role != Role.STUDENT }?.name.orEmpty()

    /**
     * Suggested name for a student device: its name in another class, or else the name
     * the device has in the Android settings (e.g. "Galaxy Tab A7").
     */
    val knownDeviceName: String
        get() = memberships.value.lastOrNull { it.role == Role.STUDENT }?.name?.ifBlank { null }
            ?: runCatching {
                android.provider.Settings.Global.getString(
                    getApplication<android.app.Application>().contentResolver,
                    android.provider.Settings.Global.DEVICE_NAME,
                )
            }.getOrNull()?.ifBlank { null }
            ?: android.os.Build.MODEL.orEmpty()

    fun createClass(className: String, teacherName: String, schoolCode: String, onDone: () -> Unit) = runAction {
        val m = ClassRepository.createClass(
            className.trim().take(MAX_NAME),
            teacherName.trim().take(MAX_NAME),
            schoolCode.trim().ifEmpty { null },
        )
        LocalSetup.save(m)
        openClass(m)
        onDone()
    }

    fun createSchool(schoolName: String, adminName: String, onDone: () -> Unit) = runAction {
        val m = ClassRepository.createSchool(schoolName.trim().take(MAX_SCHOOL_NAME), adminName.trim().take(MAX_NAME))
        LocalSetup.save(m)
        openSchool(SchoolRef(m.classId, m.className))
        onDone()
    }

    fun join(code: String, name: String, role: Role, onDone: () -> Unit) = runAction {
        val m = ClassRepository.join(code, name.trim().take(MAX_NAME), role)
        LocalSetup.save(m)
        when (m.role) {
            Role.STUDENT -> {
                closeAll()
                ClassService.start(getApplication())
            }
            Role.TEACHER -> openClass(m)
            Role.ADMIN -> openSchool(SchoolRef(m.classId, m.className))
        }
        onDone()
    }

    // ---------------------------------------------------------------- navigation

    /** Opens one of the device's own classes. */
    fun openClass(m: Membership) {
        resetClassView()
        _openSchool.value = null
        _openClass.value = m
    }

    fun openSchool(school: SchoolRef) {
        resetClassView()
        _openClass.value = null
        _openSchool.value = school
    }

    /** An admin (or the super admin) opens one of the school's classes with full teacher rights. */
    fun openSchoolClass(schoolClass: SchoolClass) {
        val school = _openSchool.value ?: return
        val myName = memberships.value.firstOrNull { it.role == Role.ADMIN && it.classId == school.schoolId }?.name
            ?: knownTeacherName.ifEmpty { "Admin" }
        resetClassView()
        _openClass.value = Membership(schoolClass.classId, Role.TEACHER, myName, schoolClass.name, school.name)
    }

    /** Leaves the class screen. Returns true if a school is still open underneath. */
    fun closeClass(): Boolean {
        resetClassView()
        _openClass.value = null
        return _openSchool.value != null
    }

    fun closeAll() {
        resetClassView()
        _openClass.value = null
        _openSchool.value = null
    }

    private fun resetClassView() {
        clearError()
        recipients = Recipients.Everyone
        sendResult = null
    }

    // ---------------------------------------------------------------- leaving

    /** A teacher leaves the open class. */
    fun leaveOpenClass() = runAction {
        val open = _openClass.value ?: return@runAction
        val m = memberships.value.firstOrNull { it.role == Role.TEACHER && it.classId == open.classId }
            ?: return@runAction
        ClassRepository.leave(m)
        LocalSetup.remove(m)
        _openClass.value = null
    }

    /** An admin leaves the open school. */
    fun leaveOpenSchool() = runAction {
        val open = _openSchool.value ?: return@runAction
        val m = memberships.value.firstOrNull { it.role == Role.ADMIN && it.classId == open.schoolId }
            ?: return@runAction
        ClassRepository.leave(m)
        LocalSetup.remove(m)
        closeAll()
    }

    /** A student device leaves one class; needs that class's teacher code so kids can't just walk out. */
    fun leaveStudentClass(classId: String, teacherCode: String, onDone: () -> Unit) = runAction {
        val m = memberships.value.firstOrNull { it.role == Role.STUDENT && it.classId == classId }
            ?: return@runAction
        if (!ClassRepository.isTeacherCode(m.classId, teacherCode)) {
            error = R.string.online_error_leave_code
            return@runAction
        }
        ClassRepository.leave(m)
        LocalSetup.remove(m)
        onDone()
    }

    // ---------------------------------------------------------------- student

    fun markOpened(classId: String, messageId: String) {
        if (memberships.value.none { it.role == Role.STUDENT && it.classId == classId }) return
        reportOpened(classId, messageId)
    }

    // ---------------------------------------------------------------- teacher

    fun updateDraft(text: String) {
        draft = text.take(ShareMessage.MAX_CONTENT_LENGTH)
    }

    fun updateRecipients(value: Recipients) {
        recipients = if (value is Recipients.Selected && value.uids.isEmpty()) Recipients.Everyone else value
    }

    fun clearSendResult(result: SendResult) {
        if (sendResult == result) sendResult = null
    }

    fun send() {
        val m = current.value ?: return
        val content = draft.trim()
        if (content.isEmpty() || sending) return
        val targets: Set<String>? = when (val r = recipients) {
            Recipients.Everyone -> null
            Recipients.OnlineOnly -> devices.value.filter { it.online }.map { it.uid }.toSet()
            is Recipients.Selected -> r.uids
        }
        if (targets != null && targets.isEmpty()) {
            error = R.string.send_none_online
            return
        }
        error = null
        sending = true
        viewModelScope.launch {
            val ok = try {
                withTimeout(SEND_TIMEOUT_MS) { ClassRepository.send(m.classId, m.name, content, targets) }
                true
            } catch (e: TimeoutCancellationException) {
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                sending = false
            }
            if (ok) {
                draft = ""
                // "Online only" stays selected; picked devices go back to the whole class.
                if (recipients is Recipients.Selected) recipients = Recipients.Everyone
            }
            sendResult = SendResult(ok)
        }
    }

    fun deleteMessage(message: SentMessage) = teacherAction { ClassRepository.deleteMessage(it, message) }

    fun setClassSound(allowed: Boolean) = teacherAction { ClassRepository.setClassSound(it, allowed) }

    /** Mutes the whole class; sound comes back by itself after [minutes]. */
    fun muteFor(minutes: Int) = teacherAction {
        val until = System.currentTimeMillis() + serverOffset.value + minutes * 60_000L
        ClassRepository.setClassSound(it, allowed = false, mutedUntil = until)
    }

    fun setDeviceSound(deviceUid: String, allowed: Boolean) =
        teacherAction { ClassRepository.setDeviceSound(it, deviceUid, allowed) }

    fun renameDevice(deviceUid: String, name: String) {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return
        teacherAction { ClassRepository.renameDevice(it, deviceUid, clean) }
    }

    /** Puts "Eyes on me" on every student device of the class, or takes it away. */
    fun setAttention(on: Boolean, text: String) = teacherAction {
        val m = current.value ?: return@teacherAction
        ClassRepository.setAttention(it, if (on) Attention(text, m.name) else null)
    }

    fun removeDevice(deviceUid: String) = teacherAction { ClassRepository.removeDevice(it, deviceUid) }

    fun removeTeacher(teacherUid: String) = teacherAction { ClassRepository.removeTeacher(it, teacherUid) }

    fun renewCode(role: Role) = teacherAction { ClassRepository.renewCode(it, role) }

    private fun teacherAction(block: suspend (classId: String) -> Unit) = runAction {
        val m = current.value ?: return@runAction
        block(m.classId)
    }

    // ---------------------------------------------------------------- school admin

    fun createSchoolClass(className: String, onDone: () -> Unit) = runAction {
        val school = _openSchool.value ?: return@runAction
        val name = className.trim().take(MAX_NAME)
        val classId = ClassRepository.createSchoolClass(school.copy(name = schoolName.value ?: school.name), name)
        onDone()
        openSchoolClass(SchoolClass(classId, name))
    }

    fun renewSchoolCode(kind: CodeKind) = schoolAction { ClassRepository.renewSchoolCode(it, kind) }

    fun removeAdmin(adminUid: String) = schoolAction { ClassRepository.removeAdmin(it, adminUid) }

    private fun schoolAction(block: suspend (schoolId: String) -> Unit) = runAction {
        val school = _openSchool.value ?: return@runAction
        block(school.schoolId)
    }

    private fun runAction(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                withTimeout(ACTION_TIMEOUT_MS) { block() }
            } catch (e: TimeoutCancellationException) {
                error = R.string.online_error_connection
            } catch (e: CancellationException) {
                throw e
            } catch (e: JoinException) {
                error = when (e.reason) {
                    JoinException.Reason.CODE_NOT_FOUND -> R.string.online_error_code
                    JoinException.Reason.STUDENT_CODE_FOR_TEACHER -> R.string.online_error_student_code
                    JoinException.Reason.TEACHER_CODE_FOR_STUDENT -> R.string.online_error_teacher_code
                    JoinException.Reason.WRONG_KIND_OF_CODE -> R.string.online_error_wrong_code
                }
            } catch (e: Exception) {
                error = if (e.message?.contains("permission denied", ignoreCase = true) == true) {
                    R.string.online_error_denied
                } else {
                    R.string.online_error_generic
                }
            } finally {
                busy = false
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val ACTION_TIMEOUT_MS = 20_000L
        private const val SEND_TIMEOUT_MS = 15_000L
        private const val MAX_NAME = 40
        private const val MAX_SCHOOL_NAME = 60
        private const val INBOX_LIMIT = 100

        /** Fire-and-forget "opened" report; also used when a link is opened from a notification. */
        fun reportOpened(classId: String, messageId: String) {
            if (!LocalSetup.rememberOpened(messageId)) return
            OpenedReporter.report(classId, messageId)
        }
    }
}

/** Sends "opened" receipts on the app's own scope so they finish even if the screen closes. */
private object OpenedReporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun report(classId: String, messageId: String) {
        scope.launch {
            try {
                withTimeout(30_000L) { ClassRepository.markOpened(classId, messageId) }
            } catch (e: Exception) {
                // Includes the timeout. Report again the next time it is opened.
                LocalSetup.forgetOpened(messageId)
            }
        }
    }
}
