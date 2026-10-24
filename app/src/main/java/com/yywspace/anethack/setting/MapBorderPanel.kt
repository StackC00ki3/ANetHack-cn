package com.yywspace.anethack.setting

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.yywspace.anethack.R
import com.yywspace.anethack.map.MapBorderStyle

/**
 * “地图边框”设置项弹出的小面板：粗细、不透明度用滑块，颜色用色块选择，
 * 顶部有实时预览。每次改动立即写入 SharedPreferences，游戏画面下一帧即生效。
 */
class MapBorderPanel(
    private val context: Context,
    private val anchor: View?,
    /** 锚点行尚未布局时用来取窗口令牌，让面板仍能在屏幕中部弹出。 */
    private val host: View? = null,
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val density = context.resources.displayMetrics.density
    private val content = LayoutInflater.from(context).inflate(R.layout.popup_map_border, null)
    private val popup = PopupWindow(
        content,
        (context.resources.displayMetrics.widthPixels * 0.78f).toInt(),
        LinearLayout.LayoutParams.WRAP_CONTENT,
        true
    ).apply {
        isOutsideTouchable = true
        elevation = 8 * density
        // 默认即允许在屏幕内翻折；列表底部弹出时会自动出现在锚点上方。
    }

    private val preview = content.findViewById<MapBorderPreviewView>(R.id.map_border_preview)
    private val widthSeek = content.findViewById<SeekBar>(R.id.map_border_width_seek)
    private val widthValue = content.findViewById<TextView>(R.id.map_border_width_value)
    private val opacitySeek = content.findViewById<SeekBar>(R.id.map_border_opacity_seek)
    private val opacityValue = content.findViewById<TextView>(R.id.map_border_opacity_value)
    private val swatches = content.findViewById<LinearLayout>(R.id.map_border_swatches)

    private var colorPref = preferences.getInt(KEY_COLOR, MapBorderStyle.DEFAULT_COLOR)
    private var widthTenths = preferences.getInt(KEY_WIDTH, MapBorderStyle.DEFAULT_WIDTH_TENTHS)
    private var opacity = preferences.getInt(KEY_OPACITY, MapBorderStyle.DEFAULT_OPACITY)

    init {
        repeat(MapBorderStyle.PALETTE.size) { index ->
            swatches.addView(
                makeSwatch(index),
                LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(8) }
            )
        }

        widthSeek.max = MapBorderStyle.MAX_WIDTH_TENTHS - MapBorderStyle.MIN_WIDTH_TENTHS
        widthSeek.setOnSeekBarChangeListener(seekListener { progress ->
            widthTenths = progress + MapBorderStyle.MIN_WIDTH_TENTHS
            preferences.edit { putInt(KEY_WIDTH, widthTenths) }
            refresh()
        })
        opacitySeek.max = MapBorderStyle.MAX_OPACITY - MapBorderStyle.MIN_OPACITY
        opacitySeek.setOnSeekBarChangeListener(seekListener { progress ->
            opacity = progress + MapBorderStyle.MIN_OPACITY
            preferences.edit { putInt(KEY_OPACITY, opacity) }
            refresh()
        })
        content.findViewById<View>(R.id.map_border_reset).setOnClickListener {
            colorPref = MapBorderStyle.DEFAULT_COLOR
            widthTenths = MapBorderStyle.DEFAULT_WIDTH_TENTHS
            opacity = MapBorderStyle.DEFAULT_OPACITY
            preferences.edit {
                putInt(KEY_COLOR, colorPref)
                putInt(KEY_WIDTH, widthTenths)
                putInt(KEY_OPACITY, opacity)
            }
            refresh()
        }
        refresh()
    }

    fun show() {
        if (anchor != null) {
            popup.showAsDropDown(anchor, (anchor.width - popup.width) / 2, -dp(8), Gravity.NO_GRAVITY)
        } else {
            popup.showAtLocation(host ?: content, Gravity.CENTER, 0, 0)
        }
    }

    private fun makeSwatch(index: Int): View {
        val color = MapBorderStyle.PALETTE[index]
        return TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 10f
            background = if (color == MapBorderStyle.THEME_COLOR) {
                // 左深右浅，示意该色随昼夜主题变化。
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(MapBorderStyle.THEME_SWATCH_DARK, MapBorderStyle.THEME_SWATCH_LIGHT)
                ).apply { shape = GradientDrawable.OVAL }
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            }
            contentDescription = if (color == MapBorderStyle.THEME_COLOR)
                context.getString(R.string.map_border_color_theme) else null
            setOnClickListener {
                colorPref = color
                preferences.edit { putInt(KEY_COLOR, colorPref) }
                refresh()
            }
        }
    }

    private fun refresh() {
        widthSeek.progress = widthTenths - MapBorderStyle.MIN_WIDTH_TENTHS
        opacitySeek.progress = opacity - MapBorderStyle.MIN_OPACITY
        widthValue.text = String.format("%.1f dp", MapBorderStyle.widthDp(widthTenths))
        opacityValue.text = String.format("%d%%", opacity)
        preview.colorPref = colorPref
        preview.widthTenths = widthTenths
        preview.opacity = opacity
        val accent = context.getColor(R.color.touch_bar_edit_accent)
        val stroke = context.getColor(R.color.key_stroke)
        for (i in 0 until swatches.childCount) {
            val selected = MapBorderStyle.PALETTE[i] == colorPref
            (swatches.getChildAt(i).background as GradientDrawable)
                .setStroke(dp(if (selected) 2.5f else 1f), if (selected) accent else stroke)
        }
    }

    private fun seekListener(onProgress: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

    private fun dp(value: Int): Int = (value * density).toInt()
    private fun dp(value: Float): Int = (value * density).toInt()

    companion object {
        private const val KEY_COLOR = MapBorderStyle.KEY_COLOR
        private const val KEY_WIDTH = MapBorderStyle.KEY_WIDTH
        private const val KEY_OPACITY = MapBorderStyle.KEY_OPACITY
    }
}
