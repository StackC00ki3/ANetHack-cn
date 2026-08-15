package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import com.yywspace.anethack.R


class BottomActionDock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onCommandPress: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_dock_height)
        setBackgroundColor(ContextCompat.getColor(context, R.color.touch_surface))
        addAction("27", R.drawable.ic_touch_cancel, R.string.touch_action_cancel)
        addAction("i", R.drawable.ic_touch_inventory, R.string.touch_action_inventory)
        addAction("Keyboard", R.drawable.ic_touch_keyboard, R.string.touch_action_keyboard)
        addAction("Center", R.drawable.ic_touch_center, R.string.touch_action_center)
        addAction("Setting", R.drawable.ic_touch_settings, R.string.touch_action_settings)
    }

    private fun addAction(
        command: String,
        @DrawableRes iconRes: Int,
        @StringRes descriptionRes: Int,
    ) {
        val button = ImageButton(context).apply {
            id = View.generateViewId()
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(context, R.color.touch_icon)
            background = context.getDrawable(R.drawable.touch_button_background)
            contentDescription = context.getString(descriptionRes)
            TooltipCompat.setTooltipText(this, contentDescription)
            minimumWidth = resources.getDimensionPixelSize(R.dimen.touch_target_min_size)
            minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min_size)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.touch_dock_icon_padding),
                resources.getDimensionPixelSize(R.dimen.touch_dock_icon_padding),
                resources.getDimensionPixelSize(R.dimen.touch_dock_icon_padding),
                resources.getDimensionPixelSize(R.dimen.touch_dock_icon_padding),
            )
            setOnClickListener { onCommandPress?.invoke(command) }
        }
        addView(
            button,
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.touch_dock_button_margin)
                marginEnd = resources.getDimensionPixelSize(R.dimen.touch_dock_button_margin)
            },
        )
    }
}
