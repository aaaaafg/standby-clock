package com.deskclock.standby

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃/异常日志：写入应用私有目录，供"诊断信息"页查看。
 * 任何关键路径的异常都会被记录而不是让应用闪退。
 */
object CrashLog {

    private const val FILE_NAME = "crash.log"
    private const val MAX_SIZE = 64 * 1024
    private var dir: File? = null

    fun install(context: Context) {
        val app = context.applicationContext
        dir = app.getExternalFilesDir(null) ?: app.filesDir
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            record("uncaught", e)
            prev?.uncaughtException(t, e)
        }
    }

    fun record(tag: String, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            append("[$tag]\n${sw}")
        } catch (e: Exception) {
            // 日志失败不影响主流程
        }
    }

    /** 记录一条普通事件（检测状态变化、启动尝试等），供诊断页追踪时间线 */
    fun event(tag: String, message: String) {
        try {
            append("[$tag] $message")
        } catch (e: Exception) {
            // 日志失败不影响主流程
        }
    }

    private fun append(body: String) {
        val f = logFile() ?: return
        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "[$ts] $body\n\n"
        val old = if (f.exists()) f.readText() else ""
        val merged = entry + old
        f.writeText(if (merged.length > MAX_SIZE) merged.substring(0, MAX_SIZE) else merged)
    }

    fun readLast(): String? {
        return try {
            val f = logFile() ?: return null
            if (f.exists()) f.readText().take(4000) else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        try {
            logFile()?.delete()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun logFile(): File? = dir?.let { File(it, FILE_NAME) }
}
