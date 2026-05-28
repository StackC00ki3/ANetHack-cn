package com.yywspace.anethack.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.text.DynamicLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import com.yywspace.anethack.NetHack
import com.yywspace.anethack.window.NHWMessage
import java.util.stream.Collectors
import kotlin.math.ceil
import kotlin.streams.toList
import androidx.core.graphics.withTranslation


class NHMessageSurfaceView: SurfaceView, SurfaceHolder.Callback,Runnable {
    private var textSize = 42f
    private val textPaint:TextPaint = TextPaint()
    private lateinit var nh: NetHack
    private lateinit var nhMessage: NHWMessage
    private var messageInit: Boolean = false
    private var messageSize = 3
    private var maxMessageSize = 5

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
    fun initMessage(nh: NetHack, message: NHWMessage) {
        this.nh = nh
        this.nhMessage = message
        messageInit = true
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        isDrawing = true
        if (drawThread?.isAlive != true) {
            drawThread = Thread(this, "NHMessageSurfaceView").also { it.start() }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isDrawing = false
        drawThread?.interrupt()
    }
    private fun drawMessageList(canvas: Canvas?) {
        canvas?.apply {
            if (messageInit) {
                var messageListHeight = 0f
                nhMessage.getRecentMessageList(messageSize)
                    .stream()
                    .limit(maxMessageSize.toLong())
                    .collect(Collectors.toList())
                    .reversed().forEach{
                    val dynamicLayout = DynamicLayout.Builder.obtain(
                        it.toSpannableString(), textPaint,
                        width
                    ).build()
                    canvas.withTranslation(0f, messageListHeight) {
                        dynamicLayout.draw(this)
                    }
                    messageListHeight += dynamicLayout.height
                }
                post {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, ceil(messageListHeight).toInt()
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
                Log.w("NHMessageSurfaceView", "lockCanvas failed", e)
                return
            }
            canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawMessageList(canvas)
        } finally {
            canvas?.let {
                try {
                    surfaceHolder.unlockCanvasAndPost(it)
                } catch (e: RuntimeException) {
                    Log.w("NHMessageSurfaceView", "unlockCanvasAndPost failed", e)
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

