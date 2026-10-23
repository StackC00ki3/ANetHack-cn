package com.yywspace.anethack.setting

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.yywspace.anethack.CrashReporter
import com.yywspace.anethack.LanguageManager
import com.yywspace.anethack.R
import com.yywspace.anethack.ThemeManager
import java.io.File


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        val toolbar = findViewById<Toolbar>(R.id.setting_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        initStatusBar()
    }

    private fun initStatusBar() {
        window.statusBarColor = Color.TRANSPARENT
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = !ThemeManager.isNightMode(this)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    class SettingsFragment : PreferenceFragmentCompat(){
        private var userSoundPerm:CheckBoxPreference? = null
        // 是否手动申请权限
        private var requestSoundPerm = false
        private val mainHandler = Handler(Looper.getMainLooper())
        private val settingsChangeListener =
            SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                when (key) {
                    ThemeManager.PREF_KEY -> {
                        val mode = prefs.getString(ThemeManager.PREF_KEY, ThemeManager.DEFAULT_MODE)
                        // 值已写入后再切换，避免在列表弹窗关闭过程中重建界面
                        mainHandler.post { ThemeManager.apply(mode) }
                    }
                    LanguageManager.PREF_KEY -> {
                        val mode = prefs.getString(LanguageManager.PREF_KEY, LanguageManager.DEFAULT_MODE)
                        mainHandler.post { LanguageManager.apply(mode) }
                    }
                }
            }
        private val exportCrashLog = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri -> uri?.let { CrashReporter.export(requireContext(), it) } }

        override fun onDestroy() {
            preferenceManager.sharedPreferences
                ?.unregisterOnSharedPreferenceChangeListener(settingsChangeListener)
            super.onDestroy()
        }

        override fun onResume() {
            super.onResume()
            userSoundPerm?.apply {
                if (!XXPermissions.isGranted(context, Permission.READ_MEDIA_AUDIO))
                    isChecked = false
                else {
                    // 拥有权限时，若为手动申请则设为有效
                    if (requestSoundPerm) isChecked = true
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            preferenceManager.sharedPreferences
                ?.registerOnSharedPreferenceChangeListener(settingsChangeListener)
            findPreference<Preference>("dumpLog")?.setOnPreferenceClickListener {
                context?.apply {
                    val fileNames = mutableListOf<String>()
                    File(filesDir,"logs/dump")
                        .walk()
                        .maxDepth(1)
                        .filter { it.extension == "log"  }
                        .forEach { fileNames.add(it.name) }
                    fileNames.sortByDescending { it.split(".")[0] }
                    AlertDialog.Builder(this).apply {
                        setTitle(R.string.pref_log_dump)
                        setItems(fileNames.toTypedArray()) { _, which ->
                            openInternalFile(context, "logs/dump/${fileNames[which]}")
                        }
                        setNegativeButton(R.string.dialog_confirm, null)
                        create()
                        show()
                    }
                }

                true
            }
            findPreference<Preference>("errorLog")?.setOnPreferenceClickListener {
                openInternalFile(context, "logs/error/nethack.log")
                true
            }
            CrashReporter.init(requireContext())
            findPreference<Preference>("crashLog")?.setOnPreferenceClickListener {
                exportCrashLog.launch(CrashReporter.exportFileName())
                true
            }
            findPreference<Preference>("crashTest")?.apply {
                isVisible = CrashReporter.isDebuggable(requireContext())
                setOnPreferenceClickListener {
                    CrashReporter.triggerNativeTestCrash()
                    true
                }
            }
            findPreference<EditTextPreference>("dumpLogMaxSize")?.apply {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }
            findPreference<EditTextPreference>("messageHistorySize")?.apply {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }
            findPreference<Preference>("optionEdit")?.apply {
                setOnPreferenceClickListener { _ ->
                    startActivity(Intent(context, OptionEditActivity::class.java))
                    true
                }
            }
            findPreference<Preference>("mapBorder")?.setOnPreferenceClickListener { preference ->
                // 面板贴着设置项弹出；行视图尚未布局时退回 Activity 根视图。
                val adapter = listView.adapter as? PreferenceGroupAdapter
                val row = adapter
                    ?.getPreferenceAdapterPosition(preference)
                    ?.takeIf { it >= 0 }
                    ?.let { listView.findViewHolderForAdapterPosition(it)?.itemView }
                MapBorderPanel(requireContext(), row, view).show()
                true
            }
            findPreference<Preference>("commandBar")?.apply {
                setOnPreferenceClickListener { _ ->
                    startActivity(Intent(context, CommandBarEditActivity::class.java))
                    true
                }
            }
            userSoundPerm = findPreference<CheckBoxPreference>("userSound")?.apply {
                setOnPreferenceClickListener {
                    if (!XXPermissions.isGranted(context, Permission.READ_MEDIA_AUDIO)) {
                        isChecked = false
                        XXPermissions.with(context)
                            .permission(Permission.READ_MEDIA_AUDIO)
                            .request(object : OnPermissionCallback {
                                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                                    if (!allGranted)
                                        return
                                    isChecked = true
                                    Toast.makeText(context, R.string.permission_granted, Toast.LENGTH_SHORT).show()
                                }
                                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                                    if (doNotAskAgain) {
                                        isChecked = false
                                        AlertDialog.Builder(context).apply {
                                            setTitle(R.string.permission_audio)
                                            setMessage(R.string.permission_denied)
                                            setNegativeButton(R.string.dialog_confirm) {_, _ ->
                                                // 是否手动申请权限
                                                requestSoundPerm = true
                                                XXPermissions.startPermissionActivity(context, permissions)
                                            }
                                            create()
                                            show()
                                        }

                                    }
                                }
                            })
                        return@setOnPreferenceClickListener true
                    }
                    true
                }
            }
        }

        private fun openInternalFile(context: Context?, relativePath:String) {
            context?.apply {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "$packageName.provider",
                    File(filesDir,relativePath)
                )
                intent.setDataAndType(contentUri, "text/plain")
                startActivity(intent)
            }
        }
    }
}
