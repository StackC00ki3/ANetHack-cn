package com.yywspace.anethack.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class NHCommandParserTest {
    @Test
    fun parsesControlCharacterCodesUsedByTouchActions() {
        assertEquals(4.toChar(), NHCommandParser.parseNHCommand("4").single().key)
        assertEquals(15.toChar(), NHCommandParser.parseNHCommand("15").single().key)
        assertEquals(16.toChar(), NHCommandParser.parseNHCommand("16").single().key)
        assertEquals(24.toChar(), NHCommandParser.parseNHCommand("24").single().key)
    }

    @Test
    fun parsesSaveSequenceAndNamedExtendedCommand() {
        val save = NHCommandParser.parseNHCommand("SS")
        assertEquals(1, save.size)
        assertEquals('S', save.single().key)

        val loot = NHCommandParser.parseNHCommand("#loot").single()
        assertTrue(loot is NHExtendCommand)
        assertEquals("loot", (loot as NHExtendCommand).name)
    }
}
