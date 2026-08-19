package com.aman.gigi.data.nowplaying

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Reaches the singletons that describe "what's playing" from inside a composable.
 *
 * These are read by screens that have no view-model of their own (the settings sheet,
 * the vinyl overlay), and threading them through half a dozen composable signatures
 * would be worse than an entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NowPlayingEntryPoint {
    fun mediaControlHub(): MediaControlHub
    fun nowPlayingTracker(): NowPlayingTracker
}

private fun entryPoint(context: Context): NowPlayingEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext, NowPlayingEntryPoint::class.java
    )

@Composable
fun rememberMediaControlHub(): MediaControlHub {
    val context = LocalContext.current
    return remember(context) { entryPoint(context).mediaControlHub() }
}

@Composable
fun rememberNowPlayingTracker(): NowPlayingTracker {
    val context = LocalContext.current
    return remember(context) { entryPoint(context).nowPlayingTracker() }
}

/** True when the user has granted the notification access the media session needs. */
fun hasNotificationAccess(context: Context): Boolean = runCatching {
    android.provider.Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ).orEmpty().contains(context.packageName)
}.getOrDefault(false)
