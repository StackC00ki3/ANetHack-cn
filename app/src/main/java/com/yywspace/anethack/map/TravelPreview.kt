package com.yywspace.anethack.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import com.yywspace.anethack.R
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 点按旅行的候选目的地。
 *
 * 第一次点按只在目的格画一个圆角方框，再画出英雄会走的路线；趁它还没淡完再点
 * 同一格才真正旅行。标记约 3 秒淡完，淡完就取消候选。
 *
 * 路线由游戏线程算好后送回。点按、绘制、送回路线分在三个线程，
 * 所以候选状态每次整体替换，放在 [AtomicReference] 里。
 */
class TravelPreview(private val context: Context) {

    private class Candidate(
        val target: Point,
        val startMs: Long,
        /** 路线是否已送回；送回了但 path 为 null 表示路线不明，画直线 */
        val resolved: Boolean = false,
        val path: IntArray? = null,
    )

    private val candidate = AtomicReference<Candidate?>(null)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val route = Path()
    private val markRect = RectF()
    private var dashSize = 0f
    private var dash: DashPathEffect? = null

    /**
     * 标出新的候选目的地，替换掉旧的。返回的函数用来送回路线，
     * 候选已被替换或取消时送回的结果会被丢弃。
     */
    fun show(target: Point): (IntArray?) -> Unit {
        val shown = Candidate(Point(target), SystemClock.uptimeMillis())
        candidate.set(shown)
        return { path ->
            candidate.compareAndSet(shown, Candidate(shown.target, shown.startMs, true, path))
        }
    }

    /** <x,y> 正是还没淡完的候选目的地 */
    fun isCandidate(x: Int, y: Int): Boolean {
        val shown = candidate.get() ?: return false
        return shown.target.x == x && shown.target.y == y
            && SystemClock.uptimeMillis() - shown.startMs < DURATION_MS
    }

    fun clear() {
        candidate.set(null)
    }

    fun draw(canvas: Canvas, hero: Point, tileBorder: (Int, Int) -> RectF) {
        val shown = candidate.get() ?: return
        val elapsed = SystemClock.uptimeMillis() - shown.startMs
        if (elapsed >= DURATION_MS) {
            candidate.compareAndSet(shown, null)
            return
        }
        val t = elapsed / DURATION_MS.toFloat()
        // 开头基本不淡，越往后淡得越快
        val fade = 1f - t * t
        val targetBorder = tileBorder(shown.target.x, shown.target.y)
        val tile = min(targetBorder.width(), targetBorder.height())
        val color = context.getColor(R.color.map_travel_preview)
        val halo = context.getColor(R.color.map_travel_preview_halo)

        val points = routePoints(shown, elapsed, hero, tileBorder)
        if (points != null)
            drawRoute(canvas, points, tile, color, halo, fade, exact = shown.path != null)
        drawMark(canvas, targetBorder, tile, elapsed, color, halo, fade)
    }

    /** 路线经过的格子中心；还在等游戏线程算路线时先不画 */
    private fun routePoints(
        shown: Candidate, elapsed: Long, hero: Point, tileBorder: (Int, Int) -> RectF,
    ): List<PointF>? {
        val path = shown.path
        if (path != null && path.size >= 4) {
            return (path.indices step 2).map { i ->
                tileBorder(path[i], path[i + 1]).let { PointF(it.centerX(), it.centerY()) }
            }
        }
        // 游戏正忙时查询要排队，等太久就先按直线画
        if (!shown.resolved && elapsed < PATH_WAIT_MS)
            return null
        val from = tileBorder(hero.x, hero.y)
        val to = tileBorder(shown.target.x, shown.target.y)
        return listOf(PointF(from.centerX(), from.centerY()), PointF(to.centerX(), to.centerY()))
    }

    private fun drawRoute(
        canvas: Canvas, points: List<PointF>, tile: Float,
        color: Int, halo: Int, fade: Float, exact: Boolean,
    ) {
        // 线从英雄格边上出发、停在目标框外，不压住英雄和标记
        val trimmed = trim(points, tile * 0.42f, tile * 0.38f) ?: return
        route.rewind()
        route.moveTo(trimmed[0].x, trimmed[0].y)
        for (i in 1 until trimmed.size)
            route.lineTo(trimmed[i].x, trimmed[i].y)

        // 路线不明时用虚线，提醒这只是大致方向
        linePaint.pathEffect = if (exact) null else dashFor(tile)
        val width = max(2f, tile * 0.09f)
        linePaint.strokeWidth = width + max(2f, tile * 0.06f)
        linePaint.color = withAlpha(halo, fade)
        canvas.drawPath(route, linePaint)
        linePaint.strokeWidth = width
        linePaint.color = withAlpha(color, fade * 0.85f)
        canvas.drawPath(route, linePaint)

        if (!exact) return
        // 每一步落脚的格子打一个点，能看出要走几步
        val radius = max(2f, tile * 0.1f)
        for (i in 1 until points.size - 1) {
            dotPaint.color = withAlpha(halo, fade)
            canvas.drawCircle(points[i].x, points[i].y, radius + max(1f, tile * 0.04f), dotPaint)
            dotPaint.color = withAlpha(color, fade)
            canvas.drawCircle(points[i].x, points[i].y, radius, dotPaint)
        }
    }

    private fun drawMark(
        canvas: Canvas, border: RectF, tile: Float, elapsed: Long,
        color: Int, halo: Int, fade: Float,
    ) {
        // 出现时从稍大一圈缩回格子里
        val pop = (elapsed / POP_MS.toFloat()).coerceAtMost(1f)
        val grow = tile * 0.18f * (1f - pop) * (1f - pop)
        val inset = tile * 0.1f - grow
        markRect.set(border.left + inset, border.top + inset, border.right - inset, border.bottom - inset)
        val corner = tile * 0.22f
        val stroke = max(2f, tile * 0.1f)

        markPaint.style = Paint.Style.FILL
        markPaint.color = withAlpha(color, fade * 0.22f)
        canvas.drawRoundRect(markRect, corner, corner, markPaint)
        markPaint.style = Paint.Style.STROKE
        markPaint.strokeWidth = stroke + max(2f, tile * 0.06f)
        markPaint.color = withAlpha(halo, fade)
        canvas.drawRoundRect(markRect, corner, corner, markPaint)
        markPaint.strokeWidth = stroke
        markPaint.color = withAlpha(color, fade)
        canvas.drawRoundRect(markRect, corner, corner, markPaint)
    }

    private fun dashFor(tile: Float): DashPathEffect =
        dash?.takeIf { dashSize == tile }
            ?: DashPathEffect(floatArrayOf(tile * 0.3f, tile * 0.25f), 0f).also {
                dash = it
                dashSize = tile
            }

    private fun withAlpha(color: Int, fade: Float): Int =
        (color and 0x00FFFFFF) or ((Color.alpha(color) * fade).toInt().coerceIn(0, 255) shl 24)

    /** 折线两头各剪掉一段；剪完不剩时返回 null */
    private fun trim(points: List<PointF>, head: Float, tail: Float): List<PointF>? {
        val lengths = points.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
        val total = lengths.sum()
        if (total <= head + tail) return null
        val result = mutableListOf(pointAt(points, lengths, head))
        var walked = 0f
        for (i in lengths.indices) {
            walked += lengths[i]
            if (walked > head && walked < total - tail)
                result.add(points[i + 1])
        }
        result.add(pointAt(points, lengths, total - tail))
        return result
    }

    /** 沿折线走 distance 后所在的点 */
    private fun pointAt(points: List<PointF>, lengths: List<Float>, distance: Float): PointF {
        var left = distance
        for (i in lengths.indices) {
            if (left <= lengths[i] || i == lengths.lastIndex) {
                val f = if (lengths[i] > 0f) (left / lengths[i]).coerceIn(0f, 1f) else 0f
                val a = points[i]
                val b = points[i + 1]
                return PointF(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
            }
            left -= lengths[i]
        }
        return points.first()
    }

    companion object {
        const val DURATION_MS = 3000L
        private const val PATH_WAIT_MS = 300L
        private const val POP_MS = 180L
    }
}
