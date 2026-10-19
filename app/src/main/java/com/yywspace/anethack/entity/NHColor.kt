package com.yywspace.anethack.entity

enum class NHColor {
    CLR_BLACK,
    CLR_RED,
    CLR_GREEN,
    CLR_BROWN,
    CLR_BLUE,
    CLR_MAGENTA,
    CLR_CYAN,
    CLR_GRAY,
    NO_COLOR,
    CLR_ORANGE,
    CLR_BRIGHT_GREEN,
    CLR_YELLOW,
    CLR_BRIGHT_BLUE,
    CLR_BRIGHT_MAGENTA,
    CLR_BRIGHT_CYAN,
    CLR_WHITE,
    CLR_MAX;

    fun toColor(lightBackground: Boolean = false):Int {
        val palette = if (lightBackground) lightColorMap else darkColorMap
        return palette[ordinal]
    }
    companion object {
        // NetHack's terminal palette, tuned for the default black map background.
        private val darkColorMap = intArrayOf(
            0xFF262626.toInt(),	// CLR_BLACK
            0xFFFF0000.toInt(),	// CLR_RED
            0xFF008800.toInt(),	// CLR_GREEN
            0xFF664411.toInt(), // CLR_BROWN
            0xFF0000FF.toInt(),	// CLR_BLUE
            0xFFFF00FF.toInt(),	// CLR_MAGENTA
            0xFF00FFFF.toInt(),	// CLR_CYAN
            0xFF888888.toInt(),	// CLR_GRAY
            0xFFFFFFFF.toInt(),	// NO_COLOR
            0xFFFF9900.toInt(),	// CLR_ORANGE
            0xFF00FF00.toInt(),	// CLR_BRIGHT_GREEN
            0xFFFFD700.toInt(),	// CLR_YELLOW
            0xFF0088FF.toInt(),	// CLR_BRIGHT_BLUE
            0xFFFF77FF.toInt(),	// CLR_BRIGHT_MAGENTA
            0xFF77FFFF.toInt(),	// CLR_BRIGHT_CYAN
            0xFFFFFFFF.toInt(),	// CLR_WHITE
            0xFFFFFFFF.toInt()	// CLR_MAX (invalid, fall back to NO_COLOR)
        )

        // Darkened equivalents that stay legible on the light map background.
        // CLR_WHITE/NO_COLOR collapse to the regular foreground color.
        private val lightColorMap = intArrayOf(
            0xFF1B1B1F.toInt(),	// CLR_BLACK
            0xFFC00000.toInt(),	// CLR_RED
            0xFF006E00.toInt(),	// CLR_GREEN
            0xFF6B4A1B.toInt(), // CLR_BROWN
            0xFF0033CC.toInt(),	// CLR_BLUE
            0xFFA000A0.toInt(),	// CLR_MAGENTA
            0xFF00707A.toInt(),	// CLR_CYAN
            0xFF5E5E66.toInt(),	// CLR_GRAY
            0xFF1B1B1F.toInt(),	// NO_COLOR
            0xFFB34700.toInt(),	// CLR_ORANGE
            0xFF007A00.toInt(),	// CLR_BRIGHT_GREEN
            0xFF7A5F00.toInt(),	// CLR_YELLOW
            0xFF0055CC.toInt(),	// CLR_BRIGHT_BLUE
            0xFFB800B8.toInt(),	// CLR_BRIGHT_MAGENTA
            0xFF007A85.toInt(),	// CLR_BRIGHT_CYAN
            0xFF1B1B1F.toInt(),	// CLR_WHITE
            0xFF1B1B1F.toInt()	// CLR_MAX (invalid, fall back to NO_COLOR)
        )

        fun fromInt(i: Int): NHColor {
            NHColor.values().forEach {
                if(it.ordinal == i)
                    return it
            }
            return NO_COLOR
        }
    }
}