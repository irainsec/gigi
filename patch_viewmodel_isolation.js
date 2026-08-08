const fs = require('fs');
const filePath = 'c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt';
let content = fs.readFileSync(filePath, 'utf8');

const updates = [
    {
        name: 'requestPhotoList',
        pattern: /fun\s+requestPhotoList\(\)\s*\{[\s\S]+?_isFetchingPhotos\.value\s*=\s*true[\s\S]+?syncManager\.sendRemoteCommand\(connectionId, com\.aman\.gigi\.utils\.Constants\.COMMAND_GET_PHOTO_LIST\)\s+\}/,
        replacement: `fun requestPhotoList() {
        val conn = selectedConnection.value
        if (conn != null) {
            _isFetchingPhotos.value = true
            syncManager.sendRemoteCommand(conn.connectionId, com.aman.gigi.utils.Constants.COMMAND_GET_PHOTO_LIST, conn.partnerDeviceId)
        }
    }`
    },
    {
        name: 'requestPhotoDownload',
        pattern: /fun\s+requestPhotoDownload\(photoId: String\)\s*\{[\s\S]+?syncManager\.sendRemoteCommandWithData\([\s\S]+?connectionId,[\s\S]+?com\.aman\.gigi\.utils\.Constants\.COMMAND_GET_FULL_PHOTO,[\s\S]+?org\.json\.JSONObject\(\)\.apply\s*\{\s*put\("photoId",\s*photoId\)\s*\}\s+\)\s+\}\s+\}/,
        replacement: `fun requestPhotoDownload(photoId: String) {
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommandWithData(
                conn.connectionId, 
                com.aman.gigi.utils.Constants.COMMAND_GET_FULL_PHOTO,
                org.json.JSONObject().apply { put("photoId", photoId) },
                conn.partnerDeviceId
            )
        }
    }`
    },
    {
        name: 'requestRemotePhoto',
        pattern: /fun\s+requestRemotePhoto\(\)\s*\{[\s\S]+?syncManager\.sendRemoteCommandWithData\([\s\S]+?connectionId,[\s\S]+?com\.aman\.gigi\.utils\.Constants\.COMMAND_TAKE_REMOTE_PHOTO,[\s\S]+?org\.json\.JSONObject\(\)\.apply\s*\{\s*put\("cameraType",\s*_selectedCamera\.value\)\s*\}\s+\)[\s\S]+?android\.util\.Log\.i\("ScreensaverViewModel",\s*"📸 Requested remote photo \(\$\{_selectedCamera\.value\}\) from \$connectionId"\)\s+\}\s+\}/,
        replacement: `fun requestRemotePhoto() {
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommandWithData(
                conn.connectionId, 
                com.aman.gigi.utils.Constants.COMMAND_TAKE_REMOTE_PHOTO,
                org.json.JSONObject().apply { put("cameraType", _selectedCamera.value) },
                conn.partnerDeviceId
            )
            android.util.Log.i("ScreensaverViewModel", "📸 Requested remote photo (\${_selectedCamera.value}) from \${conn.connectionId}")
        }
    }`
    },
    {
        name: 'startLiveCamera',
        pattern: /fun\s+startLiveCamera\(\)\s*\{[\s\S]+?syncManager\.sendRemoteCommandWithData\([\s\S]+?connectionId,[\s\S]+?com\.aman\.gigi\.utils\.Constants\.COMMAND_START_LIVE_CAMERA,[\s\S]+?org\.json\.JSONObject\(\)\.apply\s*\{\s*put\("cameraType",\s*_selectedCamera\.value\)\s*\}\s+\)[\s\S]+?_isLiveCameraActive\.value\s*=\s*true\s+\}\s+\}/,
        replacement: `fun startLiveCamera() {
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommandWithData(
                conn.connectionId,
                com.aman.gigi.utils.Constants.COMMAND_START_LIVE_CAMERA,
                org.json.JSONObject().apply { put("cameraType", _selectedCamera.value) },
                conn.partnerDeviceId
            )
            _isLiveCameraActive.value = true
        }
    }`
    },
    {
        name: 'stopLiveCamera',
        pattern: /fun\s+stopLiveCamera\(\)\s*\{[\s\S]+?syncManager\.sendRemoteCommand\(connectionId, com\.aman\.gigi\.utils\.Constants\.COMMAND_STOP_LIVE_CAMERA\)[\s\S]+?_isLiveCameraActive\.value\s*=\s*false[\s\S]+?_liveCameraFrame\.value\s*=\s*null\s+\}\s+\}/,
        replacement: `fun stopLiveCamera() {
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommand(conn.connectionId, com.aman.gigi.utils.Constants.COMMAND_STOP_LIVE_CAMERA, conn.partnerDeviceId)
            _isLiveCameraActive.value = false
            _liveCameraFrame.value = null
        }
    }`
    },
    {
        name: 'requestRemoteAudio',
        pattern: /fun\s+requestRemoteAudio\(durationMs: Long = 10000L\)\s*\{[\s\S]+?syncManager\.sendRemoteCommandWithData\([\s\S]+?connectionId,[\s\S]+?com\.aman\.gigi\.utils\.Constants\.COMMAND_RECORD_AUDIO,[\s\S]+?org\.json\.JSONObject\(\)\.apply\s*\{\s*put\("duration",\s*durationMs\)\s*\}\s+\)[\s\S]+?android\.util\.Log\.i\("ScreensaverViewModel",\s*"🎤 Requested remote audio \(\$durationMs ms\) from \$connectionId"\)\s+\}\s+\}/,
        replacement: `fun requestRemoteAudio(durationMs: Long = 10000L) {
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommandWithData(
                conn.connectionId,
                com.aman.gigi.utils.Constants.COMMAND_RECORD_AUDIO,
                org.json.JSONObject().apply { put("duration", durationMs) },
                conn.partnerDeviceId
            )
            android.util.Log.i("ScreensaverViewModel", "🎤 Requested remote audio ($durationMs ms) from \${conn.connectionId}")
        }
    }`
    },
    {
        name: 'browseRemoteDirectory',
        pattern: /fun\s+browseRemoteDirectory\(path: String\? = null\)\s*\{[\s\S]+?_isFetchingFiles\.value\s*=\s*true[\s\S]+?syncManager\.sendRemoteCommandWithData\([\s\S]+?connectionId,[\s\S]+?com\.aman\.gigi\.utils\.Constants\.COMMAND_GET_FILE_LIST,[\s\S]+?org\.json\.JSONObject\(\)\.apply\s*\{[\s\S]+?path\?\.let\s*\{\s*put\("path",\s*it\)\s*\}[\s\S]+?\}\s+\)\s+\}\s+\}/,
        replacement: `fun browseRemoteDirectory(path: String? = null) {
        val conn = selectedConnection.value
        if (conn != null) {
            _isFetchingFiles.value = true
            syncManager.sendRemoteCommandWithData(
                conn.connectionId,
                com.aman.gigi.utils.Constants.COMMAND_GET_FILE_LIST,
                org.json.JSONObject().apply { path?.let { put("path", it) } },
                conn.partnerDeviceId
            )
        }
    }`
    },
    {
        name: 'requestFileDownload',
        pattern: /fun\s+requestFileDownload\(path: String\)\s*\{[\s\S]+?syncManager\.sendRemoteCommandWithData\([\s\S]+?connectionId,[\s\S]+?com\.aman\.gigi\.utils\.Constants\.COMMAND_GET_FILE_DATA,[\s\S]+?org\.json\.JSONObject\(\)\.apply\s*\{\s*put\("path",\s*path\)\s*\}\s+\)\s+\}\s+\}/,
        replacement: `fun requestFileDownload(path: String) {
        val conn = selectedConnection.value
        if (conn != null) {
            syncManager.sendRemoteCommandWithData(
                conn.connectionId,
                com.aman.gigi.utils.Constants.COMMAND_GET_FILE_DATA,
                org.json.JSONObject().apply { put("path", path) },
                conn.partnerDeviceId
            )
        }
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

fs.writeFileSync(filePath, content);
