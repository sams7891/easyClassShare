package com.easyclassshare.app.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertTrue(settings.isAllowed("tablet1", now = 0L))
        assertFalse(settings.isAllowed("tablet2", now = 0L))
        assertFalse(settings.classWideAllowed(now = 0L))
    }

    @Test
    fun timedMuteEndsByItself() {
        val settings = SoundSettings(classWide = false, perDevice = emptyMap(), mutedUntil = 1_000L)
        assertFalse(settings.classWideAllowed(now = 999L))
        assertEquals(1_000L, settings.timerEnd(now = 999L))
        assertTrue(settings.classWideAllowed(now = 1_000L))
        assertNull(settings.timerEnd(now = 1_000L))
        assertTrue(settings.isAllowed("tablet1", now = 2_000L))
    }

    @Test
    fun deviceSwitchStillWinsAfterTimedMuteEnds() {
        val settings = SoundSettings(classWide = false, perDevice = mapOf("tablet1" to false), mutedUntil = 1_000L)
        assertFalse(settings.isAllowed("tablet1", now = 5_000L))
        assertTrue(settings.isAllowed("tablet2", now = 5_000L))
    }

    @Test
    fun lowBatteryOnlyWhenNotCharging() {
        assertTrue(DeviceInfo("a", "A", online = true, battery = 15).batteryLow)
        assertFalse(DeviceInfo("a", "A", online = true, battery = 15, charging = true).batteryLow)
        assertFalse(DeviceInfo("a", "A", online = true, battery = 80).batteryLow)
        assertFalse(DeviceInfo("a", "A", online = true).batteryLow)
    }
}
