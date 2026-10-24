package com.yywspace.anethack.keybord

import android.annotation.SuppressLint
import android.graphics.PointF
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlin.reflect.KMutableProperty0


/**
 * 让 [target] 长按后可以拖动换位置，点击照常。松手时把位置按父布局可用范围的比例
 * （"x,y"，0~1）存进 [position]，横竖屏切换或屏幕尺寸变化后仍落在差不多的地方，
 * 也不会被挤出屏幕；[position] 为空时 target 留在布局文件里的默认位置。
 */
@SuppressLint("ClickableViewAccessibility")
class LongPressDragHelper(
    private val target: View,
    private val position: KMutableProperty0<String?>,
) {
    private val safeInsets = Rect()
    private var dragging = false
    private var lastRawX = 0f
    private var lastRawY = 0f
    // 按住的点相对 target 左上角的偏移，碰到边缘再拖回来时手指仍对着原来那一点
    private var grabX = 0f
    private var grabY = 0f
    // 拖动时随手指更新，松手才写进设置；拖动途中重新布局也按它摆放，不会跳回去
    private var fraction = parse(position.get())

    init {
        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyPosition() }
        // 长按已处理，抬起时 View 不会再算作点击
        target.setOnLongClickListener {
            dragging = true
            grabX = lastRawX - target.x
            grabY = lastRawY - target.y
            target.alpha = DRAG_ALPHA
            true
        }
        // 只在拖动中吃掉移动事件；其余事件放行，点击仍由 View 自己的 onTouchEvent 处理
        target.setOnTouchListener { _, ev ->
            lastRawX = ev.rawX
            lastRawY = ev.rawY
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    moveTo(ev.rawX - grabX, ev.rawY - grabY)
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragging = false
                    target.alpha = 1f
                    position.set(fraction?.let { "${it.x},${it.y}" })
                    // 抬起事件照样放行，让 View 清掉按下状态
                }
            }
            false
        }
    }

    /** 拖动范围离父布局四边的距离（像素），用来避开侧边导航栏和顶部状态栏。 */
    fun setSafeInsets(left: Int, top: Int, right: Int, bottom: Int) {
        if (safeInsets.left == left && safeInsets.top == top &&
            safeInsets.right == right && safeInsets.bottom == bottom)
            return
        safeInsets.set(left, top, right, bottom)
        applyPosition()
    }

    private fun applyPosition() {
        val f = fraction
        if (f == null) {
            target.translationX = 0f
            target.translationY = 0f
            return
        }
        val x = rangeX() ?: return
        val y = rangeY() ?: return
        target.x = x.start + f.x * (x.endInclusive - x.start)
        target.y = y.start + f.y * (y.endInclusive - y.start)
    }

    private fun moveTo(x: Float, y: Float) {
        val rx = rangeX() ?: return
        val ry = rangeY() ?: return
        target.x = x.coerceIn(rx)
        target.y = y.coerceIn(ry)
        fraction = PointF(ratio(target.x, rx), ratio(target.y, ry))
    }

    private fun rangeX(): ClosedFloatingPointRange<Float>? {
        val parent = target.parent as? View ?: return null
        return range(safeInsets.left, parent.width - safeInsets.right - target.width)
    }

    private fun rangeY(): ClosedFloatingPointRange<Float>? {
        val parent = target.parent as? View ?: return null
        return range(safeInsets.top, parent.height - safeInsets.bottom - target.height)
    }

    /** 放不下时先让出安全边距，再不行就贴着起始边。 */
    private fun range(start: Int, end: Int): ClosedFloatingPointRange<Float> {
        val min = start.coerceAtMost(end).coerceAtLeast(0)
        return min.toFloat()..maxOf(min, end).toFloat()
    }

    private fun ratio(value: Float, range: ClosedFloatingPointRange<Float>): Float {
        val size = range.endInclusive - range.start
        return if (size > 0f) (value - range.start) / size else 0f
    }

    companion object {
        private const val DRAG_ALPHA = 0.7f

        private fun parse(value: String?): PointF? {
            val parts = value?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: return null
            if (parts.size != 2) return null
            return PointF(parts[0].coerceIn(0f, 1f), parts[1].coerceIn(0f, 1f))
        }
    }
}
