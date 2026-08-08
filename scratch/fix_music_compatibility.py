import os

sync_manager_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/data/sync/ScribbleSyncManager.kt"
with open(sync_manager_path, 'r', encoding='utf-8') as f:
    content = f.read()

target = """            com.aman.gigi.utils.Constants.COMMAND_EXCHANGE_MUSIC_HISTORY -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val partnerHistoryArray = it.optJSONArray("history")
                    val partnerHistory = mutableSetOf<String>()
                    if (partnerHistoryArray != null) {
                        for (i in 0 until partnerHistoryArray.length()) {
                            partnerHistory.add(partnerHistoryArray.getString(i))
                        }
                    }
                    val prefs = context.getSharedPreferences("gigi_music_prefs", android.content.Context.MODE_PRIVATE)
                    val localHistory = prefs.getStringSet("listened_artists", emptySet()) ?: emptySet()
                    val intersection = localHistory.intersect(partnerHistory).size
                    val union = localHistory.union(partnerHistory).size
                    val score = if (union == 0) 0 else ((intersection.toFloat() / union) * 100).toInt().coerceIn(0, 100)
                    
                    scope.launch {
                        _events.emit(SyncEvent.MusicCompatibilityReceived(connectionId, score))
                    }
                }
            }"""

replacement = """            com.aman.gigi.utils.Constants.COMMAND_EXCHANGE_MUSIC_HISTORY -> {
                val data = json.optJSONObject("data")
                data?.let {
                    val partnerHistoryArray = it.optJSONArray("history")
                    val partnerHistory = mutableSetOf<String>()
                    if (partnerHistoryArray != null) {
                        for (i in 0 until partnerHistoryArray.length()) {
                            partnerHistory.add(partnerHistoryArray.getString(i))
                        }
                    }
                    val prefs = context.getSharedPreferences("gigi_music_prefs", android.content.Context.MODE_PRIVATE)
                    val localHistory = prefs.getStringSet("listened_artists", emptySet()) ?: emptySet()
                    val intersection = localHistory.intersect(partnerHistory).size
                    val union = localHistory.union(partnerHistory).size
                    val score = if (union == 0) 0 else ((intersection.toFloat() / union) * 100).toInt().coerceIn(0, 100)
                    
                    scope.launch {
                        _events.emit(SyncEvent.MusicCompatibilityReceived(connectionId, score))
                    }
                    
                    val isReply = it.optBoolean("isReply", false)
                    if (!isReply) {
                        val replyPayload = org.json.JSONObject().apply {
                            put("history", org.json.JSONArray(localHistory.toList()))
                            put("isReply", true)
                        }
                        scope.launch {
                            sendRemoteCommandWithData(connectionId, com.aman.gigi.utils.Constants.COMMAND_EXCHANGE_MUSIC_HISTORY, replyPayload)
                        }
                    }
                }
            }"""

if target in content:
    content = content.replace(target, replacement)
    with open(sync_manager_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Success: Updated ScribbleSyncManager.kt")
else:
    print("Error: Target not found in ScribbleSyncManager.kt")
