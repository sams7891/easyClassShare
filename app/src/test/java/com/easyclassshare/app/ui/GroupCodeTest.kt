package com.easyclassshare.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupCodeTest {
    @Test
    fun splitsCodesIntoTwoHalves() {
        assertEquals("ABC DEF", groupCode("ABCDEF"))          // class code
        assertEquals("ABCD EFG", groupCode("ABCDEFG"))        // school code
        assertEquals("ABCD EFGH", groupCode("ABCDEFGH"))      // teacher code
        assertEquals("ABCDE FGHIJ", groupCode("ABCDEFGHIJ"))  // admin code
    }

    @Test
    fun leavesShortTextAlone() {
        assertEquals("AB", groupCode("AB"))
    }
}
