package com.yywspace.anethack.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import com.yywspace.anethack.R
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * 点按旅行的候选目的地。
 *
 * 第一次点按标出目的格和一路上要落脚的格子，趁它还没淡完再点同一格才真正旅行。
 * 标记约 3 秒淡完，淡完就取消候选。
 *
 * 路线由游戏线程算好后送回。点按、绘制、送回路线分在三个线程，
 * 所以候选状态每次整体替换，放在 [AtomicReference] 里。
 */
class TravelPreview(private val context: Context) {

    private class Candidate(
        val target: Point,
        val startMs: Long,
        /** 路线格子，<x0,y0,x1,y1,...>；还没算好或算不出来时为 null */
        val path: IntArray? = null,
    )

    private val candidate = AtomicReference<Candidate?>(null)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markRect = RectF()

    /**
     * 标出新的候选目的地，替换掉旧的。返回的函数用来送回路线，
     * 候选已被替换或取消时送回的结果会被丢弃。
     */
    fun show(target: Point): (IntArray?) -> Unit {
        val shown = Candidate(Point(target), SystemClock.uptimeMillis())
        candidate.set(shown)
        return { path ->
            candidate.compareAndSet(shown, Candidate(shown.target, shown.startMs, path))
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

    fun draw(canvas: Canvas, tileBorder: (Int, Int) -> RectF) {
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

        routePoints(shown, tileBorder)?.let { points ->
            drawDots(canvas, points, tile, color, fade)
        }
        drawMark(canvas, targetBorder, tile, elapsed, color, halo, fade)
    }

    /** 路线经过的格子中心；路线还没算好或没有已知路线时不画 */
    private fun routePoints(
        shown: Candidate, tileBorder: (Int, Int) -> RectF,
    ): List<PointF>? {
        val path = shown.path ?: return null
        if (path.size < 4) return null
        return (path.indices step 2).map { i ->
            tileBorder(path[i], path[i + 1]).let { PointF(it.centerX(), it.centerY()) }
        }
    }

    /** 每一步落脚的格子打一个点，能看出要走几步 */
    private fun drawDots(
        canvas: Canvas, points: List<PointF>, tile: Float, color: Int, fade: Float,
    ) {
        dotPaint.color = withAlpha(color, fade)
        val radius = max(2f, tile * 0.1f)
        for (i in 1 until points.size - 1)
            canvas.drawCircle(points[i].x, points[i].y, radius, dotPaint)
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

    private fun withAlpha(color: Int, fade: Float): Int =
        (color and 0x00FFFFFF) or ((Color.alpha(color) * fade).toInt().coerceIn(0, 255) shl 24)

    companion object {
        const val DURATION_MS = 3000L
        private const val POP_MS = 180L
    }
}
