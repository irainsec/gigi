package com.aman.gigi.data.update

import android.content.Context

/**
 * The handful of update choices that have to survive a restart.
 *
 * Deliberately SharedPreferences rather than DataStore: the download service reads these
 * synchronously on its way to `startForeground`, where suspending isn't an option.
 */
object UpdatePrefs {
    private const val FILE = "gigi_update_prefs"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_DEFERRED_VERSION = "deferred_version_code"
    private const val KEY_DEFERRED_AT = "deferred_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * When on, a download started on mobile data waits for Wi-Fi instead of spending the
     * user's data on 25 MB. Off by default — someone who taps "Update" usually means now.
     */
    fun wifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

    /**
     * "Remind me later" for one specific build.
     *
     * Deliberately expires after a day rather than lasting forever: there's no manual
     * "check for updates" button anywhere in the app, so a permanent deferral would be
     * indistinguishable from the updater being broken.
     */
    private const val DEFERRAL_MS = 24L * 60 * 60 * 1000

    fun deferVersion(context: Context, versionCode: Int) {
        prefs(context).edit()
            .putInt(KEY_DEFERRED_VERSION, versionCode)
            .putLong(KEY_DEFERRED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearDeferral(context: Context) {
        prefs(context).edit().remove(KEY_DEFERRED_VERSION).remove(KEY_DEFERRED_AT).apply()
    }

    /** True when this build was waved away by the user within the last day. */
    fun isDeferred(context: Context, versionCode: Int): Boolean {
        if (versionCode <= 0) return false
        val p = prefs(context)
        if (p.getInt(KEY_DEFERRED_VERSION, 0) != versionCode) return false
        val at = p.getLong(KEY_DEFERRED_AT, 0L)
        // A clock that moved backwards shouldn't strand the user on an old build.
        val age = System.currentTimeMillis() - at
        return age in 0 until DEFERRAL_MS
    }
}
