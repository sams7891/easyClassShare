package com.easyclassshare.app.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun generatedCodesUseOnlyEasyCharacters() {
        repeat(200) {
            val code = JoinCodes.generate(JoinCodes.STUDENT_LENGTH)
            assertEquals(JoinCodes.STUDENT_LENGTH, code.length)
            assertTrue(code.all { it in JoinCodes.ALPHABET })
            assertTrue(JoinCodes.isWellFormed(code))
        }
        assertTrue(JoinCodes.isWellFormed(JoinCodes.generate(JoinCodes.TEACHER_LENGTH)))
    }

    @Test
    fun normalizeCleansTypedCodes() {
        assertEquals("ABC234", JoinCodes.normalize(" abc-234 "))
        assertEquals("XY7Z9Q", JoinCodes.normalize("xy7 z9q"))
        assertEquals("", JoinCodes.normalize("../#$[]"))
    }

    @Test
    fun rejectsMalformedCodes() {
        assertFalse(JoinCodes.isWellFormed(""))
        assertFalse(JoinCodes.isWellFormed("ABC"))
        assertFalse(JoinCodes.isWellFormed("ABCDEFGHJKLM"))
        assertFalse(JoinCodes.isWellFormed("abc234"))
    }

    @Test
    fun soundIsAllowedUntilATeacherTurnsItOff() {
        assertTrue(SoundPolicy.isAllowed(null, null))
        assertFalse(SoundPolicy.isAllowed(false, null))
        assertTrue(SoundPolicy.isAllowed(true, null))
    }

    @Test
    fun deviceSwitchWinsOverClassSwitch() {
        assertTrue(SoundPolicy.isAllowed(false, true))
        assertFalse(SoundPolicy.isAllowed(true, false))

        val settings = SoundSettings(classWide = false, perDevice = mapOf("tablet1" to true))
        assertTrue(settings.isAllowed("tablet1"))
        assertFalse(settings.isAllowed("tablet2"))
        assertFalse(settings.classWideAllowed)
    }
}
