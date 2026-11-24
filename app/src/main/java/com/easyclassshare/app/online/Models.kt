package com.easyclassshare.app.online

import com.easyclassshare.app.data.LinkUtils
import java.security.SecureRandom

/** [ADMIN] is a school admin: teacher rights in every class of the school. */
enum class Role { STUDENT, TEACHER, ADMIN }

/**
 * This device's place in an online class or school, saved on the device after the one-time setup.
 * A student tablet has exactly one; a teacher or admin can have several.
 */
data class Membership(
    /** The class id, or for [Role.ADMIN] the school id. */
    val classId: String,
    val role: Role,
    /** The person's name as shown to the class. */
    val name: String,
    /** Last known class (or school) name, so lists show names even before going online. */
    val className: String = "",
)

/** What a join code is for. */
enum class CodeKind { STUDENT, TEACHER, ADMIN, SCHOOL }

/** A class in a school, as the school admins see it. */
data class SchoolClass(val classId: String, val name: String)

data class SchoolCodes(
    /** Teachers enter it when creating a class, to put the class into the school. */
    val schoolCode: String,
    /** Lets another person become an admin of the school. */
    val adminCode: String,
)

/** A student tablet in the class, as the teacher sees it. */
data class DeviceInfo(
    val uid: String,
    val name: String,
    val online: Boolean,
    /** Server time the tablet last connected or disconnected. */
    val lastSeen: Long? = null,
    /** Battery level 0-100, if the tablet reported one. */
    val battery: Int? = null,
    val charging: Boolean = false,
    /** Whether the tablet allows the full-screen "Eyes on me" message (null = not reported yet). */
    val canLock: Boolean? = null,
) {
    val batteryLow: Boolean get() = battery != null && battery <= LOW_BATTERY && !charging

    companion object {
        const val LOW_BATTERY = 20
    }
}

/** Something a teacher shared, with who it went to and who opened it. */
data class SentMessage(
    val id: String,
    val from: String,
    val content: String,
    val timestamp: Long,
    /** The tablets it was sent to, or null if it went to the whole class. */
    val targets: Set<String>?,
    /** Tablets that opened the link (or saw the text). */
    val openedBy: Set<String> = emptySet(),
) {
    val isLink: Boolean get() = LinkUtils.looksLikeLink(content)
}

/** The full-screen "Eyes on me" message a teacher puts on every student tablet. */
data class Attention(val text: String, val from: String)

data class TeacherInfo(val uid: String, val name: String)

data class ClassCodes(val studentCode: String, val teacherCode: String)

/**
 * Sound switches set by the teachers: one for the whole class plus optional per-device ones.
 * A class-wide mute can end by itself at [mutedUntil] (server time).
 */
data class SoundSettings(
    val classWide: Boolean?,
    val perDevice: Map<String, Boolean>,
    val mutedUntil: Long? = null,
) {
    fun classWideAllowed(now: Long): Boolean = SoundPolicy.isAllowed(classWide, null, mutedUntil, now)
    fun isAllowed(deviceUid: String, now: Long): Boolean =
        SoundPolicy.isAllowed(classWide, perDevice[deviceUid], mutedUntil, now)

    /** End of a timed class-wide mute that is still running, else null. */
    fun timerEnd(now: Long): Long? = mutedUntil?.takeIf { classWide == false && it > now }

    companion object {
        val DEFAULT = SoundSettings(null, emptyMap())
    }
}

object SoundPolicy {
    /**
     * A device's own switch wins over the class-wide one. Sound is allowed until a teacher
     * turns it off; a timed class-wide mute ends at [mutedUntil].
     */
    fun isAllowed(classWide: Boolean?, deviceOverride: Boolean?, mutedUntil: Long? = null, now: Long = 0L): Boolean {
        if (deviceOverride != null) return deviceOverride
        if (classWide == false && mutedUntil != null && now >= mutedUntil) return true
        return classWide ?: true
    }
}

/** Short codes typed in once to join a class. No 0/O or 1/I so they are easy to read aloud. */
object JoinCodes {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val STUDENT_LENGTH = 6
    const val TEACHER_LENGTH = 8
    const val SCHOOL_LENGTH = 7
    const val ADMIN_LENGTH = 10

    private val random = SecureRandom()

    fun generate(length: Int): String =
        (1..length).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** Upper-cases and drops spaces, dashes and anything else that can't be part of a code. */
    fun normalize(input: String): String =
        input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun isWellFormed(code: String): Boolean =
        code.length in STUDENT_LENGTH..10 && code.all { it in 'A'..'Z' || it in '0'..'9' }
}

class JoinException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { CODE_NOT_FOUND, STUDENT_CODE_FOR_TEACHER, TEACHER_CODE_FOR_STUDENT, WRONG_KIND_OF_CODE }
}
