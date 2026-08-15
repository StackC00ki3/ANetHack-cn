package com.yywspace.anethack.keybord

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.yywspace.anethack.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


class RadialActionWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val radius = resources.getDimension(R.dimen.touch_wheel_radius)
    private val itemWidth = resources.getDimensionPixelSize(R.dimen.touch_wheel_item_width)
    private val itemHeight = resources.getDimensionPixelSize(R.dimen.touch_wheel_item_height)
    private val edgeMargin = resources.getDimension(R.dimen.touch_wheel_edge_margin)
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.touch_wheel_connector)
        strokeWidth = resources.getDimension(R.dimen.touch_wheel_connector_width)
    }
    private var anchor = PointF()
    private var wheelCenter = PointF()
    private var pages: List<List<TouchAction>> = emptyList()
    private var pageIndex = 0
    private var onAction: ((String) -> Unit)? = null
    private var safeTop = 0f
    private var safeBottom = 0f

    val isShowing: Boolean
        get() = isVisible

    init {
        setWillNotDraw(false)
        setBackgroundColor(ContextCompat.getColor(context, R.color.touch_wheel_scrim))
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        visibility = GONE
        setOnClickListener { dismiss() }
    }

    fun setSafeInsets(top: Int, bottom: Int) {
        safeTop = top.toFloat()
        safeBottom = bottom.toFloat()
    }

    fun show(
        anchorInView: PointF,
        actionPages: List<List<TouchAction>>,
        onAction: (String) -> Unit,
    ) {
        if (actionPages.isEmpty()) return
        anchor.set(anchorInView.x, anchorInView.y)
        pages = actionPages
        pageIndex = 0
        this.onAction = onAction
        visibility = VISIBLE
        bringToFront()
        requestFocus()
        post { rebuildPage() }
    }

    fun dismiss(): Boolean {
        if (!isShowing) return false
        removeAllViews()
        pages = emptyList()
        onAction = null
        visibility = GONE
        return true
    }

    private fun rebuildPage() {
        removeAllViews()
        if (width == 0 || height == 0) {
            post { rebuildPage() }
            return
        }
        val extentX = radius + itemWidth / 2f + edgeMargin
        val extentY = radius + itemHeight / 2f + edgeMargin
        val center = TouchWheelGeometry.clampCenter(
            WheelPoint(anchor.x, anchor.y),
            width.toFloat(),
            height.toFloat(),
            extentX,
            extentY,
            safeTop,
            safeBottom,
        )
        wheelCenter.set(center.x, center.y)
        pages[pageIndex].forEachIndexed { index, action ->
            addView(createActionView(action), actionLayoutParams(index, pages[pageIndex].size))
        }
        if (pages.size > 1) addView(createPageButton(), centerLayoutParams())
        invalidate()
    }

    private fun createActionView(action: TouchAction): View {
        val view = inflate(context, R.layout.touch_wheel_action, null)
        view.findViewById<ImageView>(R.id.touch_action_icon).apply {
            setImageResource(action.iconRes)
            imageTintList = ContextCompat.getColorStateList(context, R.color.touch_icon)
        }
        view.findViewById<TextView>(R.id.touch_action_label).text = action.label
        view.findViewById<TextView>(R.id.touch_action_key).text = action.keyHint
        view.contentDescription = context.getString(
            R.string.touch_action_description,
            action.label,
            action.keyHint,
        )
        view.setOnClickListener {
            val listener = onAction
            dismiss()
            listener?.invoke(action.command)
        }
        return view
    }

    private fun createPageButton(): View {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_touch_next)
            imageTintList = ContextCompat.getColorStateList(context, R.color.touch_icon)
            background = context.getDrawable(R.drawable.touch_wheel_page_background)
            contentDescription = context.getString(
                R.string.touch_action_next_page,
                pageIndex + 1,
                pages.size,
            )
            setPadding(
                resources.getDimensionPixelSize(R.dimen.touch_wheel_page_padding),
                resources.getDimensionPixelSize(R.dimen.touch_wheel_page_padding),
                resources.getDimensionPixelSize(R.dimen.touch_wheel_page_padding),
                resources.getDimensionPixelSize(R.dimen.touch_wheel_page_padding),
            )
            setOnClickListener {
                pageIndex = (pageIndex + 1) % pages.size
                rebuildPage()
            }
        }
    }

    private fun actionLayoutParams(index: Int, count: Int): LayoutParams {
        val angle = -PI / 2.0 + 2.0 * PI * index / count
        val centerX = wheelCenter.x + radius * cos(angle).toFloat()
        val centerY = wheelCenter.y + radius * sin(angle).toFloat()
        return LayoutParams(itemWidth, itemHeight).apply {
            leftMargin = (centerX - itemWidth / 2f).roundToInt()
            topMargin = (centerY - itemHeight / 2f).roundToInt()
        }
    }

    private fun centerLayoutParams(): LayoutParams {
        val size = resources.getDimensionPixelSize(R.dimen.touch_wheel_page_size)
        return LayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (wheelCenter.x - size / 2f).roundToInt()
            topMargin = (wheelCenter.y - size / 2f).roundToInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isShowing) {
            canvas.drawLine(anchor.x, anchor.y, wheelCenter.x, wheelCenter.y, connectorPaint)
            canvas.drawCircle(anchor.x, anchor.y, resources.getDimension(R.dimen.touch_wheel_anchor_radius), connectorPaint)
        }
    }
}
