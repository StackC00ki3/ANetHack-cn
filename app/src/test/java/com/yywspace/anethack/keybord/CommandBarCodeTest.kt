package com.yywspace.anethack.keybord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class CommandBarCodeTest {
    @Test
    fun roundTripsDefaultConfig() {
        val code = CommandBarCode.encode(TouchCommandBar.DEFAULT_CONFIG)

        assertTrue(code.startsWith("NHB${CommandBarCode.CODE_VERSION}-"))
        assertEquals(
            CommandBarCode.DecodeResult.Ok(TouchCommandBar.DEFAULT_CONFIG),
            CommandBarCode.decode(code),
        )
    }

    @Test
    fun roundTripsDisabledRowsAndLimits() {
        val config = "! a f\n@6 z\n! @4 w W"

        assertEquals(
            CommandBarCode.DecodeResult.Ok(config),
            CommandBarCode.decode(CommandBarCode.encode(config)),
        )
        val rows = TouchCommandBar.parseRows(
            (CommandBarCode.decode(CommandBarCode.encode(config))
                    as CommandBarCode.DecodeResult.Ok).config,
        )
        assertFalse(rows[0].enabled)
        assertFalse(rows[2].enabled)
        assertEquals(4, rows[2].visibleLimit)
    }

    @Test
    fun trimsSurroundingWhitespace() {
        val code = CommandBarCode.encode(TouchCommandBar.DEFAULT_CONFIG)

        assertEquals(
            CommandBarCode.DecodeResult.Ok(TouchCommandBar.DEFAULT_CONFIG),
            CommandBarCode.decode("  $code\n"),
        )
    }

    @Test
    fun rejectsMalformedCodes() {
        assertEquals(CommandBarCode.DecodeResult.Invalid, CommandBarCode.decode(""))
        assertEquals(CommandBarCode.DecodeResult.Invalid, CommandBarCode.decode("XXX1-AAAA"))
        assertEquals(CommandBarCode.DecodeResult.Invalid, CommandBarCode.decode("NHB"))
        assertEquals(CommandBarCode.DecodeResult.Invalid, CommandBarCode.decode("NHB1-"))
        assertEquals(CommandBarCode.DecodeResult.Invalid, CommandBarCode.decode("NHBx-AAAA"))
        assertEquals(CommandBarCode.DecodeResult.Invalid, CommandBarCode.decode("NHB0-AAAA"))
        assertEquals(CommandBarCode.DecodeResult.Invalid, CommandBarCode.decode("NHB1-****"))
        // Valid Base64 but decodes to an empty layout.
        assertEquals(
            CommandBarCode.DecodeResult.Invalid,
            CommandBarCode.decode(CommandBarCode.encode("   ")),
        )
    }

    @Test
    fun rejectsCodesFromNewerVersions() {
        val newer = CommandBarCode.encode(TouchCommandBar.DEFAULT_CONFIG)
            .replaceFirst("NHB${CommandBarCode.CODE_VERSION}-", "NHB${CommandBarCode.CODE_VERSION + 1}-")

        assertEquals(
            CommandBarCode.DecodeResult.TooNew(CommandBarCode.CODE_VERSION + 1),
            CommandBarCode.decode(newer),
        )
    }
}
