import re

with open('app/src/main/java/com/aman/gigi/data/client/ConnectionBootstrapManager.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add model import
if "import com.aman.gigi.model.BreakCardConfig" not in content:
    content = content.replace(
        "import com.aman.gigi.model.ServerStatus",
        "import com.aman.gigi.model.ServerStatus\nimport com.aman.gigi.model.BreakCardConfig"
    )

# 2. Add StateFlow
state_flow_code = """
    private val _breakCards = MutableStateFlow<List<BreakCardConfig>>(emptyList())
    val breakCards: StateFlow<List<BreakCardConfig>> = _breakCards.asStateFlow()
"""
if "val breakCards: StateFlow<List<BreakCardConfig>>" not in content:
    content = content.replace(
        "val devOtpHint: StateFlow<String?> = _devOtpHint.asStateFlow()",
        "val devOtpHint: StateFlow<String?> = _devOtpHint.asStateFlow()" + state_flow_code
    )

# 3. Add fetch logic in refreshBootstrap or initialization
fetch_code = """
    /** Fetches the available Break Cards from the server. */
    suspend fun fetchBreakCardConfigs() {
        val token = _memberIdentity.value?.authToken?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            val response = requestJson(
                path = "/api/client/break-cards",
                method = "POST",
                body = JSONObject(),
                token = token
            )
            if (response.code in 200..299) {
                val jsonBody = JSONObject(response.body)
                val cardsArr = jsonBody.optJSONArray("cards")
                if (cardsArr != null) {
                    val cards = mutableListOf<BreakCardConfig>()
                    for (i in 0 until cardsArr.length()) {
                        val c = cardsArr.optJSONObject(i) ?: continue
                        cards.add(BreakCardConfig(
                            cardId = c.optString("id"),
                            name = c.optString("name"),
                            animatedSvgUrl = resolveServerAssetUrl(c.optString("animatedSvgUrl"))
                        ))
                    }
                    _breakCards.value = cards
                    return
                }
            }
        }
        // Fallback if API fails or isn't ready
        _breakCards.value = listOf(
            BreakCardConfig("tea_break", "Tea Break", null),
            BreakCardConfig("coffee_break", "Coffee Break", null),
            BreakCardConfig("sutta_break", "Sutta Break", null),
            BreakCardConfig("stretch_break", "Stretch Break", null)
        )
    }
"""

if "fun fetchBreakCardConfigs" not in content:
    # insert before companion object or at end
    if "companion object {" in content:
        content = content.replace("companion object {", fetch_code + "\n    companion object {")
    else:
        content = content.replace("\n}", fetch_code + "\n}")

with open('app/src/main/java/com/aman/gigi/data/client/ConnectionBootstrapManager.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated ConnectionBootstrapManager.kt")
