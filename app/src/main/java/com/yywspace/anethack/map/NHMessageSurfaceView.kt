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
import com.yywspace.anethack.ThemeManager
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

    /**
     * 必须等绘制线程真正退出再返回：SurfaceView 在回调返回后就会释放 Surface，
     * 线程还在 lockCanvas / unlockCanvasAndPost 里就会锁到已销毁的 mutex 被 FORTIFY 直接 abort。
     */
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isDrawing = false
        val thread = drawThread
        if (thread != null) {
            thread.interrupt()
            if (thread !== Thread.currentThread()) {
                try {
                    thread.join(1000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            if (!thread.isAlive) drawThread = null
        }
    }
    private fun drawMessageList(canvas: Canvas?) {
        canvas?.apply {
            if (messageInit) {
                val lightBackground = !ThemeManager.isNightMode(context)
                var messageListHeight = 0f
                nhMessage.getRecentMessageList(messageSize)
                    .stream()
                    .limit(maxMessageSize.toLong())
                    .collect(Collectors.toList())
                    .reversed().forEach{
                    val dynamicLayout = DynamicLayout.Builder.obtain(
                        it.toSpannableString(lightBackground = lightBackground), textPaint,
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
        val lockedCanvas = try {
            surfaceHolder.lockCanvas()
        } catch (e: RuntimeException) {
            Log.w("NHMessageSurfaceView", "lockCanvas failed", e)
            return
        }
        // lockCanvas 拿不到会返回 null，此时并没有持锁，不能再 unlock（会在下一轮误放掉别人的锁）
        if (lockedCanvas == null) return
        canvas = lockedCanvas
        try {
            lockedCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            drawMessageList(lockedCanvas)
        } finally {
            canvas = null
            try {
                surfaceHolder.unlockCanvasAndPost(lockedCanvas)
            } catch (e: RuntimeException) {
                Log.w("NHMessageSurfaceView", "unlockCanvasAndPost failed", e)
            }
        }
    }
    override fun run() {
        while (isDrawing && !Thread.currentThread().isInterrupted) {
            try {
                draw()
            } catch (e: RuntimeException) {
                // 绘制线程一旦被未捕获异常带走，surfaceDestroyed 会等到超时；
                // 记一笔继续跑，下一轮会因 surface 失效自行退出。
                Log.w("NHMessageSurfaceView", "draw failed", e)
            }
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }
}

