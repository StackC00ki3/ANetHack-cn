package com.yywspace.anethack.keybord

import androidx.annotation.DrawableRes
import com.yywspace.anethack.R
import kotlin.math.max
import kotlin.math.min


data class TouchAction(
    val command: String,
    val label: String,
    @DrawableRes val iconRes: Int = TouchActionIcons.forCommand(command),
) {
    val keyHint: String
        get() = when {
            command == "SS" -> "S"
            command == "S20s" -> "20s"
            command.startsWith("S#engrave") -> "#engrave"
            command.length <= 6 -> command
            else -> command.take(5) + "…"
        }
}

object TouchActionParser {
    private const val MAX_ACTIONS_PER_PAGE = 6
    private val dockCommands = setOf("Setting", "Keyboard", "Center")

    fun parsePages(commandPanel: String): List<List<TouchAction>> {
        val pages = mutableListOf<List<TouchAction>>()
        commandPanel.lineSequence().forEach { row ->
            val actions = row.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .mapNotNull(::parseAction)
                .filterNot { it.command in dockCommands }
            actions.chunked(MAX_ACTIONS_PER_PAGE).forEach(pages::add)
        }
        return pages
    }

    private fun parseAction(value: String): TouchAction? {
        val parts = value.split("|", limit = 2)
        val command = parts.firstOrNull()?.trim().orEmpty()
        if (command.isEmpty()) return null
        val label = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrEmpty() } ?: command
        return TouchAction(command, label)
    }
}

object TouchActionIcons {
    @DrawableRes
    fun forCommand(command: String): Int = when {
        command == "SS" -> R.drawable.ic_touch_save
        command == "#quit" -> R.drawable.ic_touch_quit
        command.startsWith("S#engrave") -> R.drawable.ic_touch_engrave
        command == "S20s" || command == "." -> R.drawable.ic_touch_wait
        command == "#" -> R.drawable.ic_touch_extended
        command == "d" -> R.drawable.ic_touch_drop
        command == "e" -> R.drawable.ic_touch_eat
        command == ":" -> R.drawable.ic_touch_look
        command == "," -> R.drawable.ic_touch_pickup
        else -> R.drawable.ic_touch_fallback
    }
}

data class WheelPoint(val x: Float, val y: Float)

object TouchWheelGeometry {
    fun clampCenter(
        anchor: WheelPoint,
        width: Float,
        height: Float,
        extentX: Float,
        extentY: Float,
        safeTop: Float,
        safeBottom: Float,
    ): WheelPoint {
        val minX = extentX
        val maxX = max(minX, width - extentX)
        val minY = safeTop + extentY
        val maxY = max(minY, height - safeBottom - extentY)
        return WheelPoint(
            min(max(anchor.x, minX), maxX),
            min(max(anchor.y, minY), maxY),
        )
    }
}
