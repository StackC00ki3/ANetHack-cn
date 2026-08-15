package com.yywspace.anethack.keybord

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.yywspace.anethack.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin


class RadialActionWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val preferredRadius: Float
        get() = resources.getDimension(R.dimen.touch_wheel_radius)
    private val itemWidth: Int
        get() = resources.getDimensionPixelSize(R.dimen.touch_wheel_item_width)
    private val itemHeight: Int
        get() = resources.getDimensionPixelSize(R.dimen.touch_wheel_item_height)
    private val edgeMargin: Float
        get() = resources.getDimension(R.dimen.touch_wheel_edge_margin)
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.touch_wheel_connector)
        strokeWidth = resources.getDimension(R.dimen.touch_wheel_connector_width)
    }
    private var anchor = PointF()
    private var wheelCenter = PointF()
    private var effectiveRadius = preferredRadius
    private var categories: List<TouchActionCategory> = emptyList()
    private var categoryIndex: Int? = null
    private var pageIndex = 0
    private var onAction: ((String) -> Unit)? = null
    private var safeLeft = 0f
    private var safeTop = 0f
    private var safeRight = 0f
    private var safeBottom = 0f
    private var panelAlpha = TouchWheelOpacity.toAlpha(TouchWheelOpacity.DEFAULT_PERCENT)

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

    fun setSafeInsets(left: Int, top: Int, right: Int, bottom: Int) {
        safeLeft = left.toFloat()
        safeTop = top.toFloat()
        safeRight = right.toFloat()
        safeBottom = bottom.toFloat()
    }

    fun setPanelOpacity(percent: Int) {
        val nextAlpha = TouchWheelOpacity.toAlpha(percent)
        if (panelAlpha == nextAlpha) return
        panelAlpha = nextAlpha
        if (isShowing) rebuild()
    }

    fun show(
        anchorInView: PointF,
        actionCategories: List<TouchActionCategory>,
        onAction: (String) -> Unit,
    ) {
        if (actionCategories.isEmpty()) return
        anchor.set(anchorInView.x, anchorInView.y)
        categories = actionCategories
        categoryIndex = null
        pageIndex = 0
        this.onAction = onAction
        visibility = VISIBLE
        bringToFront()
        requestFocus()
        post { rebuild() }
    }

    fun navigateBackOrDismiss(): Boolean {
        if (!isShowing) return false
        return if (categoryIndex != null) {
            showCategoryHub()
            true
        } else {
            dismiss()
        }
    }

    fun dismiss(): Boolean {
        if (!isShowing) return false
        removeAllViews()
        categories = emptyList()
        categoryIndex = null
        pageIndex = 0
        onAction = null
        visibility = GONE
        return true
    }

    private fun rebuild() {
        removeAllViews()
        if (width == 0 || height == 0) {
            post { rebuild() }
            return
        }
        updateWheelCenter()
        val selectedCategory = categoryIndex?.let { categories.getOrNull(it) }
        if (selectedCategory == null) {
            rebuildCategoryHub()
        } else {
            rebuildCategoryPage(selectedCategory)
        }
        invalidate()
    }

    private fun updateWheelCenter() {
        val availableWidth = max(0f, width - safeLeft - safeRight)
        val availableHeight = max(0f, height - safeTop - safeBottom)
        val maxRadiusX = max(0f, (availableWidth - itemWidth - edgeMargin * 2f) / 2f)
        val maxRadiusY = max(0f, (availableHeight - itemHeight - edgeMargin * 2f) / 2f)
        effectiveRadius = min(preferredRadius, min(maxRadiusX, maxRadiusY))
        val extentX = effectiveRadius + itemWidth / 2f + edgeMargin
        val extentY = effectiveRadius + itemHeight / 2f + edgeMargin
        val center = TouchWheelGeometry.clampCenter(
            anchor = WheelPoint(anchor.x, anchor.y),
            width = width.toFloat(),
            height = height.toFloat(),
            extentX = extentX,
            extentY = extentY,
            safeLeft = safeLeft,
            safeTop = safeTop,
            safeRight = safeRight,
            safeBottom = safeBottom,
        )
        wheelCenter.set(center.x, center.y)
    }

    private fun rebuildCategoryHub() {
        categories.forEachIndexed { index, category ->
            addView(
                createWheelItem(
                    iconRes = category.iconRes,
                    label = category.label(context),
                    keyHint = resources.getQuantityString(
                        R.plurals.touch_category_action_count,
                        category.actionCount,
                        category.actionCount,
                    ),
                    contentDescription = context.getString(
                        R.string.touch_category_description,
                        category.label(context),
                        category.actionCount,
                    ),
                ) {
                    categoryIndex = index
                    pageIndex = 0
                    rebuild()
                },
                radialLayoutParams(index, categories.size),
            )
        }
        addView(createHubCloseButton(), centerLayoutParams())
    }

    private fun rebuildCategoryPage(category: TouchActionCategory) {
        val safePageIndex = pageIndex.coerceIn(0, category.pages.lastIndex)
        if (safePageIndex != pageIndex) pageIndex = safePageIndex
        val actions = category.pages[pageIndex]
        val itemCount = actions.size + 1
        actions.forEachIndexed { index, action ->
            addView(createActionView(action), radialLayoutParams(index, itemCount))
        }
        addView(createBackToCategoriesView(), radialLayoutParams(actions.size, itemCount))
        addView(createCategoryPageButton(category), centerLayoutParams())
    }

    private fun createActionView(action: TouchAction): View {
        val label = action.label(context)
        return createWheelItem(
            iconRes = action.iconRes,
            label = label,
            keyHint = action.keyHint,
            contentDescription = context.getString(
                R.string.touch_action_description,
                label,
                action.keyHint,
            ),
        ) {
            val listener = onAction
            dismiss()
            listener?.invoke(action.command)
        }
    }

    private fun createBackToCategoriesView(): View = createWheelItem(
        iconRes = R.drawable.ic_touch_cancel,
        label = context.getString(R.string.touch_action_categories),
        keyHint = context.getString(R.string.touch_action_back_hint),
        contentDescription = context.getString(R.string.touch_action_categories_description),
        onClick = ::showCategoryHub,
    )

    private fun createWheelItem(
        @DrawableRes iconRes: Int,
        label: String,
        keyHint: String,
        contentDescription: String,
        onClick: () -> Unit,
    ): View {
        val view = inflate(context, R.layout.touch_wheel_action, null)
        applyPanelBackground(view, R.drawable.touch_wheel_item_background)
        view.findViewById<ImageView>(R.id.touch_action_icon).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(context, R.color.touch_icon)
        }
        view.findViewById<TextView>(R.id.touch_action_label).text = label
        view.findViewById<TextView>(R.id.touch_action_key).text = keyHint
        view.contentDescription = contentDescription
        TooltipCompat.setTooltipText(view, contentDescription)
        view.setOnClickListener { onClick() }
        return view
    }

    private fun createHubCloseButton(): View = createCenterButton(
        text = context.getString(R.string.touch_wheel_close),
        contentDescription = context.getString(R.string.touch_wheel_close_description),
        clickable = true,
        onClick = { dismiss() },
    )

    private fun createCategoryPageButton(category: TouchActionCategory): View {
        val pageCount = category.pages.size
        val categoryLabel = category.label(context)
        val text = if (pageCount > 1) {
            context.getString(R.string.touch_action_page_status, categoryLabel, pageIndex + 1, pageCount)
        } else {
            categoryLabel
        }
        val description = if (pageCount > 1) {
            context.getString(R.string.touch_action_next_page, pageIndex + 1, pageCount)
        } else {
            context.getString(R.string.touch_action_single_page, categoryLabel)
        }
        return createCenterButton(
            text = text,
            contentDescription = description,
            clickable = pageCount > 1,
        ) {
            pageIndex = (pageIndex + 1) % pageCount
            rebuild()
        }
    }

    private fun createCenterButton(
        text: String,
        contentDescription: String,
        clickable: Boolean,
        onClick: () -> Unit = {},
    ): View = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 2
        setTextColor(ContextCompat.getColor(context, R.color.touch_text))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.touch_wheel_center_text))
        this.text = text
        this.contentDescription = contentDescription
        applyPanelBackground(this, R.drawable.touch_wheel_page_background)
        isClickable = clickable
        isFocusable = clickable
        if (clickable) {
            TooltipCompat.setTooltipText(this, contentDescription)
            setOnClickListener { onClick() }
        }
        val padding = resources.getDimensionPixelSize(R.dimen.touch_wheel_page_padding)
        setPadding(padding, padding, padding, padding)
    }

    private fun showCategoryHub() {
        categoryIndex = null
        pageIndex = 0
        rebuild()
    }

    private fun applyPanelBackground(view: View, @DrawableRes backgroundRes: Int) {
        view.background = ContextCompat.getDrawable(context, backgroundRes)?.mutate()?.apply {
            alpha = panelAlpha
        }
    }

    private fun radialLayoutParams(index: Int, count: Int): LayoutParams {
        val angle = -PI / 2.0 + 2.0 * PI * index / count
        val centerX = wheelCenter.x + effectiveRadius * cos(angle).toFloat()
        val centerY = wheelCenter.y + effectiveRadius * sin(angle).toFloat()
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
            canvas.drawCircle(
                anchor.x,
                anchor.y,
                resources.getDimension(R.dimen.touch_wheel_anchor_radius),
                connectorPaint,
            )
        }
    }
}
