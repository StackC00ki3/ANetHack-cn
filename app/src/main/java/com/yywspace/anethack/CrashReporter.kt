package com.yywspace.anethack

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * Collects crash evidence under files/crash so it can be exported after the
 * next launch (not under files/logs: AssetsLoader only unpacks the bundled
 * logs folder when files/logs does not exist yet):
 * - native_*.log: fatal signals in native code, written by androidcrash.c
 * - jni_*.log: Java exceptions thrown from NetHack JNI callbacks
 * - java_*.log: uncaught Java exceptions on any thread
 * - logcat_*.txt: logcat taken on the first launch after a crash
 * The export additionally contains the system's exit records and, on
 * Android 12+, the tombstone of the last native crashes.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val MAX_FILES = 20
    private const val KEY_LAST_SEEN = "lastSeen"
    private const val KEY_LAST_SNAPSHOT = "lastLogcatSnapshot"
    private val REPORT_PREFIXES = listOf("native_", "jni_", "java_")

    private lateinit var appContext: Context
    private lateinit var crashDir: File
    private var header = ""
    private val prefs by lazy { appContext.getSharedPreferences("crash_reporter", Context.MODE_PRIVATE) }

    /** Installs the Java and native handlers. Call before the game starts; repeated calls are no-ops. */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        crashDir = File(appContext.filesDir, "crash").apply { mkdirs() }
        header = buildHeader()
        installJavaHandler()
        try {
            System.loadLibrary("NetHack")
            nativeInstall(crashDir.path, header)
        } catch (e: Throwable) {
            Log.e(TAG, "native crash handler not installed", e)
        }
        // logcat still holds the dead process's last lines now; once the game
        // starts logging they are pushed out of the ring buffer quickly
        if (latestCrashTime() > prefs.getLong(KEY_LAST_SNAPSHOT, 0)) {
            val now = System.currentTimeMillis()
            try {
                File(crashDir, "logcat_$now.txt").outputStream().use { runLogcat(it) }
            } catch (e: Exception) {
                Log.e(TAG, "logcat snapshot failed", e)
            }
            prefs.edit { putLong(KEY_LAST_SNAPSHOT, now) }
        }
        trimFiles()
    }

    /** True if a crash happened after the user last answered the crash prompt or exported. */
    fun hasUnseenCrash(): Boolean = latestCrashTime() > prefs.getLong(KEY_LAST_SEEN, 0)

    fun markSeen() = prefs.edit { putLong(KEY_LAST_SEEN, System.currentTimeMillis()) }

    fun exportFileName(): String =
        "anethack-crash-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.zip"

    /** Writes everything collected as a zip to [uri] on a background thread. */
    fun export(context: Context, uri: Uri) {
        init(context)
        markSeen()
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            val message = try {
                appContext.contentResolver.openOutputStream(uri)?.use { writeZip(it) }
                    ?: throw IOException("cannot open $uri")
                R.string.crash_export_done
            } catch (e: Exception) {
                Log.e(TAG, "crash log export failed", e)
                R.string.crash_export_failed
            }
            mainHandler.post { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show() }
        }.start()
    }

    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Debug builds only: crashes in native code to check the whole report path. */
    fun triggerNativeTestCrash() = nativeTestCrash()

    /** Called from androidentry.c right before it aborts on a pending Java exception. */
    @JvmStatic
    fun recordCallbackException(method: String, throwable: Throwable) {
        writeReport("jni", "exception thrown from NetHack JNI callback $method", throwable)
    }

    private fun installJavaHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeReport("java", "uncaught exception in thread \"${thread.name}\"", throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun writeReport(prefix: String, title: String, throwable: Throwable) {
        if (!::crashDir.isInitialized) return
        try {
            val now = System.currentTimeMillis()
            File(crashDir, "${prefix}_$now.log").writeText(buildString {
                appendLine("*** ANetHack $prefix crash ***")
                appendLine(header)
                appendLine("time: ${formatTime(now)}")
                appendLine(title)
                appendLine()
                append(throwable.stackTraceToString())
            })
        } catch (e: Throwable) {
            Log.e(TAG, "cannot write crash report", e)
        }
    }

    private fun buildHeader(): String {
        val pkg = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return "app: ${appContext.packageName} ${pkg.versionName} (${pkg.longVersionCode})\n" +
            "device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}), ABIs ${Build.SUPPORTED_ABIS.joinToString()}\n" +
            "build: ${Build.FINGERPRINT}"
    }

    private fun reportFiles(): List<File> =
        crashDir.listFiles { file -> REPORT_PREFIXES.any { file.name.startsWith(it) } }
            ?.toList() ?: emptyList()

    private fun latestCrashTime(): Long {
        val reported = reportFiles().maxOfOrNull { it.lastModified() } ?: 0L
        val exited = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) latestCrashExitTime() else 0L
        return maxOf(reported, exited)
    }

    private fun trimFiles() {
        crashDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    private fun runLogcat(out: OutputStream) {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
        process.inputStream.use { it.copyTo(out) }
        process.waitFor()
    }

    private fun writeZip(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.entry("device.txt") { it.write(header.toByteArray()) }
            crashDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                zip.entry("crash/${file.name}") { os -> file.inputStream().use { it.copyTo(os) } }
            }
            File(appContext.filesDir, "logs/error/nethack.log").takeIf { it.exists() }?.let { file ->
                zip.entry("error/nethack.log") { os -> file.inputStream().use { it.copyTo(os) } }
            }
            zip.entry("logcat_now.txt") { runLogcat(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                writeExitInfo(zip)
        }
    }

    private fun ZipOutputStream.entry(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exitInfos(): List<ApplicationExitInfo> = try {
        appContext.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(appContext.packageName, 0, 10) ?: emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "cannot read exit reasons", e)
        emptyList()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun latestCrashExitTime(): Long {
        val crashReasons = setOf(
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
        )
        return exitInfos().filter { it.reason in crashReasons }.maxOfOrNull { it.timestamp } ?: 0L
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun writeExitInfo(zip: ZipOutputStream) {
        val infos = exitInfos()
        zip.entry("exit_info.txt") { os ->
            os.write(buildString {
                if (infos.isEmpty()) appendLine("no exit records")
                infos.forEach { info ->
                    appendLine("${formatTime(info.timestamp)}  pid ${info.pid}  reason ${reasonName(info.reason)}" +
                        "  status ${info.status}  importance ${info.importance}")
                    appendLine("  description: ${info.description}")
                    appendLine("  pss ${info.pss} kB, rss ${info.rss} kB")
                }
            }.toByteArray())
        }
        // Android 12+ attaches the tombstone (protobuf) to native crashes and
        // the thread dump (text) to ANRs
        infos.forEach { info ->
            val name = when (info.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "exit/tombstone_${info.timestamp}.pb"
                ApplicationExitInfo.REASON_ANR -> "exit/anr_${info.timestamp}.txt"
                else -> return@forEach
            }
            val trace = try {
                info.traceInputStream
            } catch (e: IOException) {
                null
            } ?: return@forEach
            zip.entry(name) { os -> trace.use { it.copyTo(os) } }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "UNKNOWN"
    } + " ($reason)"

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(millis))

    private external fun nativeInstall(dir: String, header: String)
    private external fun nativeTestCrash()
}
