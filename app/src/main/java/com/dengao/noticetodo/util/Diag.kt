package com.dengao.noticetodo.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 极简运行日志：同时写 logcat 和 filesDir/diag.log，设置页里能直接看。
 * 目的：另一台手机上"后台收不到"这种问题，没有日志就只能靠猜。
 */
object Diag {

    private const val TAG = "NoticeTodo"
    private const val FILE_NAME = "diag.log"
    private const val MAX_BYTES = 200_000
    private const val BUFFER_SIZE = 400

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val lock = Any()
    private val buffer = ArrayDeque<String>()

    fun log(context: Context?, tag: String, message: String) {
        val line = "${timeFormat.format(Date())} [$tag] $message"
        Log.i(TAG, line)
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > BUFFER_SIZE) buffer.removeFirst()
        }
        if (context != null) {
            runCatching {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                if (file.length() > MAX_BYTES) file.writeText("")
                file.appendText(line + "\n")
            }
        }
    }

    fun recent(count: Int = BUFFER_SIZE): String = synchronized(lock) {
        buffer.toList().takeLast(count).joinToString("\n")
    }

    fun readAll(context: Context): String {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        val text = runCatching { file.readText() }.getOrDefault("")
        return text.ifBlank { recent() }
    }

    fun clear(context: Context) {
        synchronized(lock) { buffer.clear() }
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).writeText("") }
    }
}
