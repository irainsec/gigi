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
    val liveTracking: Boolean = true,
    val tabReminders: Boolean = true,
    val tabLive: Boolean = true,
    val tabSweetCorner: Boolean = true,
    val tabNest: Boolean = true,
    val tabMusic: Boolean = true
)

/** A tier the server says we may sell, with the copy and price it wants shown. */
data class UpgradeOption(
    val tierId: String,
    val displayName: String,
    val emoji: String,
    val tagline: String,
    val priceLabel: String,
    val productId: String
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
    val maxLivePosts: Int = 1,
    val features: PlanFeatures = PlanFeatures(),
    /**
     * What the app may offer as an upgrade, straight from the server's tier list.
     * The names, prices and product ids used to be hardcoded here, which meant
     * deleting a tier in the admin panel left the app still advertising it.
     */
    val upgradeOptions: List<UpgradeOption> = emptyList(),
    /** False when the admin has switched everything to free — hide all upsell. */
    val monetizationEnabled: Boolean = true
) {
    val isFree: Boolean get() = tier == "free"
    /** On any tier above free. Tier-agnostic — no hardcoded "plus"/"pro" names. */
    val isPaid: Boolean get() = tier != "free"
    /** The cheapest tier above this one, or null when there's nothing to sell. */
    val nextUpgrade: UpgradeOption? get() = upgradeOptions.firstOrNull()
    val canUpgrade: Boolean get() = monetizationEnabled && upgradeOptions.isNotEmpty()
    val upgradeTarget: String get() = nextUpgrade?.displayName ?: "Gigi"
}

/**
 * Settings the admin panel can change without shipping a build. Defaults match the
 * server's, so the app behaves sensibly if it can't reach it.
 */
data class RemoteSettings(
    val osmTileUrl: String = "https://gigi.iamanraj.com/tiles/{z}/{x}/{y}.png",
    /**
     * Blank until a Spotify app is registered, and blank is meaningful: it hides the
     * whole Spotify surface rather than showing a Connect button that cannot work.
     * Set from the admin panel so it needs no app release.
     */
    val spotifyClientId: String = "",
    val minSupportedVersionCode: Int = 0,
    val forceUpdate: Boolean = false,
    val maintenanceMode: Boolean = false,
    val announcement: String = "",
    val killReminders: Boolean = false,
    val killLive: Boolean = false,
    val killSweetCorner: Boolean = false,
    val killNest: Boolean = false,
    val killMusic: Boolean = false,
    val killLiveTracking: Boolean = false,
    val killCosmicNebula: Boolean = false,
    val supportEmail: String = "aman.raj@alticyber.com",
    val privacyUrl: String = "https://gigi.iamanraj.com/privacy"
)

object AppConfig {
    @Volatile var giphyApiKey: String? = null
    @Volatile var upgradeUrl: String = "https://gigi.iamanraj.com/upgrade"

    private val _planFlow = MutableStateFlow(UserPlan())
    val planFlow: StateFlow<UserPlan> = _planFlow.asStateFlow()
    val userPlan: UserPlan get() = _planFlow.value

    private val _settingsFlow = MutableStateFlow(RemoteSettings())
    val settingsFlow: StateFlow<RemoteSettings> = _settingsFlow.asStateFlow()
    val settings: RemoteSettings get() = _settingsFlow.value

    /** Parses the `settings` block from bootstrap or a live `app_settings_update`. */
    fun applySettingsJson(s: org.json.JSONObject?) {
        s ?: return
        val cur = _settingsFlow.value
        _settingsFlow.value = RemoteSettings(
            osmTileUrl = s.optString("osmTileUrl").ifBlank { cur.osmTileUrl },
            spotifyClientId = s.optString("spotifyClientId").ifBlank { cur.spotifyClientId },
            minSupportedVersionCode = s.optInt("minSupportedVersionCode", cur.minSupportedVersionCode),
            forceUpdate = s.optBoolean("forceUpdate", cur.forceUpdate),
            maintenanceMode = s.optBoolean("maintenanceMode", cur.maintenanceMode),
            announcement = s.optString("announcement", cur.announcement),
            killReminders = s.optBoolean("killReminders", cur.killReminders),
            killLive = s.optBoolean("killLive", cur.killLive),
            killSweetCorner = s.optBoolean("killSweetCorner", cur.killSweetCorner),
            killNest = s.optBoolean("killNest", cur.killNest),
            killMusic = s.optBoolean("killMusic", cur.killMusic),
            killLiveTracking = s.optBoolean("killLiveTracking", cur.killLiveTracking),
            killCosmicNebula = s.optBoolean("killCosmicNebula", cur.killCosmicNebula),
            supportEmail = s.optString("supportEmail").ifBlank { cur.supportEmail },
            privacyUrl = s.optString("privacyUrl").ifBlank { cur.privacyUrl }
        )
    }

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
        applySettingsJson(cfg.optJSONObject("settings"))
        val options = cfg.optJSONArray("upgradeOptions")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    UpgradeOption(
                        tierId = o.optString("tierId"),
                        displayName = o.optString("displayName").ifBlank { o.optString("tierId") },
                        emoji = o.optString("emoji").ifBlank { "✨" },
                        tagline = o.optString("tagline"),
                        priceLabel = o.optString("priceLabel"),
                        productId = o.optString("productId")
                    )
                }?.takeIf { it.productId.isNotBlank() }
            }
        } ?: emptyList()
        val monetization = cfg.optBoolean("monetizationEnabled", true)

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
                maxLivePosts = p.optInt("maxLivePosts", 1),
                features = PlanFeatures(
                    gifPicker = f?.optBoolean("gifPicker", false) ?: false,
                    premiumBrushes = f?.optBoolean("premiumBrushes", false) ?: false,
                    timeCapsule = f?.optBoolean("timeCapsule", false) ?: false,
                    animatedCards = f?.optBoolean("animatedCards", false) ?: false,
                    groupConnections = f?.optBoolean("groupConnections", false) ?: false,
                    customTheme = f?.optBoolean("customTheme", false) ?: false,
                    allThemes = f?.optBoolean("allThemes", false) ?: false,
                    recurringAlarms = f?.optBoolean("recurringAlarms", false) ?: false,
                    liveTracking = f?.optBoolean("liveTracking", true) ?: true,
                    tabReminders = f?.optBoolean("tabReminders", true) ?: true,
                    tabLive = f?.optBoolean("tabLive", true) ?: true,
                    tabSweetCorner = f?.optBoolean("tabSweetCorner", true) ?: true,
                    tabMusic = f?.optBoolean("tabMusic", true) ?: true
                ),
                upgradeOptions = options,
                monetizationEnabled = monetization
            )
        }
        applyServerConfig(giphyKey, plan, url)
    }
}


