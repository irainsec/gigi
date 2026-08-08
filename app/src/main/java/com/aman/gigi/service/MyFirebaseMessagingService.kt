package com.aman.gigi.service

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.aman.gigi.data.sync.ScribbleSyncManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var syncManager: ScribbleSyncManager

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "🔔 [GIGI-FCM] Push notification received from: ${remoteMessage.from}")

        // Acquire a 10-second WakeLock to wake CPU and ensure closed app delivers the message
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Gigi:FcmWakeLock"
        )
        wakeLock?.acquire(10_000L)

        try {
            if (remoteMessage.data.isNotEmpty()) {
                val data = remoteMessage.data
                Log.d(TAG, "🔔 [GIGI-FCM] Message data payload: $data")

                val type = data["type"] ?: data["actionType"] ?: ""
                val connectionId = data["connectionId"] ?: data["connectionCode"] ?: ""

                when (type) {
                    "scribble", "doodle", "sparkle" -> {
                        val assetRef = data["assetRef"] ?: data["filePath"] ?: data["mediaUrl"] ?: ""
                        val scribbleId = data["scribbleId"] ?: data["scribble_id"] ?: data["messageId"] ?: data["id"] ?: ""
                        Log.i(TAG, "🎨 [GIGI-FCM] Incoming doodle push for connection: $connectionId asset: $assetRef id: $scribbleId")
                        if (connectionId.isNotBlank() && (assetRef.isNotBlank() || scribbleId.isNotBlank())) {
                            syncManager.deliverScribbleFromPush(
                                connectionId = connectionId,
                                assetRef = assetRef,
                                scribbleId = scribbleId,
                                actionType = type
                            )
                        }
                    }
                    "chat_message" -> {
                        Log.i(TAG, "💬 [GIGI-FCM] Incoming chat push for connection: $connectionId")
                        syncManager.deliverChatFromPush(
                            connectionId = connectionId,
                            senderName = data["senderName"] ?: "Partner",
                            msgType = data["msgType"] ?: "text",
                            text = data["text"] ?: "",
                            gifUrl = data["gifUrl"] ?: "",
                            clientMsgId = data["clientMsgId"] ?: ""
                        )
                    }
                    else -> {
                        Log.d(TAG, "🔔 [GIGI-FCM] Generic push type '$type' — triggering foreground sync reconnect")
                        syncManager.onAppForegrounded()
                    }
                }
            }

            remoteMessage.notification?.let {
                Log.d(TAG, "🔔 [GIGI-FCM] Notification Body: ${it.body}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling FCM push message", e)
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "🔔 [GIGI-FCM] Refreshed token: $token")
        syncManager.updateFcmTokenGlobal(token)
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
