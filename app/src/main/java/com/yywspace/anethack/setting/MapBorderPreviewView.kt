package com.yywspace.anethack.setting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.yywspace.anethack.R
import com.yywspace.anethack.map.MapBorderStyle

/** 设置面板里的边框预览：用与地图相同的颜色、粗细和不透明度画一个方框。 */
class MapBorderPreviewView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    var colorPref: Int = MapBorderStyle.DEFAULT_COLOR
        set(value) { field = value; invalidate() }

    /** 以 0.1dp 为单位，与设置项一致。 */
    var widthTenths: Int = MapBorderStyle.DEFAULT_WIDTH_TENTHS
        set(value) { field = value; invalidate() }

    var opacity: Int = MapBorderStyle.DEFAULT_OPACITY
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val stroke = MapBorderStyle.widthDp(widthTenths) * density
        val inset = stroke / 2 + 2 * density
        rect.set(inset, inset, width - inset, height - inset)
        paint.color = MapBorderStyle.paintColor(context, colorPref, opacity)
        paint.strokeWidth = stroke
        canvas.drawRect(rect, paint)
    }
}
