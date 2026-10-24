package com.yywspace.anethack

import android.content.Intent
import android.content.res.Configuration
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.yywspace.anethack.command.NHCommandParser
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
import com.yywspace.anethack.identify.NHPriceIDialog
import com.yywspace.anethack.keybord.LongPressDragHelper
import com.yywspace.anethack.keybord.TouchActionCatalog
import com.yywspace.anethack.keybord.TouchCommandBar
import com.yywspace.anethack.setting.SettingsActivity
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream


class NetHackActivity : AppCompatActivity() {
    private lateinit var nethack:NetHack
    private lateinit var handler:Handler
    private lateinit var binding: ActivityNethackBinding
    private lateinit var priceIDialog: NHPriceIDialog
    private lateinit var floatingButtonDrag: LongPressDragHelper
    private var isKeyboardShow = false
    private var navBarInset = 0
    // 手滑连点的冷却：同一时刻只接受一次按钮输入，避免指令堆积后一路执行到底。
    private var lastKeyPressNanos = 0L
    private val exportCrashLog = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { CrashReporter.export(this, it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.init(this)
        binding = ActivityNethackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler = Handler(Looper.getMainLooper())
        nethack = NetHack(handler, this, binding,"${filesDir.path}/nethackdir")
        priceIDialog = NHPriceIDialog(this, nethack)
        initView()
        initKeyboard()
        initTouchControls()
        initBackNavigation()
        if (CrashReporter.hasUnseenCrash())
            showCrashReportDialog()
        AssetsLoader(this).loadAssets(
            listOf("nethackdir", "logs", "conf"), false
        ) { overwrite ->
            processLogs()
            processConf(overwrite)
            nethack.run()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.actionWheel.dismiss()
        if (nethack.prefs.immersiveMode)
            hideSystemUi()
        else
            showSystemUi()
        if (nethack.prefs.priceId)
            binding.floatingButton.visibility = View.VISIBLE
        else
            binding.floatingButton.visibility = View.GONE
        binding.keyboardView.setKeyboardVibrate(nethack.prefs.keyboardVibrate)
        binding.keyboardView.refreshTheme()
        binding.actionWheel.refreshTheme()
        binding.actionWheel.setPanelOpacity(nethack.prefs.actionWheelOpacity)
        binding.directionPad.refreshTheme()
        // 等方向时进了设置页开关方向键盘，回来立即生效
        nethack.refreshDirectionPad()
        refreshCommandBar()
    }

    override fun onPause() {
        binding.actionWheel.dismiss()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        binding.actionWheel.dismiss()
        super.onConfigurationChanged(newConfig)
        refreshCommandBar()
        binding.keyboardView.refreshTheme()
        binding.actionWheel.refreshTheme()
        binding.directionPad.refreshTheme()
        ViewCompat.requestApplyInsets(binding.root)
        if(isKeyboardShow) {
            binding.keyboardView.apply {
                postDelayed({
                    refreshNHKeyboard()
                }, 100)
            }
        }
    }

    private fun initView() {
        if (nethack.prefs.priceId)
            binding.floatingButton.visibility = View.VISIBLE
        else
            binding.floatingButton.visibility = View.GONE
        binding.floatingButton.setOnClickListener {
            priceIDialog.show()
        }
        // 长按拖动换位置，免得挡住地图
        floatingButtonDrag = LongPressDragHelper(
            binding.floatingButton, nethack.prefs::priceIdButtonPosition
        )
    }
    private fun showCrashReportDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_report_title)
            .setMessage(R.string.crash_report_message)
            .setPositiveButton(R.string.crash_report_export) { _, _ ->
                exportCrashLog.launch(CrashReporter.exportFileName())
            }
            .setNegativeButton(R.string.crash_report_ignore, null)
            .setOnDismissListener { CrashReporter.markSeen() }
            .show()
    }

    private fun initBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!binding.actionWheel.navigateBackOrDismiss()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun initKeyboard() {
        binding.keyboardView.apply {
            onKeyPress = {
                if (nethack.isRunning && it.label.isNotEmpty())
                    processKeyPress(it.value)
            }
            onSpecialKeyLongPress = {
                when(it.label) {
                    "ESC" -> {
                        processKeyPress("Center")
                    }
                    "Meta", "Ctrl" ->
                        processKeyPress("#")
                }
            }
            visibility = View.GONE
        }


    }

    private fun hideKeyboard() {
        //向下位移隐藏动画  从自身位置的最上端向下滑动了自身的高度
        TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f
        ).apply {
            repeatMode = Animation.REVERSE
            duration = 200
            setAnimationListener(object :AnimationListener{
                override fun onAnimationStart(animation: Animation?) {
                }
                override fun onAnimationEnd(animation: Animation?) {
                    binding.keyboardView.visibility = View.GONE
                }
                override fun onAnimationRepeat(animation: Animation?) {
                }
            })
            binding.keyboardView.startAnimation(this)
        }
    }
    private fun showKeyboard() {
        //向上位移显示动画  从自身位置的最下端向上滑动了自身的高度
        TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f
        ).apply {
            repeatMode = Animation.REVERSE
            duration = 200
            binding.keyboardView.startAnimation(this)
            binding.keyboardView.visibility = View.VISIBLE
        }
    }



    /**
     * 连点冷却：[KEY_PRESS_COOLDOWN_MS] 内的重复按键直接丢弃，避免手滑把指令堆进队列。
     * 另外待处理输入超过 [MAX_PENDING_INPUT] 条时也不再接受新按键——
     * 按住不放时按键间隔可能略大于冷却时间，只靠时间窗挡不住积压。
     * 只用于底部命令栏和动作轮盘这类可连点的按钮；软键盘打字不节流，否则会丢字符。
     */
    private fun acceptButtonPress(): Boolean {
        val now = System.nanoTime()
        if (now - lastKeyPressNanos < KEY_PRESS_COOLDOWN_MS * 1_000_000L) return false
        if (nethack.command.pendingUserInputCount() >= MAX_PENDING_INPUT) return false
        lastKeyPressNanos = now
        return true
    }

    /** 底部命令栏 / 动作轮盘的入口：先过冷却，再走正常按键处理。 */
    private fun processButtonPress(cmd: String) {
        if (!acceptButtonPress()) return
        processKeyPress(cmd)
    }

    private fun processKeyPress(cmd:String) {
        if (cmd.isEmpty()) return
        when (cmd) {
            "Keyboard" -> {
                binding.actionWheel.dismiss()
                isKeyboardShow = !isKeyboardShow
                if (isKeyboardShow)
                    showKeyboard()
                else
                    hideKeyboard()
            }
            "Repeat" -> {
                Thread {
                    for (i in 0..100) {
                        for (j in 0..5) {
                            nethack.command.sendCommand(NHKeyCommand('h'))
                        }
                        for (j in 0..5) {
                            nethack.command.sendCommand(NHKeyCommand('l'))
                        }
                    }
                }.start()
            }
            "Center" -> {
                binding.mapView.centerPlayerInScreen()
            }
            "Setting" -> {
                binding.actionWheel.dismiss()
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            else -> {
                if(nethack.isRunning) {
                    val commands = NHCommandParser.parseNHCommand(cmd)
                    // 多键序列是一次性脚本，弹窗出现时仍可作为预设回答。
                    if (commands.size > 1)
                        commands.forEach { it.fromSequence = true }
                    commands.forEach {
                        if (it is NHExtendCommand)
                            nethack.command.sendCommand(NHKeyCommand('#').apply {
                                fromSequence = it.fromSequence
                            })
                        nethack.command.sendCommand(it)
                    }
                }
            }
        }
    }

    private fun initTouchControls() {
        binding.bottomCommandBar.onCommandPress = ::processButtonPress
        refreshCommandBar()
        binding.mapView.onPlayerTap = ::showActionWheel
        binding.directionPad.onKey = {
            if (nethack.isRunning)
                nethack.command.sendCommand(NHKeyCommand(it))
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            navBarInset = bars.bottom
            binding.bottomCommandBar.setPadding(bars.left, 0, bars.right, bars.bottom)
            updateKeyboardMargin()
            binding.root.post {
                refreshActionWheelSafeInsets(bars.left, bars.top, bars.right)
                refreshFloatingButtonSafeInsets(bars.left, bars.right)
                refreshDirectionPadOffset(bars.left, bars.right)
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun refreshCommandBar() {
        val rows = TouchCommandBar.parseRows(nethack.prefs.commandBar).filter { it.enabled }
        binding.bottomCommandBar.setActions(rows)
        updateKeyboardMargin()
    }

    /**
     * The keyboard is an overlay floating above the bottommost command bar
     * row: it slides up over the other rows, which stay rendered underneath,
     * and slides back down to reveal them — the bar itself never changes.
     */
    private fun updateKeyboardMargin() {
        val rowHeight = resources.getDimensionPixelSize(R.dimen.touch_combat_row_height)
        val hasRows = binding.bottomCommandBar.visibility == View.VISIBLE
        binding.keyboardView.updateLayoutParams<FrameLayout.LayoutParams> {
            bottomMargin = navBarInset + if (hasRows) rowHeight else 0
        }
    }

    private fun showActionWheel(mapAnchor: PointF) {
        if (!nethack.prefs.actionWheelEnabled) return
        if (binding.dialogContainer.visibility == View.VISIBLE) return
        val categories = TouchActionCatalog.build(nethack.prefs.commandPanel)
        if (categories.isEmpty()) return
        val mapLocation = IntArray(2)
        val wheelLocation = IntArray(2)
        binding.mapView.getLocationInWindow(mapLocation)
        binding.actionWheel.getLocationInWindow(wheelLocation)
        val anchor = PointF(
            mapLocation[0] + mapAnchor.x - wheelLocation[0],
            mapLocation[1] + mapAnchor.y - wheelLocation[1],
        )
        val insets = ViewCompat.getRootWindowInsets(binding.root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        refreshActionWheelSafeInsets(
            left = insets?.left ?: 0,
            top = insets?.top ?: 0,
            right = insets?.right ?: 0,
        )
        binding.actionWheel.setPanelOpacity(nethack.prefs.actionWheelOpacity)
        binding.actionWheel.show(anchor, categories, ::processButtonPress)
    }

    private fun refreshActionWheelSafeInsets(left: Int, top: Int, right: Int) {
        val bottomControlsTop = if (isKeyboardShow && binding.keyboardView.visibility == View.VISIBLE) {
            binding.keyboardView.top
        } else {
            binding.bottomCommandBar.top
        }
        val safeBottom = if (bottomControlsTop > 0) {
            binding.root.height - bottomControlsTop
        } else {
            binding.bottomCommandBar.height
        }
        binding.actionWheel.setSafeInsets(
            left = left,
            top = maxOf(top, binding.messageView.bottom),
            right = right,
            bottom = safeBottom,
        )
    }

    /** 拖动价格鉴定按钮时避开侧边导航栏，也别盖住顶部的状态栏和消息栏。 */
    private fun refreshFloatingButtonSafeInsets(left: Int, right: Int) {
        // 消息栏和按钮所在的 FrameLayout 都从根布局顶端算起
        floatingButtonDrag.setSafeInsets(left, binding.messageView.bottom, right, 0)
    }

    /** 方向键盘贴着底部按钮栏上方居中，横屏非沉浸模式下导航栏在侧边，往中间让一让。 */
    private fun refreshDirectionPadOffset(left: Int, right: Int) {
        binding.directionPad.translationX = (left - right) / 2f
    }

    private fun hideSystemUi() {
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemUi() {
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        window.statusBarColor = getColor(R.color.ui_background)
        windowInsetsController.isAppearanceLightStatusBars = !ThemeManager.isNightMode(this)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun processLogs() {
        val dumpLogMaxSize = nethack.prefs.dumpLogMaxSize
        object : Thread() {
            override fun run() {
                // save error log: only this process, otherwise vendor spam
                // (VSPA etc.) from other processes fills the file first
                val logcatProcess = Runtime.getRuntime()
                    .exec("logcat -d *:W --pid=${android.os.Process.myPid()}")
                val errorLog = File(filesDir,"logs/error/nethack.log")
                FileOutputStream(errorLog).use { fileOut ->
                    logcatProcess.inputStream.copyTo(fileOut)
                }
                // delete dump logs that exceed the size
                val dumpDir = File(filesDir,"logs/dump")
                dumpDir.listFiles(FileFilter { it.extension == "log" })
                    ?.apply {
                    if (size > dumpLogMaxSize) {
                        toList()
                            .sortedBy { it.name.split(".")[0] }
                            .stream().limit((size - dumpLogMaxSize).toLong()).forEach {
                                it.delete()
                            }
                    }
                }
            }
        }.start()
    }

    private fun processConf(overwrite:Boolean) {
        val sysconf = File(filesDir,"conf/sysconf")
        val userConf = File(filesDir,"conf/nethackrc")
        val sysconfTarget = File(filesDir,"nethackdir/sysconf")
        val userConfTarget = File(filesDir,"nethackdir/.nethackrc")
        if (!sysconfTarget.exists() || overwrite)
            FileOutputStream(sysconfTarget).use { fileOut ->
                FileInputStream(sysconf).copyTo(fileOut)
            }
        if (!userConfTarget.exists() || overwrite)
            FileOutputStream(userConfTarget).use { fileOut ->
                FileInputStream(userConf).copyTo(fileOut)
            }
    }

    companion object {
        /** 按钮连点冷却，略长于一次点击的最短间隔，但不影响正常连续操作。 */
        private const val KEY_PRESS_COOLDOWN_MS = 200L
        /** 允许排队等待执行的玩家输入条数；超出的按键直接丢弃而不是堆进队列。 */
        private const val MAX_PENDING_INPUT = 2
    }

}