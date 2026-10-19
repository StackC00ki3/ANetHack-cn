package com.yywspace.anethack.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NHColorTest {

    @Test
    fun palettesCoverEveryColor() {
        // Indexing the palettes by ordinal throws if they fall out of sync
        // with the enum, so touching every entry is the assertion.
        NHColor.values().forEach {
            it.toColor()
            it.toColor(lightBackground = true)
        }
    }

    @Test
    fun lightPaletteRemapsWhiteText() {
        assertNotEquals(NHColor.CLR_WHITE.toColor(), NHColor.CLR_WHITE.toColor(true))
        assertEquals(NHColor.CLR_BLACK.toColor(true), NHColor.CLR_WHITE.toColor(true))
    }
}
