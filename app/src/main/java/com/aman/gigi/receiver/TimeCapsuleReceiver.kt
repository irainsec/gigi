package com.aman.gigi.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aman.gigi.utils.NotificationHelper

class TimeCapsuleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stackId = intent.getStringExtra("EXTRA_STACK_ID") ?: return
        val partnerName = intent.getStringExtra("EXTRA_PARTNER_NAME") ?: "Your partner"
        
        NotificationHelper.showLoveCardNotification(
            context = context,
            partnerName = partnerName,
            stackId = stackId,
            isTimeCapsuleUnlock = true
        )
    }
}
