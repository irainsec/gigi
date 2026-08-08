package com.aman.gigi.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.aman.gigi.data.sync.ScribbleSyncManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Background service for scribble synchronization
 */
@AndroidEntryPoint
class ScribbleSyncService : Service() {
    
    @Inject
    lateinit var syncManager: ScribbleSyncManager
    
    private val TAG = "ScribbleSyncService"
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Start sync manager
        syncManager.start()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        // Stop sync manager
        syncManager.stop()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
