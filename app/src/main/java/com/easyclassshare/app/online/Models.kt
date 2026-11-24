package com.easyclassshare.app.online

import java.security.SecureRandom

enum class Role { STUDENT, TEACHER }

/** This device's place in an online class. Saved on the device after the one-time setup. */
data class Membership(val classId: String, val role: Role, val name: String)

/** A student tablet in the class, as the teacher sees it. */
data class DeviceInfo(val uid: String, val name: String, val online: Boolean)

data class TeacherInfo(val uid: String, val name: String)

data class ClassCodes(val studentCode: String, val teacherCode: String)

/** Sound switches set by the teachers: one for the whole class plus optional per-device ones. */
data class SoundSettings(val classWide: Boolean?, val perDevice: Map<String, Boolean>) {
    val classWideAllowed: Boolean get() = SoundPolicy.isAllowed(classWide, null)
    fun isAllowed(deviceUid: String): Boolean = SoundPolicy.isAllowed(classWide, perDevice[deviceUid])

    companion object {
        val DEFAULT = SoundSettings(null, emptyMap())
    }
}

object SoundPolicy {
    /** A device's own switch wins over the class-wide one. Sound is allowed until a teacher turns it off. */
    fun isAllowed(classWide: Boolean?, deviceOverride: Boolean?): Boolean = deviceOverride ?: classWide ?: true
}

/** Short codes typed in once to join a class. No 0/O or 1/I so they are easy to read aloud. */
object JoinCodes {
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val STUDENT_LENGTH = 6
    const val TEACHER_LENGTH = 8

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
    enum class Reason { CODE_NOT_FOUND, STUDENT_CODE_FOR_TEACHER, TEACHER_CODE_FOR_STUDENT }
}
