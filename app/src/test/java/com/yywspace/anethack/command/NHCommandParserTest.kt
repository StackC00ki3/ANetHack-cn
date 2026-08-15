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

    @Test
    fun parsesCtrlKeyNotation() {
        assertEquals(16.toChar(), NHCommandParser.parseNHCommand("^p").single().key)
        assertEquals(16.toChar(), NHCommandParser.parseNHCommand("^P").single().key)
        assertEquals(24.toChar(), NHCommandParser.parseNHCommand("^X").single().key)
        assertEquals(27.toChar(), NHCommandParser.parseNHCommand("^[").single().key)
        assertEquals(127.toChar(), NHCommandParser.parseNHCommand("^?").single().key)
    }

    @Test
    fun parsesAltKeyNotationAsMetaBit() {
        assertEquals((128 + 'p'.code).toChar(), NHCommandParser.parseNHCommand("M-p").single().key)
        assertEquals((128 + 'P'.code).toChar(), NHCommandParser.parseNHCommand("M-P").single().key)
        assertEquals((128 + '2'.code).toChar(), NHCommandParser.parseNHCommand("M-2").single().key)
    }

    @Test
    fun parsesEscapeKeyNotation() {
        assertEquals(27.toChar(), NHCommandParser.parseNHCommand("esc").single().key)
        assertEquals(27.toChar(), NHCommandParser.parseNHCommand("ESC").single().key)
    }

    @Test
    fun keepsSingleCommaAsPickupKey() {
        assertEquals(',', NHCommandParser.parseNHCommand(",").single().key)
    }

    @Test
    fun sendsPlainCharactersInOrder() {
        val keys = NHCommandParser.parseNHCommand("10s").map { it.key }
        assertEquals(listOf('1', '0', 's'), keys)
    }

    @Test
    fun runsSequencesWithCtrlAndAltKeysInOrder() {
        val keys = NHCommandParser.parseNHCommand("10s^pM-za").map { it.key }
        assertEquals(
            listOf('1', '0', 's', 16.toChar(), (128 + 'z'.code).toChar(), 'a'),
            keys,
        )
    }

    @Test
    fun ctrlAndAltKeysAreCaseSensitive() {
        // M-p and M-P are different keys; lowercase m- is not a meta prefix
        assertEquals(
            listOf((128 + 'p'.code).toChar(), (128 + 'P'.code).toChar()),
            NHCommandParser.parseNHCommand("M-pM-P").map { it.key },
        )
        assertEquals(
            listOf('m', '-', 'p'),
            NHCommandParser.parseNHCommand("m-p").map { it.key },
        )
    }

    @Test
    fun incompleteNotationFallsBackToPlainKeys() {
        assertEquals(listOf('^'), NHCommandParser.parseNHCommand("^").map { it.key })
        assertEquals(listOf('M', '-'), NHCommandParser.parseNHCommand("M-").map { it.key })
        assertEquals(
            listOf('a', '^'),
            NHCommandParser.parseNHCommand("a^").map { it.key },
        )
    }
}
