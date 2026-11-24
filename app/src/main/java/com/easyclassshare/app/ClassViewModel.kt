package com.easyclassshare.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easyclassshare.app.data.ShareMessage
import com.easyclassshare.app.online.ClassCodes
import com.easyclassshare.app.online.ClassRepository
import com.easyclassshare.app.online.DeviceInfo
import com.easyclassshare.app.online.JoinException
import com.easyclassshare.app.online.LocalSetup
import com.easyclassshare.app.online.Membership
import com.easyclassshare.app.online.OnlineConfig
import com.easyclassshare.app.online.Role
import com.easyclassshare.app.online.SoundSettings
import com.easyclassshare.app.online.TeacherInfo
import com.easyclassshare.app.service.ClassService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** State and actions for online classes (the one-time setup, the class screens and Manage class). */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassViewModel(application: Application) : AndroidViewModel(application) {
    val isConfigured: Boolean = OnlineConfig.isConfigured

    /** The class shown right now. */
    val membership: StateFlow<Membership?> = LocalSetup.membership

    /** All classes of this device (a teacher can have several). */
    val memberships: StateFlow<List<Membership>> = LocalSetup.memberships

    /** Name of a class a teacher just removed this device from (null = nothing to tell). */
    val removedFrom: StateFlow<String?> = LocalSetup.removedFrom

    /** Follows [block] for the current class, or shows [initial] when there is none. */
    private fun <T> live(initial: T, teacherOnly: Boolean = false, block: (Membership) -> Flow<T>): StateFlow<T> =
        membership.flatMapLatest { m ->
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
    val messages: StateFlow<List<ShareMessage>> = live(emptyList()) { ClassRepository.messages(it.classId) }
    val mySoundAllowed: StateFlow<Boolean> = live(true) {
        if (it.role == Role.STUDENT) ClassRepository.soundAllowedForMe(it.classId) else flowOf(true)
    }
    val myUid: StateFlow<String?> = live(null) { flow { emit(ClassRepository.uid()) } }

    // Teacher only
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

    var busy by mutableStateOf(false)
        private set

    /** String resource of the last error to show, if any. */
    var error by mutableStateOf<Int?>(null)
        private set

    var draft by mutableStateOf("")
        private set
    var sendResult by mutableStateOf<SendResult?>(null)
        private set

    init {
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

    /** The teacher's name from an earlier class, to fill in when adding another class. */
    val knownTeacherName: String
        get() = memberships.value.lastOrNull { it.role == Role.TEACHER }?.name.orEmpty()

    fun createClass(className: String, teacherName: String, onDone: () -> Unit) = runAction {
        val m = ClassRepository.createClass(className.trim().take(MAX_NAME), teacherName.trim().take(MAX_NAME))
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
        clearError()
        draft = ""
        LocalSetup.setActive(classId)
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

    // ---------------------------------------------------------------- teacher

    fun updateDraft(text: String) {
        draft = text.take(ShareMessage.MAX_CONTENT_LENGTH)
    }

    fun clearSendResult(result: SendResult) {
        if (sendResult == result) sendResult = null
    }

    fun send() {
        val m = membership.value ?: return
        val content = draft.trim()
        if (content.isEmpty() || busy) return
        busy = true
        viewModelScope.launch {
            val ok = try {
                withTimeout(SEND_TIMEOUT_MS) { ClassRepository.send(m.classId, m.name, content) }
                true
            } catch (e: TimeoutCancellationException) {
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                busy = false
            }
            if (ok) draft = ""
            sendResult = SendResult(ok)
        }
    }

    fun setClassSound(allowed: Boolean) = teacherAction { ClassRepository.setClassSound(it, allowed) }

    fun setDeviceSound(deviceUid: String, allowed: Boolean) =
        teacherAction { ClassRepository.setDeviceSound(it, deviceUid, allowed) }

    fun removeDevice(deviceUid: String) = teacherAction { ClassRepository.removeDevice(it, deviceUid) }

    fun removeTeacher(teacherUid: String) = teacherAction { ClassRepository.removeTeacher(it, teacherUid) }

    fun renewCode(role: Role) = teacherAction { ClassRepository.renewCode(it, role) }

    private fun teacherAction(block: suspend (classId: String) -> Unit) = runAction {
        val m = membership.value ?: return@runAction
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
                }
            } catch (e: Exception) {
                error = R.string.online_error_generic
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val ACTION_TIMEOUT_MS = 20_000L
        const val SEND_TIMEOUT_MS = 15_000L
        const val MAX_NAME = 40
    }
}
