const fs = require('fs');
const filePath = 'c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt';
let content = fs.readFileSync(filePath, 'utf8');

const updates = [
    {
        name: 'refreshNotificationApps',
        pattern: /fun\s+refreshNotificationApps\(\)\s*\{[\s\S]+?val\s+connId\s*=\s*_partnerConnectionId\.value\s*\?:\s*return[\s\S]+?syncManager\.sendGetNotificationApps\(connId\)\s+\}/,
        replacement: `fun refreshNotificationApps() {
        val conn = selectedConnection.value ?: return
        syncManager.sendRemoteCommand(conn.connectionId, "get_notification_apps", conn.partnerDeviceId)
    }`
    },
    {
        name: 'requestPartnerLocation',
        pattern: /fun\s+requestPartnerLocation\(\)\s*\{[\s\S]+?_partnerConnectionId\.value\?\.let\s*\{\s*connectionId\s*->[\s\S]+?syncManager\.sendRemoteCommand\(connectionId,\s*com\.aman\.gigi\.utils\.Constants\.COMMAND_GET_LOCATION\)\s+\}\s+\}/,
        replacement: `fun requestPartnerLocation() {
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommand(conn.connectionId, com.aman.gigi.utils.Constants.COMMAND_GET_LOCATION, conn.partnerDeviceId)
        }
    }`
    },
    {
        name: 'stopMirroringStream',
        pattern: /\/\/ Also send stop command to partner if we are the creator and stopping a "watch" session[\s\S]+?_partnerConnectionId\.value\?\.let\s*\{\s*connId\s*->[\s\S]+?syncManager\.sendRemoteCommand\(connId,\s*com\.aman\.gigi\.utils\.Constants\.COMMAND_STOP_MIRROR\)\s+\}/,
        replacement: `// Also send stop command to partner if we are the creator and stopping a "watch" session
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommand(conn.connectionId, com.aman.gigi.utils.Constants.COMMAND_STOP_MIRROR, conn.partnerDeviceId)
        }`
    }
];

updates.forEach(update => {
    if (update.pattern.test(content)) {
        content = content.replace(update.pattern, update.replacement);
        console.log(`✅ Patched ${update.name}`);
    } else {
        console.error(`❌ Failed to patch ${update.name}`);
    }
});

// Add updateScreensaver if missing
if (!content.includes('fun updateScreensaver')) {
    const searchString = 'fun setViewingNotifications(viewing: Boolean) {';
    const insertIndex = content.indexOf(searchString);
    if (insertIndex !== -1) {
        const screensaverFunction = `/**
     * Sets the screensaver background image for the selected partner.
     */
    fun updateScreensaver(imageUrl: String) {
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommandWithData(
                conn.connectionId,
                "set_background",
                org.json.JSONObject().apply { put("imageUrl", imageUrl) },
                conn.partnerDeviceId
            )
            android.util.Log.i("ScreensaverViewModel", "🖼️ Sent background image to partner \${conn.connectionId}")
        }
    }

    `;
        content = content.slice(0, insertIndex) + screensaverFunction + content.slice(insertIndex);
        console.log('✅ Added updateScreensaver');
    } else {
        console.error('❌ Could not find insertion point for updateScreensaver');
    }
}

fs.writeFileSync(filePath, content);
