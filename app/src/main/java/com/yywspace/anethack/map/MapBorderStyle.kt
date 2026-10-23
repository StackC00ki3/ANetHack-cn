package com.yywspace.anethack.map

import android.content.Context
import android.graphics.Color
import com.yywspace.anethack.R

/**
 * 地图边缘框线的外观。设置里保存三项：调色板中的颜色（-1 表示跟随主题）、
 * 以 0.1dp 为单位的粗细、以及 0-100 的不透明度。
 */
object MapBorderStyle {
    // 键名与 SharedPreferencesUtils 的属性名保持一致。
    const val KEY_COLOR = "mapBorderColor"
    const val KEY_WIDTH = "mapBorderWidth"
    const val KEY_OPACITY = "mapBorderOpacity"

    const val THEME_COLOR = -1

    const val DEFAULT_COLOR = THEME_COLOR
    const val DEFAULT_WIDTH_TENTHS = 10
    const val MIN_WIDTH_TENTHS = 5
    const val MAX_WIDTH_TENTHS = 60
    const val DEFAULT_OPACITY = 100
    const val MIN_OPACITY = 10
    const val MAX_OPACITY = 100

    /** 可选颜色；[THEME_COLOR] 使用随昼夜主题变化的默认色。 */
    val PALETTE = intArrayOf(
        THEME_COLOR,
        0xFFFFFFFF.toInt(),
        0xFF000000.toInt(),
        0xFFFFD166.toInt(),
        0xFF7ED957.toInt(),
        0xFF6D9EFF.toInt(),
        0xFFFF6B6B.toInt(),
    )

    // “跟随主题”色块的左右两半，分别示意夜间与日间主题下的默认框线色。
    const val THEME_SWATCH_DARK = 0xFFC9CEDB.toInt()
    const val THEME_SWATCH_LIGHT = 0xFF3F3A33.toInt()

    fun baseColor(context: Context, colorPref: Int): Int =
        if (colorPref == THEME_COLOR) context.getColor(R.color.map_border) else colorPref

    fun paintColor(context: Context, colorPref: Int, opacity: Int): Int {
        val color = baseColor(context, colorPref)
        val alpha = opacity.coerceIn(0, 100) * 255 / 100
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun widthDp(widthTenths: Int): Float = widthTenths / 10f
}
