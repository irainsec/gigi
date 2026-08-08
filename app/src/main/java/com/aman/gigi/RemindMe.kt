package com.aman.gigi

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltAndroidApp
class RemindMe : Application() {
    @javax.inject.Inject
    lateinit var scribbleSyncManager: com.aman.gigi.data.sync.ScribbleSyncManager

    @javax.inject.Inject
    lateinit var bootstrapManager: com.aman.gigi.data.client.ConnectionBootstrapManager

    @javax.inject.Inject
    lateinit var themeSongPlayer: com.aman.gigi.service.ThemeSongPlayer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        bootstrapManager.start()

        appScope.launch {
            bootstrapManager.memberIdentity.collectLatest { identity ->
                themeSongPlayer.playIfNeeded(identity)
            }
        }
        
        // Monitor app lifecycle for auto-reconnect
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        // App came to foreground - trigger reconnect
                        android.util.Log.i("RemindMe", "🟢 App foregrounded - triggering auto-reconnect")
                        bootstrapManager.onAppForegrounded()
                        scribbleSyncManager.onAppForegrounded()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        // App went to background
                        android.util.Log.i("RemindMe", "⚫ App backgrounded")
                        themeSongPlayer.stop()
                    }
                    else -> {}
                }
            }
        })
    }

    companion object {
        private lateinit var instance: RemindMe
    }
}
