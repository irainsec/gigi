package com.aman.gigi.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.aman.gigi.model.ConnectionState
import com.aman.gigi.repository.ConnectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for screensaver lifecycle and sync service
 */
@Singleton
class ScreensaverManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository
) {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val TAG = "ScreensaverManager"
    private var isServiceRunning = false
    
    /**
     * Initialize manager - observe connection state
     */
    fun initialize() {
        // Start sync service immediately on app launch for presence tracking
        startSyncService()
        
        scope.launch {
            connectionRepository.getConnectionState().collectLatest { state ->
                // Still keep this for state management, but service is now always running
                if (state == ConnectionState.CONNECTED) {
                    // startSyncService() - Already started
                } else {
                    // stopSyncService() - No longer stopping on disconnect to maintain presence
                }
            }
        }
    }
    
    /**
     * Start sync service
     */
    fun startSyncService(force: Boolean = false) {
        if (isServiceRunning && !force) {
            Log.d(TAG, "Sync service already running")
            return
        }
        
        Log.d(TAG, "Starting sync service")
        val intent = Intent(context, ScreensaverSyncService::class.java).apply {
            action = ScreensaverSyncService.ACTION_START_SYNC
        }
        
        try {
            ContextCompat.startForegroundService(context, intent)
            isServiceRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start sync service", e)
        }
    }
    
    /**
     * Stop sync service
     */
    private fun stopSyncService() {
        if (!isServiceRunning) {
            Log.d(TAG, "Sync service not running")
            return
        }
        
        Log.d(TAG, "Stopping sync service")
        val intent = Intent(context, ScreensaverSyncService::class.java).apply {
            action = ScreensaverSyncService.ACTION_STOP_SYNC
        }
        
        try {
            context.startService(intent)
            isServiceRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop sync service", e)
        }
    }
}
