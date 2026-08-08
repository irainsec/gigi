import re

with open('app/src/main/java/com/aman/gigi/data/sync/ScribbleSyncManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add model imports
if "import com.aman.gigi.model.BreakCardSessionMirror" not in content:
    content = content.replace(
        "import com.aman.gigi.model.OutboundAction",
        "import com.aman.gigi.model.OutboundAction\nimport com.aman.gigi.model.BreakCardSessionMirror\nimport com.aman.gigi.data.dao.BreakCardDao"
    )

# 2. Add BreakCardDao to constructor
if "private val breakCardDao: BreakCardDao" not in content:
    content = content.replace(
        "private val sharedAlbumStore: com.aman.gigi.data.music.SharedAlbumStore",
        "private val sharedAlbumStore: com.aman.gigi.data.music.SharedAlbumStore,\n    private val breakCardDao: BreakCardDao"
    )

# 3. Add sending logic
sending_code = """
    // --------------------------------------------------------------------------- Break Cards ---------------------------------------------------------------------------
    fun sendBreakCard(connectionId: String, cardId: String, cardName: String, animatedSvgUrl: String?) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val sessionId = java.util.UUID.randomUUID().toString()
            val session = BreakCardSessionMirror(
                sessionId = sessionId,
                cardId = cardId,
                cardName = cardName,
                connectionId = connectionId,
                senderDeviceId = deviceId,
                senderName = bootstrapManager.memberIdentity.value?.displayName ?: "A friend",
                animatedSvgUrl = animatedSvgUrl
            )
            // Save locally
            breakCardDao.deactivateAllForConnection(connectionId)
            breakCardDao.insertOrUpdate(session)

            // Send via network
            val payload = org.json.JSONObject().apply {
                put("type", "break_card_sent")
                put("sessionId", sessionId)
                put("cardId", cardId)
                put("cardName", cardName)
                put("animatedSvgUrl", animatedSvgUrl ?: "")
            }
            sendEnvelope(connectionId, SyncProtocol.Envelope(
                type = SyncProtocol.Type.REMOTE_COMMAND,
                senderDeviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                payloadJson = payload.toString()
            ))
        }
    }

    fun sendBreakCardResponse(connectionId: String, sessionId: String, response: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val session = breakCardDao.getSessionById(sessionId) ?: return@launch
            val responses = org.json.JSONObject(session.responsesJson)
            responses.put(deviceId, response)
            breakCardDao.insertOrUpdate(session.copy(
                responsesJson = responses.toString(),
                updatedAt = System.currentTimeMillis()
            ))
            
            val payload = org.json.JSONObject().apply {
                put("type", "break_card_response")
                put("sessionId", sessionId)
                put("response", response)
            }
            sendEnvelope(connectionId, SyncProtocol.Envelope(
                type = SyncProtocol.Type.REMOTE_COMMAND,
                senderDeviceId = deviceId,
                timestamp = System.currentTimeMillis(),
                payloadJson = payload.toString()
            ))
        }
    }
"""

if "fun sendBreakCard(" not in content:
    content = content.replace(
        "    // --------------------------------------------------------------------------- Chat ---------------------------------------------------------------------------",
        sending_code + "\n    // --------------------------------------------------------------------------- Chat ---------------------------------------------------------------------------"
    )

# 4. Add handlers to handleRemoteCommand
handlers_code = """
            "break_card_sent" -> {
                val sessionId = json.optString("sessionId")
                val cardId = json.optString("cardId")
                val cardName = json.optString("cardName")
                val animatedSvgUrl = json.optString("animatedSvgUrl").takeIf { it.isNotBlank() }
                val senderName = connectionRepository.getMembersForConnection(connectionId)
                    .find { it.memberDeviceId == senderDeviceId }?.memberName ?: "A friend"

                val session = BreakCardSessionMirror(
                    sessionId = sessionId,
                    cardId = cardId,
                    cardName = cardName,
                    connectionId = connectionId,
                    senderDeviceId = senderDeviceId,
                    senderName = senderName,
                    animatedSvgUrl = animatedSvgUrl
                )
                breakCardDao.deactivateAllForConnection(connectionId)
                breakCardDao.insertOrUpdate(session)
                com.aman.gigi.utils.NotificationHelper(context).showLoveCardNotification(senderName, "Sent a break card: $cardName", connectionId, false)
            }
            "break_card_response" -> {
                val sessionId = json.optString("sessionId")
                val response = json.optString("response")
                val session = breakCardDao.getSessionById(sessionId)
                if (session != null) {
                    val responses = org.json.JSONObject(session.responsesJson)
                    responses.put(senderDeviceId, response)
                    breakCardDao.insertOrUpdate(session.copy(
                        responsesJson = responses.toString(),
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
"""

if '"break_card_sent" -> {' not in content:
    content = content.replace(
        '"love_card_answered" -> handleLoveCardAnswered(json, connectionId)',
        '"love_card_answered" -> handleLoveCardAnswered(json, connectionId)' + handlers_code
    )

with open('app/src/main/java/com/aman/gigi/data/sync/ScribbleSyncManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated ScribbleSyncManager.kt")
