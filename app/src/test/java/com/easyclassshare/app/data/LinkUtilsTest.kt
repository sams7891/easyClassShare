package com.easyclassshare.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkUtilsTest {
    @Test
    fun recognisesLinks() {
        assertTrue(LinkUtils.looksLikeLink("https://example.com"))
        assertTrue(LinkUtils.looksLikeLink("  http://example.com/page?x=1  "))
        assertTrue(LinkUtils.looksLikeLink("www.khanacademy.org/math"))
        assertTrue(LinkUtils.looksLikeLink("scratch.mit.edu"))
    }

    @Test
    fun rejectsNonLinks() {
        assertFalse(LinkUtils.looksLikeLink("Read page 12 please"))
        assertFalse(LinkUtils.looksLikeLink("javascript:alert(1)"))
        assertFalse(LinkUtils.looksLikeLink("ftp://example.com"))
        assertFalse(LinkUtils.looksLikeLink(""))
    }

    @Test
    fun normalizeAddsHttps() {
        assertEquals("https://scratch.mit.edu", LinkUtils.normalize("scratch.mit.edu"))
        assertEquals("http://example.com", LinkUtils.normalize("http://example.com"))
        assertNull(LinkUtils.normalize("hello class"))
    }
}
