package com.yywspace.anethack.keybord

import com.yywspace.anethack.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class TouchActionParserTest {
    @Test
    fun parsesLabelsAndPreservesSequenceCommands() {
        val pages = TouchActionParser.parsePages(
            "Setting SS|Save #quit|Quit S#engrave#-nL\"Elbereth\":|Elber Keyboard\n#|Extend d|Drop",
        )

        assertEquals(2, pages.size)
        assertEquals(listOf("SS", "#quit", "S#engrave#-nL\"Elbereth\":"), pages[0].map { it.command })
        assertEquals(listOf("Save", "Quit", "Elber"), pages[0].map { it.label })
        assertEquals(listOf("#", "d"), pages[1].map { it.command })
    }

    @Test
    fun filtersDockCommandsAndSplitsLongRows() {
        val pages = TouchActionParser.parsePages("Setting Keyboard Center a b c d e f g")

        assertEquals(2, pages.size)
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), pages[0].map { it.command })
        assertEquals(listOf("g"), pages[1].map { it.command })
    }

    @Test
    fun mapsKnownAndUnknownIcons() {
        assertEquals(R.drawable.ic_touch_save, TouchActionIcons.forCommand("SS"))
        assertEquals(R.drawable.ic_touch_engrave, TouchActionIcons.forCommand("S#engrave#-nL\"Elbereth\":"))
        assertEquals(R.drawable.ic_touch_fallback, TouchActionIcons.forCommand("a"))
    }

    @Test
    fun clampsWheelInsideSafeBounds() {
        val center = TouchWheelGeometry.clampCenter(
            anchor = WheelPoint(2f, 995f),
            width = 500f,
            height = 1000f,
            extentX = 100f,
            extentY = 100f,
            safeTop = 80f,
            safeBottom = 120f,
        )

        assertEquals(100f, center.x)
        assertEquals(780f, center.y)
        assertTrue(center.y - 100f >= 80f)
        assertTrue(center.y + 100f <= 880f)
    }
}
