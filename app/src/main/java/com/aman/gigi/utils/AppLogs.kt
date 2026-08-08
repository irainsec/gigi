package com.aman.gigi.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogs {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun addLog(tag: String, message: String, level: String = "INFO") {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] [$level] $tag: $message"
        val currentList = _logs.value.toMutableList()
        currentList.add(0, logLine)
        if (currentList.size > 200) {
            currentList.removeAt(currentList.size - 1)
        }
        _logs.value = currentList
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
}
