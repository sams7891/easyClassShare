package com.easyclassshare.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareMessageTest {
    @Test
    fun jsonRoundTrip() {
        val original = ShareMessage(room = "fox", senderName = "Ms. Lee", content = "https://example.com")
        assertEquals(original, ShareMessage.fromJson(original.toJson()))
    }

    @Test
    fun ignoresForeignOrBrokenPackets() {
        assertNull(ShareMessage.fromJson("not json"))
        assertNull(ShareMessage.fromJson("""{"app":"other","id":"1","room":"fox","content":"x"}"""))
        assertNull(ShareMessage.fromJson("""{"app":"easyClassShare","id":"1","room":"fox","content":""}"""))
    }
}
