package com.yywspace.anethack

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.OnBackPressedCallback
import com.yywspace.anethack.command.NHCommandParser
import com.yywspace.anethack.command.NHExtendCommand
import com.yywspace.anethack.command.NHKeyCommand
import com.yywspace.anethack.databinding.ActivityNethackBinding
import com.yywspace.anethack.identify.NHPriceIDialog
import com.yywspace.anethack.keybord.TouchActionParser
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
    private var isKeyboardShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNethackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handler = Handler(Looper.getMainLooper())
        nethack = NetHack(handler, this, binding,"${filesDir.path}/nethackdir")
        priceIDialog = NHPriceIDialog(this, nethack)
        initView()
        initKeyboard()
        initTouchControls()
        initBackNavigation()
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
    }

    override fun onPause() {
        binding.actionWheel.dismiss()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        binding.actionWheel.dismiss()
        super.onConfigurationChanged(newConfig)
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
    }
    private fun initBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!binding.actionWheel.dismiss()) {
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



    private fun processKeyPress(cmd:String) {
        if (cmd.isEmpty()) return
        when (cmd) {
            "Keyboard" -> {
                binding.actionWheel.dismiss()
                if (!isKeyboardShow)
                    showKeyboard()
                else
                    hideKeyboard()
                isKeyboardShow = !isKeyboardShow
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
                    NHCommandParser.parseNHCommand(cmd).forEach {
                        if (it is NHExtendCommand)
                            nethack.command.sendCommand(NHKeyCommand('#'))
                        nethack.command.sendCommand(it)
                    }
                }
            }
        }
    }

    private fun initTouchControls() {
        binding.bottomActionDock.onCommandPress = ::processKeyPress
        binding.mapView.onPlayerTap = ::showActionWheel
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = binding.bottomActionDock.layoutParams
            params.height = resources.getDimensionPixelSize(R.dimen.touch_dock_height) + bars.bottom
            binding.bottomActionDock.layoutParams = params
            binding.bottomActionDock.setPadding(0, 0, 0, bars.bottom)
            binding.actionWheel.setSafeInsets(bars.top, binding.bottomActionDock.height)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun showActionWheel(mapAnchor: PointF) {
        if (binding.dialogContainer.visibility == View.VISIBLE) return
        val configured = nethack.prefs.commandPanel.orEmpty().ifEmpty {
            getString(R.string.pref_keyboard_command_panel_default)
        }
        val pages = TouchActionParser.parsePages(configured)
        if (pages.isEmpty()) return
        val mapLocation = IntArray(2)
        val wheelLocation = IntArray(2)
        binding.mapView.getLocationInWindow(mapLocation)
        binding.actionWheel.getLocationInWindow(wheelLocation)
        val anchor = PointF(
            mapLocation[0] + mapAnchor.x - wheelLocation[0],
            mapLocation[1] + mapAnchor.y - wheelLocation[1],
        )
        binding.actionWheel.setSafeInsets(
            binding.messageView.bottom,
            binding.bottomActionDock.height + if (isKeyboardShow) binding.keyboardView.height else 0,
        )
        binding.actionWheel.show(anchor, pages, ::processKeyPress)
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
        window.statusBarColor = Color.BLACK
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun processLogs() {
        val dumpLogMaxSize = nethack.prefs.dumpLogMaxSize
        object : Thread() {
            override fun run() {
                // save error log
                val logcatProcess = Runtime.getRuntime().exec("logcat -d *:W")
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

}