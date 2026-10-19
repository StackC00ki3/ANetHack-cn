package com.yywspace.anethack.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.text.DynamicLayout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.ThemeManager
import com.yywspace.anethack.entity.NHStatus
import com.yywspace.anethack.entity.NHStatus.StatusField
import kotlin.math.ceil
import androidx.core.graphics.withTranslation


class NHStatusSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 42f
    private val paint: Paint = Paint()
    private val textPaint:TextPaint = TextPaint()
    private lateinit var nh: NetHack
    private lateinit var status: NHStatus
    private var statusInit: Boolean = false

    private var holder: SurfaceHolder? = null
    private var canvas: Canvas? = null
    @Volatile private var isDrawing = false
    private var drawThread: Thread? = null

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    init {
        initView()
        textPaint.textSize = textSize
        textPaint.isAntiAlias = true
    }

    private fun initView() {
        holder = getHolder()
        holder?.addCallback(this)
        holder?.setFormat(PixelFormat.TRANSLUCENT)
        isFocusable = true
        this.keepScreenOn = true
    }
    fun initStatus(nh: NetHack, nhStatus: NHStatus) {
        this.nh = nh
        this.status = nhStatus
        statusInit = true
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        isDrawing = true
        if (drawThread?.isAlive != true) {
            drawThread = Thread(this, "NHStatusSurfaceView").also { it.start() }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isDrawing = false
        drawThread?.interrupt()
    }
    
    private fun getStatus(field: StatusField, lightBackground: Boolean):Pair<StatusField, Spannable> {
        return when (field) {
            StatusField.BL_HP -> {
                val hp = status.hitPoints.toSpannableString(lightBackground)
                val hpMax = status.maxHitPoints.toSpannableString(lightBackground)
                Pair(field, SpannableStringBuilder(hp).append(hpMax))
            }
            StatusField.BL_ENE -> {
                val pw = status.power.toSpannableString(lightBackground)
                val pwMax = status.maxPower.toSpannableString(lightBackground)
                Pair(field, SpannableStringBuilder(pw).append(pwMax))
            }
            StatusField.BL_XP -> {
                val xp = status.expLevel.toSpannableString(lightBackground)
                val exp = status.expPoints.toSpannableString(lightBackground)
                val hd = status.hitDice.toSpannableString(lightBackground)
                if (xp.isEmpty())
                    Pair(field, hd)
                else
                    Pair(field, SpannableStringBuilder(xp).append(exp))
            }
            StatusField.BL_CONDITION -> {
                Pair(field, status.getConditionSpannable(lightBackground))
            }
            else -> Pair(field, status.getField(field).toSpannableString(lightBackground))
        }
    }
    private fun buildStatusBar(lightBackground: Boolean):List<List<Pair<StatusField, Spannable>>> {
        val align = getStatus(StatusField.BL_ALIGN, lightBackground)
        val title = getStatus(StatusField.BL_TITLE, lightBackground)
        val st = getStatus(StatusField.BL_STR, lightBackground)
        val dx = getStatus(StatusField.BL_DX, lightBackground)
        val co = getStatus(StatusField.BL_CO, lightBackground)
        val intel = getStatus(StatusField.BL_IN, lightBackground)
        val wi = getStatus(StatusField.BL_WI, lightBackground)
        val ch = getStatus(StatusField.BL_CH, lightBackground)
        val hp = getStatus(StatusField.BL_HP, lightBackground)
        val pw = getStatus(StatusField.BL_ENE, lightBackground)
        val gold = getStatus(StatusField.BL_GOLD, lightBackground)
        val ac = getStatus(StatusField.BL_AC, lightBackground)
        val xp = getStatus(StatusField.BL_XP, lightBackground)
        val time = getStatus(StatusField.BL_TIME, lightBackground)
        val levelDesc = getStatus(StatusField.BL_LEVELDESC, lightBackground)
        val hunger = getStatus(StatusField.BL_HUNGER, lightBackground)
        val cap = getStatus(StatusField.BL_CAP, lightBackground)
        val condition = getStatus(StatusField.BL_CONDITION, lightBackground)
        val statusBarList = mutableListOf<List<Pair<StatusField, Spannable>>>().apply {
            add(listOf(title, align))
            add(listOf(hp, st, dx, co, intel, wi, ch))
            add(listOf(pw, gold, ac, xp, time))
            add(listOf(levelDesc, hunger, cap, condition))
        }
        return statusBarList
    }

    private fun drawTitle(status:Pair<StatusField, Spannable>,  canvas: Canvas):RectF {
        val hp = this.status.hitPoints
        val title = SpannableStringBuilder(status.second)
        val percent = hp.percent
        val remainSpan = BackgroundColorSpan(Color.argb(200, 220,220,220))
        val colorSpan = BackgroundColorSpan(hp.color)
        title.setSpan(colorSpan, 0, title.length * percent / 100, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        title.setSpan(remainSpan, title.length * percent / 100, title.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        val statusLayout =DynamicLayout.Builder.obtain(
            title, textPaint,
            ceil(DynamicLayout.getDesiredWidth(title, textPaint)).toInt()
        ).build()
        statusLayout.draw(canvas)
        return RectF(0f,0f, statusLayout.width.toFloat(), statusLayout.height.toFloat())
    }

    private fun drawStatusBar(canvas: Canvas?) {
        canvas?.apply {
            if (statusInit) {
                val statusBarList = buildStatusBar(!ThemeManager.isNightMode(context))
                var statusBarHeight = 0f
                statusBarList.forEach{
                    var statusBarWidth = 0f
                    var maxHeight = 0f
                    it.forEach { s ->
                        canvas.withTranslation(statusBarWidth, statusBarHeight) {
                            if (s.first == StatusField.BL_TITLE) {
                                val border = drawTitle(s, this)
                                statusBarWidth += (border.width() + 20f)
                                maxHeight = maxHeight.coerceAtLeast(border.height())
                            } else {
                                if (s.second.isNotEmpty()) {
                                    val dynamicLayout = DynamicLayout.Builder.obtain(
                                        s.second,
                                        textPaint,
                                        ceil(
                                            DynamicLayout.getDesiredWidth(
                                                s.second,
                                                textPaint
                                            )
                                        ).toInt()
                                    ).build()
                                    dynamicLayout.draw(this)
                                    statusBarWidth += (dynamicLayout.width + 20f)
                                    maxHeight =
                                        maxHeight.coerceAtLeast(dynamicLayout.height.toFloat())
                                }
                            }
                        }
                    }
                    statusBarHeight += maxHeight
                }
                post {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, ceil(statusBarHeight+5).toInt()
                    )
                }
            }
        }
    }

    private fun draw() {
        val surfaceHolder = holder ?: return
        if (!surfaceHolder.surface.isValid) {
            return
        }
        try {
            canvas = try {
                surfaceHolder.lockCanvas()
            } catch (e: RuntimeException) {
                Log.w("NHStatusSurfaceView", "lockCanvas failed", e)
                return
            }
            canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawStatusBar(canvas)
        } finally {
            canvas?.let {
                try {
                    surfaceHolder.unlockCanvasAndPost(it)
                } catch (e: RuntimeException) {
                    Log.w("NHStatusSurfaceView", "unlockCanvasAndPost failed", e)
                }
            }
            canvas = null
        }
    }
    override fun run() {
        while (isDrawing && !Thread.currentThread().isInterrupted) {
            draw()
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }
}

