package com.yywspace.anethack.keybord

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import com.yywspace.anethack.R


/**
 * 等待方向输入（getdir）时浮出的方向键盘：九宫格是八个方向加中间的"自己"，
 * 下面一行居中是取消（ESC），两侧是向上/向下，直接写成 < >，用于挖掘、向下施法等。
 * 按下任意键立即收起并忽略后续点击，免得连点把多出的方向键当成移动指令。
 */
class DirectionPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onKey: ((Char) -> Unit)? = null

    private class PadKey(
        val key: Char,
        // 没有图标时直接显示按键字符
        @DrawableRes val icon: Int?,
        val rotation: Float,
        @StringRes val label: Int,
    )

    // 与 NHMapSurfaceView.playerMove 一样用 vi 方向键
    private val rows = listOf(
        listOf(
            PadKey('y', R.drawable.ic_direction_arrow, -45f, R.string.direction_pad_up_left),
            PadKey('k', R.drawable.ic_direction_arrow, 0f, R.string.direction_pad_up),
            PadKey('u', R.drawable.ic_direction_arrow, 45f, R.string.direction_pad_up_right),
        ),
        listOf(
            PadKey('h', R.drawable.ic_direction_arrow, -90f, R.string.direction_pad_left),
            PadKey('.', R.drawable.ic_direction_self, 0f, R.string.direction_pad_self),
            PadKey('l', R.drawable.ic_direction_arrow, 90f, R.string.direction_pad_right),
        ),
        listOf(
            PadKey('b', R.drawable.ic_direction_arrow, -135f, R.string.direction_pad_down_left),
            PadKey('j', R.drawable.ic_direction_arrow, 180f, R.string.direction_pad_down),
            PadKey('n', R.drawable.ic_direction_arrow, 135f, R.string.direction_pad_down_right),
        ),
        listOf(
            PadKey('<', null, 0f, R.string.direction_pad_upward),
            PadKey(27.toChar(), R.drawable.ic_direction_back, 0f, R.string.direction_pad_cancel),
            PadKey('>', null, 0f, R.string.direction_pad_downward),
        ),
    )

    init {
        orientation = VERTICAL
        // 按键之间的空隙也接住点击，不漏到下面的地图上
        isClickable = true
        build()
    }

    fun show() {
        visibility = VISIBLE
    }

    fun hide() {
        visibility = GONE
    }

    /** 主题或横竖屏切换后重新取颜色和尺寸。 */
    fun refreshTheme() {
        build()
    }

    private fun build() {
        removeAllViews()
        rows.forEach { row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEach { addView(buildButton(it)) }
            })
        }
    }

    private fun buildButton(padKey: PadKey): FrameLayout {
        val size = resources.getDimensionPixelSize(R.dimen.direction_pad_button_size)
        val iconSize = resources.getDimensionPixelSize(R.dimen.direction_pad_icon_size)
        val margin = resources.getDimensionPixelSize(R.dimen.direction_pad_button_margin)
        return FrameLayout(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.direction_pad_item_background)
            contentDescription = context.getString(padKey.label)
            isClickable = true
            isFocusable = true
            TooltipCompat.setTooltipText(this, contentDescription)
            setOnClickListener {
                if (this@DirectionPadView.visibility != VISIBLE)
                    return@setOnClickListener
                hide()
                onKey?.invoke(padKey.key)
            }
            if (padKey.icon != null) {
                addView(
                    ImageView(context).apply {
                        setImageResource(padKey.icon)
                        rotation = padKey.rotation
                        imageTintList = ContextCompat.getColorStateList(context, R.color.touch_icon)
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER),
                )
            } else {
                addView(
                    TextView(context).apply {
                        text = padKey.key.toString()
                        typeface = Typeface.DEFAULT_BOLD
                        includeFontPadding = false
                        setTextColor(ContextCompat.getColorStateList(context, R.color.touch_icon))
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.direction_pad_text_size))
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
            }
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
        }
    }
}
