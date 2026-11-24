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
import com.easyclassshare.app.online.JoinException
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Membership
import com.easyclassshare.app.online.OnlineConfig
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.online.SchoolClass
import com.easyclassshare.app.online.SchoolCodes
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** State and actions for online classes (the one-time setup, the class screens and class settings). */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassViewModel(application: Application) : AndroidViewModel(application) {
    val isConfigured: Boolean = OnlineConfig.isConfigured

    /** The class (or, for an admin, the school) chosen on this device. */
    val membership: StateFlow<Membership?> = LocalSetup.membership

    /** A school class an admin opened. The admin gets teacher rights there without being a member. */
    private val adminClass = MutableStateFlow<Membership?>(null)

    /**
     * The class shown on the class screens: the chosen class, or the school class an admin opened.
     * Always has role STUDENT or TEACHER.
     */
    val current: StateFlow<Membership?> = combine(membership, adminClass) { m, opened ->
        if (m?.role == Role.ADMIN) opened else m
    }.stateIn(viewModelScope, SharingStarted.Eagerly, membership.value.takeIf { it?.role != Role.ADMIN })

    /** True while an admin is looking at one of the school's classes. */
    val isAdminView: StateFlow<Boolean> = combine(membership, adminClass) { m, opened ->
        m?.role == Role.ADMIN && opened != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** All classes of this device (a teacher can have several). */
    val memberships: StateFlow<List<Membership>> = LocalSetup.memberships

    /** Name of a class a teacher just removed this device from (null = nothing to tell). */
    val removedFrom: StateFlow<String?> = LocalSetup.removedFrom

    /** Follows [block] for the current class, or shows [initial] when there is none. */
    private fun <T> live(initial: T, teacherOnly: Boolean = false, block: (Membership) -> Flow<T>): StateFlow<T> =
        current.flatMapLatest { m ->
            if (m == null || (teacherOnly && m.role != Role.TEACHER)) {
                flowOf(initial)
            } else {
                block(m).catch { emit(initial) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    val className: StateFlow<String?> = live(null) { m ->
        ClassRepository.className(m.classId).onEach { name ->
            if (name != null) LocalSetup.updateClassName(m.classId, name)
        }
    }
    val myUid: StateFlow<String?> = live(null) { flow { emit(ClassRepository.uid()) } }

    /** Server clock minus device clock, for timed mutes. */
    val serverOffset: StateFlow<Long> = live(0L) { ClassRepository.serverTimeOffset() }

    val attention: StateFlow<Attention?> = live(null) { ClassRepository.attention(it.classId) }

    // Student only
    val messages: StateFlow<List<ShareMessage>> = live(emptyList()) {
        if (it.role == Role.STUDENT) ClassRepository.messages(it.classId) else flowOf(emptyList())
    }
    val mySoundAllowed: StateFlow<Boolean> = live(true) {
        if (it.role == Role.STUDENT) ClassRepository.soundAllowedForMe(it.classId) else flowOf(true)
    }

    /** The name shown for this tablet; a teacher may have renamed it. */
    val myDeviceName: StateFlow<String?> = live(null) {
        if (it.role == Role.STUDENT) ClassRepository.myDeviceName(it.classId) else flowOf(null)
    }

    // Teacher only
    val sent: StateFlow<List<SentMessage>> = live(emptyList(), teacherOnly = true) {
        ClassRepository.sentMessages(it.classId)
    }
    val sound: StateFlow<SoundSettings> = live(SoundSettings.DEFAULT, teacherOnly = true) {
        ClassRepository.soundSettings(it.classId)
    }
    val devices: StateFlow<List<DeviceInfo>> = live(emptyList(), teacherOnly = true) {
        ClassRepository.devices(it.classId)
    }
    val teachers: StateFlow<List<TeacherInfo>> = live(emptyList(), teacherOnly = true) {
        ClassRepository.teachers(it.classId)
    }
    val codes: StateFlow<ClassCodes?> = live(null, teacherOnly = true) { ClassRepository.codes(it.classId) }

    // School admin only (for the chosen school)
    private fun <T> school(initial: T, block: (schoolId: String) -> Flow<T>): StateFlow<T> =
        membership.flatMapLatest { m ->
            if (m?.role != Role.ADMIN) flowOf(initial) else block(m.classId).catch { emit(initial) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    val schoolName: StateFlow<String?> = school(null) { id ->
        ClassRepository.schoolName(id).onEach { name -> if (name != null) LocalSetup.updateClassName(id, name) }
    }
    val schoolClasses: StateFlow<List<SchoolClass>> = school(emptyList()) { ClassRepository.schoolClasses(it) }
    val schoolAdmins: StateFlow<List<TeacherInfo>> = school(emptyList()) { ClassRepository.schoolAdmins(it) }
    val schoolCodes: StateFlow<SchoolCodes?> = school(null) { ClassRepository.schoolCodes(it) }

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

    /** Who the next message goes to: null = the whole class, else these tablets. */
    var recipients by mutableStateOf<Set<String>?>(null)
        private set

    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    init {
        // An opened school class closes when the device is no longer working as that school's admin.
        viewModelScope.launch {
            var schoolId: String? = null
            membership.collect { m ->
                val now = m?.takeIf { it.role == Role.ADMIN }?.classId
                if (now != schoolId) adminClass.value = null
                schoolId = now
            }
        }
        // Notice when a teacher removes this device (or this teacher) from any of its classes.
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
    }

    fun clearError() {
        error = null
    }

    fun dismissRemovedNotice() = LocalSetup.dismissRemovedNotice()

    // ---------------------------------------------------------------- setup

    /** The teacher's or admin's name from an earlier class or school, to fill in next time. */
    val knownTeacherName: String
        get() = memberships.value.lastOrNull { it.role != Role.STUDENT }?.name.orEmpty()

    fun createClass(className: String, teacherName: String, schoolCode: String, onDone: () -> Unit) = runAction {
        val m = ClassRepository.createClass(
            className.trim().take(MAX_NAME),
            teacherName.trim().take(MAX_NAME),
            schoolCode.trim().ifEmpty { null },
        )
        LocalSetup.save(m)
        onDone()
    }

    fun createSchool(schoolName: String, adminName: String, onDone: () -> Unit) = runAction {
        val m = ClassRepository.createSchool(schoolName.trim().take(MAX_SCHOOL_NAME), adminName.trim().take(MAX_NAME))
        LocalSetup.save(m)
        onDone()
    }

    fun join(code: String, name: String, role: Role, onDone: () -> Unit) = runAction {
        val m = ClassRepository.join(code, name.trim().take(MAX_NAME), role)
        LocalSetup.save(m)
        if (m.role == Role.STUDENT) ClassService.start(getApplication())
        onDone()
    }

    fun switchClass(classId: String) {
        resetClassView()
        adminClass.value = null
        LocalSetup.setActive(classId)
    }

    private fun resetClassView() {
        clearError()
        draft = ""
        recipients = null
        sendResult = null
    }

    // ---------------------------------------------------------------- school admin

    /** An admin opens one of the school's classes with full teacher rights. */
    fun openSchoolClass(schoolClass: SchoolClass) {
        val school = membership.value?.takeIf { it.role == Role.ADMIN } ?: return
        resetClassView()
        adminClass.value = Membership(schoolClass.classId, Role.TEACHER, school.name, schoolClass.name)
    }

    fun closeSchoolClass() {
        resetClassView()
        adminClass.value = null
    }

    fun createSchoolClass(className: String, onDone: () -> Unit) = runAction {
        val school = membership.value?.takeIf { it.role == Role.ADMIN } ?: return@runAction
        val name = className.trim().take(MAX_NAME)
        val classId = ClassRepository.createSchoolClass(school.classId, name)
        onDone()
        openSchoolClass(SchoolClass(classId, name))
    }

    fun renewSchoolCode(kind: CodeKind) = schoolAction { ClassRepository.renewSchoolCode(it, kind) }

    fun removeAdmin(adminUid: String) = schoolAction { ClassRepository.removeAdmin(it, adminUid) }

    private fun schoolAction(block: suspend (schoolId: String) -> Unit) = runAction {
        val m = membership.value ?: return@runAction
        if (m.role == Role.ADMIN) block(m.classId)
    }

    /** Teachers leave freely; students need a teacher code so kids can't just walk out of class. */
    fun leave(teacherCode: String? = null) = runAction {
        val m = membership.value ?: return@runAction
        if (m.role == Role.STUDENT && !ClassRepository.isTeacherCode(m.classId, teacherCode.orEmpty())) {
            error = R.string.online_error_leave_code
            return@runAction
        }
        ClassRepository.leave(m)
        LocalSetup.remove(m)
    }

    // ---------------------------------------------------------------- student

    /** Reports to the teachers that this tablet opened [messageId]; each message is reported once. */
    fun markOpened(messageId: String) {
        val m = membership.value ?: return
        if (m.role != Role.STUDENT) return
        reportOpened(m.classId, messageId)
    }

    // ---------------------------------------------------------------- teacher

    fun updateDraft(text: String) {
        draft = text.take(ShareMessage.MAX_CONTENT_LENGTH)
    }

    fun updateRecipients(uids: Set<String>?) {
        recipients = uids?.takeIf { it.isNotEmpty() }
    }

    fun clearSendResult(result: SendResult) {
        if (sendResult == result) sendResult = null
    }

    fun send() {
        val m = current.value ?: return
        val content = draft.trim()
        if (content.isEmpty() || sending) return
        val targets = recipients
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
                recipients = null
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

    /** Puts "Eyes on me" on every student tablet, or takes it away. */
    fun setAttention(on: Boolean, text: String) = teacherAction {
        val m = current.value ?: return@teacherAction
        ClassRepository.setAttention(it, if (on) Attention(text, m.name) else null)
    }

    fun removeDevice(deviceUid: String) = teacherAction { ClassRepository.removeDevice(it, deviceUid) }

    fun removeTeacher(teacherUid: String) = teacherAction { ClassRepository.removeTeacher(it, teacherUid) }

    fun renewCode(role: Role) = teacherAction { ClassRepository.renewCode(it, role) }

    private fun teacherAction(block: suspend (classId: String) -> Unit) = runAction {
        val m = current.value ?: return@runAction
        if (m.role == Role.TEACHER) block(m.classId)
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
