package com.yywspace.anethack.keybord

import com.yywspace.anethack.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals(listOf("Save", "Quit", "Elber"), pages[0].map { it.customLabel })
        assertEquals(listOf("#", "d"), pages[1].map { it.command })
    }

    @Test
    fun filtersDockCommandsAndReservesNavigationSlot() {
        val pages = TouchActionParser.parsePages("Setting Keyboard Center a b c d e f")

        assertEquals(2, pages.size)
        assertEquals(listOf("a", "b", "c", "d", "e"), pages[0].map { it.command })
        assertEquals(listOf("f"), pages[1].map { it.command })
    }

    @Test
    fun buildsStableCompleteCategoryCatalog() {
        val categories = TouchActionCatalog.builtInCategories()
        val actions = categories.flatMap { category -> category.pages.flatten() }
        val commands = actions.map { it.command }

        assertEquals(listOf("combat", "items", "explore", "info", "system"), categories.map { it.id })
        assertTrue(commands.containsAll(listOf("a", "f", "t", "z", "Z", "4")))
        assertTrue(commands.containsAll(listOf("#loot", "#dip", "#invoke", "W", "P", "R", "T")))
        assertTrue(commands.containsAll(listOf("o", "c", "<", ">", "_", "#pray", "#untrap")))
        assertTrue(commands.containsAll(listOf("?", "/", "15", "24", "#vanquished", "#history")))
        assertTrue(commands.containsAll(listOf("SS", "#quit", "O", "#", "1", "18")))
        assertEquals(actions.map { it.id }.distinct().size, actions.size)
        assertTrue(categories.all { category -> category.pages.all { it.size <= 5 } })
    }

    @Test
    fun excludesUnsafeAndUnavailableCommands() {
        val commands = TouchActionCatalog.builtInCategories()
            .flatMap { it.pages.flatten() }
            .map { it.command }
            .toSet()

        listOf("!", "26", "#exploremode", "#panic", "#debugfuzzer", "#wizwish")
            .forEach { command -> assertFalse(command in commands) }
    }

    @Test
    fun everyBuiltInActionHasARealIconAndReadableControlHint() {
        val actions = TouchActionCatalog.builtInCategories().flatMap { it.pages.flatten() }

        actions.forEach { action -> assertNotEquals(action.id, R.drawable.ic_touch_fallback, action.iconRes) }
        assertEquals(
            "every built-in action must have its own icon",
            actions.size,
            actions.map { it.iconRes }.distinct().size,
        )
        assertEquals("Ctrl-D", TouchActionKeyHints.forCommand("4"))
        assertEquals("Ctrl-O", TouchActionKeyHints.forCommand("15"))
        assertEquals("Ctrl-P", TouchActionKeyHints.forCommand("16"))
        assertEquals("Ctrl-X", TouchActionKeyHints.forCommand("24"))
        assertEquals("Del", TouchActionKeyHints.forCommand("127"))
    }

    @Test
    fun everyCommandIconIsRegisteredAndSelectable() {
        val actions = TouchActionCatalog.builtInCategories().flatMap { it.pages.flatten() }

        actions.forEach { action ->
            val key = TouchActionIcons.keyForRes(action.iconRes)
            assertNotEquals("no key registered for ${action.command}", null, key)
            assertEquals(action.iconRes, TouchActionIcons.resForKey(key!!))
        }
    }

    @Test
    fun parsesRowsWithLabelsAndIconOverrides() {
        val rows = TouchCommandBar.parseRows("a|ApplyAll|ic_touch_cast z\n\n#loot||ic_touch_gold Setting")

        assertEquals(2, rows.size)
        assertEquals("a", rows[0].actions[0].command)
        assertEquals("ApplyAll", rows[0].actions[0].customLabel)
        assertEquals(R.drawable.ic_touch_cast, rows[0].actions[0].iconRes)
        assertEquals(R.string.touch_action_zap, rows[0].actions[1].labelRes)
        assertEquals(null, rows[1].actions[0].customLabel)
        assertEquals(R.drawable.ic_touch_gold, rows[1].actions[0].iconRes)
        assertEquals("Setting", rows[1].actions[1].command)
        assertEquals(R.string.touch_action_settings, rows[1].actions[1].labelRes)
    }

    @Test
    fun parsesAndClampsPerRowVisibleLimit() {
        val rows = TouchCommandBar.parseRows("@6 a f\n@99 z\n@0 w\ne")

        assertEquals(6, rows[0].visibleLimit)
        assertEquals(2, rows[0].visibleCount)
        assertEquals(TouchCommandBar.MAX_VISIBLE_LIMIT, rows[1].visibleLimit)
        assertEquals(1, rows[2].visibleLimit)
        assertEquals(TouchCommandBar.DEFAULT_VISIBLE_LIMIT, rows[3].visibleLimit)
        // Wheel pages ignore the bar-specific limit token.
        assertEquals(
            listOf("a", "f"),
            TouchActionParser.parsePages("@6 a f").flatten().map { it.command },
        )
    }

    @Test
    fun serializesRowLimitOnlyWhenNotDefault() {
        val rows = TouchCommandBar.parseRows("@6 a f\nz")

        assertEquals("@6 a f\nz", TouchCommandBar.serialize(rows))
        assertEquals(rows, TouchCommandBar.parseRows(TouchCommandBar.serialize(rows)))
    }

    @Test
    fun migratesLegacyConfigByPrependingDockRow() {
        assertEquals(TouchCommandBar.DEFAULT_CONFIG, TouchCommandBar.migrateLegacy(null))
        assertEquals(TouchCommandBar.DEFAULT_CONFIG, TouchCommandBar.migrateLegacy("  "))
        assertEquals(TouchCommandBar.DEFAULT_CONFIG, TouchCommandBar.migrateLegacy("a f t z Z 4"))
        assertEquals(
            "27 i Keyboard Center Setting\ns #pray",
            TouchCommandBar.migrateLegacy("s #pray"),
        )
        val migrated = TouchCommandBar.parseRows(TouchCommandBar.migrateLegacy("a f t z Z 4"))
        assertEquals(
            listOf("27", "i", "Keyboard", "Center", "Setting"),
            migrated[0].actions.map { it.command },
        )
    }

    @Test
    fun barSerializationOmitsDefaultsAndRoundTrips() {
        val rows = TouchCommandBar.parseRows(TouchCommandBar.DEFAULT_CONFIG)
        assertEquals(TouchCommandBar.DEFAULT_CONFIG, TouchCommandBar.serialize(rows))

        val customized = TouchCommandBar.parseRows("a|ApplyAll|ic_touch_cast w")
        val reparsed = TouchCommandBar.parseRows(TouchCommandBar.serialize(customized))
        assertEquals(
            customized.map { row -> row.actions.map { Triple(it.command, it.customLabel, it.iconRes) } },
            reparsed.map { row -> row.actions.map { Triple(it.command, it.customLabel, it.iconRes) } },
        )
    }

    @Test
    fun barModelEnforcesRowAndButtonLimits() {
        var rows = listOf(TouchCommandRow(actions = listOf(TouchCommandBar.newAction("a"))))
        rows = TouchCommandBar.addRow(
            TouchCommandBar.addRow(TouchCommandBar.addRow(TouchCommandBar.addRow(rows))),
        )
        assertEquals(TouchCommandBar.MAX_ROWS, rows.size)
        assertEquals(rows, TouchCommandBar.addRow(rows))

        var full = listOf(
            TouchCommandRow(
                actions = List(TouchCommandBar.MAX_BUTTONS_PER_ROW) { TouchCommandBar.newAction("a") },
            ),
        )
        assertEquals(full, TouchCommandBar.addButton(full, 0, TouchCommandBar.newAction("f")))

        full = TouchCommandBar.removeButton(full, 0, 0)
        assertEquals(TouchCommandBar.MAX_BUTTONS_PER_ROW - 1, full[0].actions.size)
        val emptied = (1..full[0].actions.size).fold(full) { acc, _ ->
            TouchCommandBar.removeButton(acc, 0, 0)
        }
        assertTrue(emptied.isEmpty())

        val twoRows = TouchCommandBar.addRow(
            listOf(TouchCommandRow(actions = listOf(TouchCommandBar.newAction("a")))),
        )
        assertEquals(1, TouchCommandBar.removeRow(twoRows, 1).size)

        assertEquals(5, TouchCommandBar.setRowLimit(twoRows, 0, 5)[0].visibleLimit)
        assertEquals(1, TouchCommandBar.setRowLimit(twoRows, 0, 0)[0].visibleLimit)
        assertEquals(
            TouchCommandBar.MAX_VISIBLE_LIMIT,
            TouchCommandBar.setRowLimit(twoRows, 0, 99)[0].visibleLimit,
        )
    }

    @Test
    fun movesButtonsWithinAndAcrossRows() {
        val rows = TouchCommandBar.parseRows("a f t\nz Z")

        val within = TouchCommandBar.moveButton(rows, 0, 0, 0, 2)
        assertEquals(listOf("f", "a", "t"), within[0].actions.map { it.command })
        assertEquals(2, within.size)

        val across = TouchCommandBar.moveButton(rows, 1, 1, 0, 3)
        assertEquals(listOf("a", "f", "t", "Z"), across[0].actions.map { it.command })
        assertEquals(listOf("z"), across[1].actions.map { it.command })

        // An emptied source row survives a move so it stays editable.
        val emptied = TouchCommandBar.moveButton(rows, 1, 0, 1, 2)
        assertEquals(listOf("Z", "z"), emptied[1].actions.map { it.command })

        val full = listOf(
            TouchCommandRow(
                actions = List(TouchCommandBar.MAX_BUTTONS_PER_ROW) { TouchCommandBar.newAction("a") },
            ),
            TouchCommandRow(actions = listOf(TouchCommandBar.newAction("z"))),
        )
        assertEquals(full, TouchCommandBar.moveButton(full, 1, 0, 0, 0))
        assertEquals(rows, TouchCommandBar.moveButton(rows, 9, 0, 0, 0))
        assertEquals(rows, TouchCommandBar.moveButton(rows, 0, 0, 9, 0))
    }

    @Test
    fun barSanitizesLabelsAndParsesDefaultConfig() {
        assertEquals("ApplyAllx", TouchCommandBar.sanitizeToken("Apply All|x"))
        val rows = TouchCommandBar.parseRows(TouchCommandBar.DEFAULT_CONFIG)
        assertEquals(2, rows.size)
        assertEquals(
            listOf("27", "i", "Keyboard", "Center", "Setting"),
            rows[0].actions.map { it.command },
        )
        assertEquals(listOf("a", "f", "t", "z", "Z", "4"), rows[1].actions.map { it.command })
        assertEquals(
            listOf(
                R.drawable.ic_touch_apply, R.drawable.ic_touch_fire, R.drawable.ic_touch_throw,
                R.drawable.ic_touch_zap, R.drawable.ic_touch_cast, R.drawable.ic_touch_kick,
            ),
            rows[1].actions.map { it.iconRes },
        )
        assertTrue(rows.all { it.visibleLimit == TouchCommandBar.DEFAULT_VISIBLE_LIMIT })
    }

    @Test
    fun suppressesLegacyDefaultButKeepsRealCustomActions() {
        val legacyDefault =
            "Setting SS|Save #quit|Quit S#engrave#-nL\"Elbereth\":|Elber S20s|20s Keyboard|Abc\r\n" +
                "#|Extend d|Drop e|Eat :|Look ,|Pick .|Rest"

        assertTrue(TouchActionCatalog.isLegacyDefault(legacyDefault))
        assertEquals(5, TouchActionCatalog.build(legacyDefault).size)

        val custom = TouchActionCatalog.build("S#engrave#-nL\"Elbereth\":|Elber #loot|Loot")
        assertEquals("custom", custom.last().id)
        assertEquals(listOf("Elber", "Loot"), custom.last().pages.flatten().map { it.customLabel })
    }

    @Test
    fun mapsKnownAndUnknownCustomIcons() {
        assertEquals(R.drawable.ic_touch_save, TouchActionIcons.forCommand("SS"))
        assertEquals(R.drawable.ic_touch_engrave, TouchActionIcons.forCommand("S#engrave#-nL\"Elbereth\":"))
        assertEquals(R.drawable.ic_touch_apply, TouchActionIcons.forCommand("a"))
        assertEquals(R.drawable.ic_touch_fallback, TouchActionIcons.forCommand("not-a-command"))
    }

    @Test
    fun clampsWheelInsideAllSafeBounds() {
        val center = TouchWheelGeometry.clampCenter(
            anchor = WheelPoint(2f, 995f),
            width = 500f,
            height = 1000f,
            extentX = 100f,
            extentY = 100f,
            safeLeft = 20f,
            safeTop = 80f,
            safeRight = 30f,
            safeBottom = 120f,
        )

        assertEquals(120f, center.x)
        assertEquals(780f, center.y)
        assertTrue(center.x - 100f >= 20f)
        assertTrue(center.x + 100f <= 470f)
        assertTrue(center.y - 100f >= 80f)
        assertTrue(center.y + 100f <= 880f)
    }

    @Test
    fun clampsAndConvertsWheelOpacity() {
        assertEquals(35, TouchWheelOpacity.clamp(0))
        assertEquals(100, TouchWheelOpacity.clamp(150))
        assertEquals(89, TouchWheelOpacity.toAlpha(35))
        assertEquals(242, TouchWheelOpacity.toAlpha(95))
        assertEquals(255, TouchWheelOpacity.toAlpha(100))
    }
}
