package io.github.elli0t0.tetherbridge

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Every log line in the app goes through here, tag "App", and is also kept
 *  in memory so the screen can show it (and the user can copy it). */
object AppLog {
    const val TAG = "App"
    private const val MAX_LINES = 80

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun stamp(): String = timeFormat.format(Date())

    private fun add(level: String, msg: String) {
        val line = "${stamp()} $level $msg"
        val current = _lines.value
        val next = (current + line).takeLast(MAX_LINES)
        _lines.value = next
    }

    fun d(msg: String) {
        Log.d(TAG, msg)
        add("D", msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        if (t == null) {
            Log.e(TAG, msg)
        } else {
            Log.e(TAG, msg, t)
        }
        val detail = if (t == null) {
            msg
        } else {
            val trace = t.stackTrace.take(3).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            "$msg | ${t.javaClass.simpleName}: ${t.message} [$trace]"
        }
        add("E", detail)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
