package com.aman.gigi.ui.components

import android.app.Activity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aman.gigi.utils.AppConfig
import com.aman.gigi.utils.BillingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeSheet(
    featureName: String,
    featureDescription: String,
    onDismiss: () -> Unit
) {
    val plan by AppConfig.planFlow.collectAsState()
    val context = LocalContext.current
    // Everything about the offer now comes from the server's tier list: which tiers
    // are sellable, what they're called, what they cost, and their product ids. The
    // app used to hardcode "Plus"/"Pro" and two product ids, so a tier deleted in the
    // admin panel was still advertised here.
    val offer = plan.nextUpgrade
    val isUpgradable = plan.canUpgrade && offer != null
    val targetTier = offer?.displayName ?: ""
    val targetProductId = offer?.productId ?: ""

    val billingManager = remember { BillingManager(context) }
    val isBillingReady by billingManager.isReady.collectAsState()
    val prices by billingManager.prices.collectAsState()
    LaunchedEffect(Unit) {
        billingManager.startConnection()
    }
    LaunchedEffect(isBillingReady, plan.upgradeOptions) {
        if (isBillingReady && plan.upgradeOptions.isNotEmpty()) {
            billingManager.queryPrices(plan.upgradeOptions.map { it.productId })
        }
    }


    val accentColor = if (plan.isFree) Color(0xFF58A6FF) else Color(0xFFBF91F9)
    // Play's localised price wins; the admin-set label is the fallback.
    val tierPrice = prices[targetProductId]?.let { "$it/month" }
        ?: offer?.priceLabel.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF161B22),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(Color(0xFF30363D), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.radialGradient(listOf(accentColor.copy(alpha = 0.3f), Color.Transparent)),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = featureName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isUpgradable) featureDescription
                       else "This isn't part of your plan right now.",
                fontSize = 14.sp,
                color = Color(0xFF8B949E),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))

            if (isUpgradable) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${offer?.emoji.orEmpty()} $targetTier".trim(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        Text(
                            text = offer?.tagline?.takeIf { it.isNotBlank() }
                                ?: "Unlocks $featureName and more",
                            fontSize = 12.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = tierPrice,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isUpgradable) Button(
                onClick = {
                    val activity = context as? Activity
                    if (activity != null) {
                        billingManager.purchaseSubscription(activity, targetProductId)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(
                    text = listOf("Upgrade to $targetTier", tierPrice)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (isUpgradable) "Maybe later" else "OK",
                    color = Color(0xFF8B949E), fontSize = 14.sp
                )
            }
        }
    }
}
