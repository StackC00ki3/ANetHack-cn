package com.yywspace.anethack.keybord

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import com.yywspace.anethack.R


/**
 * Customizable bottom command bar, including the former system dock as its
 * bottommost row. [TouchCommandBar] rows are ordered bottom-up, so the first
 * config line renders closest to the screen edge. A row holding more buttons
 * than its visible limit pans horizontally to reveal the overflow.
 */
class BottomCommandBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onCommandPress: ((String) -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.touch_surface))
    }

    fun setActions(rows: List<TouchCommandRow>) {
        removeAllViews()
        rows.asReversed()
            .filter { it.actions.isNotEmpty() }
            .forEach { row -> addView(buildRow(row)) }
        visibility = if (childCount == 0) View.GONE else View.VISIBLE
    }

    private fun buildRow(row: TouchCommandRow): View {
        val margin = resources.getDimensionPixelSize(R.dimen.touch_dock_button_margin)
        val rowLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.actions.forEach { action -> rowLayout.addView(buildButton(action)) }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                rowLayout,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.touch_combat_row_height),
            )
            // Equal-width cells sized by the row's visible limit; buttons beyond
            // the limit extend past the edge and are panned into view.
            post {
                val cellWidth = width / maxOf(row.visibleCount, 1)
                for (i in 0 until rowLayout.childCount) {
                    rowLayout.getChildAt(i).layoutParams = LayoutParams(
                        cellWidth - 2 * margin,
                        LayoutParams.MATCH_PARENT,
                    ).apply {
                        marginStart = margin
                        marginEnd = margin
                    }
                }
                rowLayout.requestLayout()
            }
        }
    }

    private fun buildButton(action: TouchAction): View {
        val label = action.label(context)
        val actionView = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = resources.getDimensionPixelSize(R.dimen.touch_target_min_size)
            minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min_size)
            background = ContextCompat.getDrawable(context, R.drawable.touch_button_background)
            contentDescription = context.getString(R.string.touch_bar_action_description, label)
            isClickable = true
            isFocusable = true
            TooltipCompat.setTooltipText(this, contentDescription)
            setOnClickListener { onCommandPress?.invoke(action.command) }
        }
        actionView.addView(
            ImageView(context).apply {
                setImageResource(action.iconRes)
                imageTintList = ContextCompat.getColorStateList(context, R.color.touch_icon)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LayoutParams(
                resources.getDimensionPixelSize(R.dimen.touch_combat_icon_size),
                resources.getDimensionPixelSize(R.dimen.touch_combat_icon_size),
            ),
        )
        actionView.addView(
            TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(ContextCompat.getColor(context, R.color.touch_text))
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.touch_combat_label_text),
                )
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        return actionView
    }
}
