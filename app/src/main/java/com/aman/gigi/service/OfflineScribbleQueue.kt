package com.aman.gigi.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A lightweight SharedPreferences-backed FIFO queue for scribble payloads
 * that failed to send (e.g. partner offline). On reconnect, drain and retry.
 */
@Singleton
class OfflineScribbleQueue @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gigi_offline_scribbles", Context.MODE_PRIVATE)

    private val KEY_COUNT = "pending_count"
    private fun itemKey(i: Int) = "item_$i"

    @Synchronized
    fun enqueue(payload: String) {
        val count = prefs.getInt(KEY_COUNT, 0)
        prefs.edit()
            .putString(itemKey(count), payload)
            .putInt(KEY_COUNT, count + 1)
            .apply()
    }

    @Synchronized
    fun dequeueAll(): List<String> {
        val count = prefs.getInt(KEY_COUNT, 0)
        if (count == 0) return emptyList()
        val items = (0 until count).mapNotNull { prefs.getString(itemKey(it), null) }
        clear()
        return items
    }

    @Synchronized
    fun size(): Int = prefs.getInt(KEY_COUNT, 0)

    @Synchronized
    fun clear() {
        val count = prefs.getInt(KEY_COUNT, 0)
        val editor = prefs.edit()
        for (i in 0 until count) editor.remove(itemKey(i))
        editor.putInt(KEY_COUNT, 0).apply()
    }
}
