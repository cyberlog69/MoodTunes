package com.moodtunes.app.platform

import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global uncaught exception handler. Writes crash details to disk so the app
 * can surface a recovery prompt on next launch and help with debugging.
 */
@Singleton
class CrashHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val crashDir: File
        get() = File(context.filesDir, CRASH_DIR)

    private val originalHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeCrashFile(thread, throwable)
            }.onFailure {
                Timber.e(it, "Failed to persist crash report")
            }
            originalHandler?.uncaughtException(thread, throwable)
                ?: run { Process.killProcess(Process.myPid()) }
        }
    }

    fun writeCrashFile(thread: Thread, throwable: Throwable) {
        runCatching {
            crashDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val file = File(crashDir, "crash_$timestamp.log")
            val stack = throwable.stackTraceToString()
            val device = buildDeviceInfo()
            val report = buildString {
                appendLine("Device: $device")
                appendLine("Thread: ${thread.name}")
                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("Exception: ${throwable::class.qualifiedName}: ${throwable.message}")
                appendLine("Stack trace:")
                appendLine(stack)
            }
            file.writeText(report)
            Timber.e(throwable, "Crash captured to %s", file.absolutePath)
        }
    }

    fun latestCrashReport(): String? = runCatching {
        val dir = crashDir
        if (!dir.exists()) return null
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash_") }
            ?.maxByOrNull { it.lastModified() }
            ?.takeIf { it.exists() }
            ?.readText()
    }.getOrNull()

    fun clearLatestCrash() {
        runCatching {
            crashDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("crash_") }
                ?.forEach { it.delete() }
        }
    }

    private fun buildDeviceInfo(): String {
        val fields = arrayOf(
            "SDK" to android.os.Build.VERSION.SDK_INT,
            "MODEL" to android.os.Build.MODEL,
            "MANUFACTURER" to android.os.Build.MANUFACTURER,
            "BRAND" to android.os.Build.BRAND,
            "RELEASE" to android.os.Build.VERSION.RELEASE
        )
        return fields.joinToString(", ") { "${it.first}=${it.second}" }
    }

    companion object {
        const val CRASH_DIR = "crashes"
    }
}
