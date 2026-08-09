package com.aman.gigi.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlanFeatures(
    val gifPicker: Boolean = false,
    val premiumBrushes: Boolean = false,
    val timeCapsule: Boolean = false,
    val animatedCards: Boolean = false,
    val groupConnections: Boolean = false,
    val customTheme: Boolean = false,
    val allThemes: Boolean = false,
    val recurringAlarms: Boolean = false,
    val tabReminders: Boolean = true,
    val tabLive: Boolean = true,
    val tabSweetCorner: Boolean = true,
    val tabMusic: Boolean = true
)

data class UserPlan(
    val tier: String = "free",
    val expiresAt: String? = null,
    val maxConnections: Int = 2,
    val maxGroupMembers: Int = 0,
    val maxStrokes: Int = 50,
    val maxReminders: Int = 5,
    val maxCardsPerStack: Int = 1,
    val historyDays: Int = 3,
    val features: PlanFeatures = PlanFeatures()
) {
    val isPro: Boolean get() = tier == "pro"
    val isPlus: Boolean get() = tier == "plus" || isPro
    val isFree: Boolean get() = tier == "free"
    val upgradeTarget: String get() = if (isFree) "Plus" else "Pro"
}

object AppConfig {
    @Volatile var giphyApiKey: String? = null
    @Volatile var upgradeUrl: String = "https://gigi.iamanraj.com/upgrade"

    private val _planFlow = MutableStateFlow(UserPlan())
    val planFlow: StateFlow<UserPlan> = _planFlow.asStateFlow()
    val userPlan: UserPlan get() = _planFlow.value

    fun applyServerConfig(
        giphyKey: String? = null,
        plan: UserPlan? = null,
        upgradeUrl: String? = null
    ) {
        giphyKey?.takeIf { it.isNotBlank() }?.let { giphyApiKey = it }
        upgradeUrl?.takeIf { it.isNotBlank() }?.let { this.upgradeUrl = it }
        plan?.let { _planFlow.value = it }
    }

    /** Parses an `appConfig` JSON block (from bootstrap or a live plan_update) and applies it. */
    fun applyServerConfigJson(cfg: org.json.JSONObject?) {
        cfg ?: return
        val giphyKey = cfg.optString("giphyApiKey").takeIf { it.isNotBlank() }
        val url = cfg.optString("upgradeUrl").takeIf { it.isNotBlank() }
        val plan = cfg.optJSONObject("plan")?.let { p ->
            val f = p.optJSONObject("features")
            UserPlan(
                tier = p.optString("tier", "free"),
                expiresAt = p.optString("expiresAt").takeIf { it.isNotBlank() },
                maxConnections = p.optInt("maxConnections", 2),
                maxGroupMembers = p.optInt("maxGroupMembers", 0),
                maxStrokes = p.optInt("maxStrokes", 50),
                maxReminders = p.optInt("maxReminders", 5),
                maxCardsPerStack = p.optInt("maxCardsPerStack", 1),
                historyDays = p.optInt("historyDays", 3),
                features = PlanFeatures(
                    gifPicker = f?.optBoolean("gifPicker", false) ?: false,
                    premiumBrushes = f?.optBoolean("premiumBrushes", false) ?: false,
                    timeCapsule = f?.optBoolean("timeCapsule", false) ?: false,
                    animatedCards = f?.optBoolean("animatedCards", false) ?: false,
                    groupConnections = f?.optBoolean("groupConnections", false) ?: false,
                    customTheme = f?.optBoolean("customTheme", false) ?: false,
                    allThemes = f?.optBoolean("allThemes", false) ?: false,
                    recurringAlarms = f?.optBoolean("recurringAlarms", false) ?: false,
                    tabReminders = f?.optBoolean("tabReminders", true) ?: true,
                    tabLive = f?.optBoolean("tabLive", true) ?: true,
                    tabSweetCorner = f?.optBoolean("tabSweetCorner", true) ?: true,
                    tabMusic = f?.optBoolean("tabMusic", true) ?: true
                )
            )
        }
        applyServerConfig(giphyKey, plan, url)
    }
}

