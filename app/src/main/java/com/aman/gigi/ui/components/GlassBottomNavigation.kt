package com.aman.gigi.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.Cloudy

data class NavigationItem(val label: String, val icon: ImageVector)

@Composable
fun GlassNavActionPill(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    darkTheme: Boolean = false,
    width: Dp = 148.dp,
    height: Dp = 54.dp,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val contentColor = if (darkTheme) Color.White.copy(alpha = 0.86f) else Color(0xFF5F35D9)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                shadowElevation = 10f
                shape = RoundedCornerShape(42.dp)
                clip = true
            }
            .clip(RoundedCornerShape(42.dp))
            .border(
                1.5.dp,
                if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.3f),
                RoundedCornerShape(42.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    if (darkTheme) Color(0xFF1F1731).copy(alpha = 0.72f)
                    else Color.White.copy(alpha = 0.36f)
                )
        )
        Cloudy(radius = 42) {
            Box(
                Modifier.matchParentSize().background(
                    if (darkTheme) Color(0xFF201733).copy(alpha = 0.82f)
                    else Color(0xFFFDFBFF).copy(alpha = 0.50f)
                )
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    if (darkTheme) Color.White.copy(alpha = 0.04f)
                    else Color(0xFFF8F2FF).copy(alpha = 0.34f)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Floating glass navigation pill with smooth morph transitions between contexts.
 */
@Composable
fun GlassBottomNavigation(
    modifier: Modifier = Modifier,
    items: List<NavigationItem>,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    onSwipe: (Int) -> Unit = {},
    isMusicTab: Boolean = false,
    activeNowPlayingTrackLabel: String? = null,
    activeNowPlayingPartnerName: String? = null,
    isNowPlayingPlaying: Boolean = false,
    onNowPlayingClick: () -> Unit = {},
    onNowPlayingTogglePlay: () -> Unit = {}
) {
    val darkTheme = false
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val currentLabel = items.getOrNull(selectedItem)?.label.orEmpty()
    val isMusicScreen = isMusicTab ||
            currentLabel.equals("Music", ignoreCase = true) ||
            currentLabel.equals("Player", ignoreCase = true) ||
            currentLabel.equals("Library", ignoreCase = true)

    val shouldShowMiniPlayer = !activeNowPlayingTrackLabel.isNullOrBlank() && isNowPlayingPlaying && !isMusicScreen

    Column(
        modifier = modifier.padding(bottom = 28.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = shouldShowMiniPlayer,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (shouldShowMiniPlayer) {
                DynamicIslandMiniPlayer(
                    trackLabel = activeNowPlayingTrackLabel.orEmpty(),
                    partnerName = activeNowPlayingPartnerName,
                    isPlaying = isNowPlayingPlaying,
                    onTogglePlay = onNowPlayingTogglePlay,
                    onClick = onNowPlayingClick,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(0.92f)
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center
        ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    shadowElevation = 12f
                    shape = RoundedCornerShape(42.dp)
                    clip = true
                }
                .clip(RoundedCornerShape(42.dp))
                .border(
                    1.5.dp,
                    if (darkTheme) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.3f),
                    RoundedCornerShape(42.dp)
                )
                .pointerInput(onSwipe) {
                    // Swipe the pill horizontally to jump between pages.
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragTotal += amount
                        },
                        onDragEnd = {
                            when {
                                dragTotal < -50f -> {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onSwipe(1)
                                }
                                dragTotal > 50f -> {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onSwipe(-1)
                                }
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (darkTheme) Color(0xFF1F1731).copy(alpha = 0.72f)
                        else Color.White.copy(alpha = 0.36f)
                    )
            )
            Cloudy(radius = 42) {
                Box(
                    Modifier.matchParentSize().background(
                        if (darkTheme) Color(0xFF201733).copy(alpha = 0.82f)
                        else Color(0xFFFDFBFF).copy(alpha = 0.50f)
                    )
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (darkTheme) Color.White.copy(alpha = 0.04f)
                        else Color(0xFFF8F2FF).copy(alpha = 0.34f)
                    )
            )

            // Morph between item sets (main tabs ⇄ music controls) with a soft pop.
            AnimatedContent(
                targetState = items,
                transitionSpec = {
                    (fadeIn(tween(280)) + scaleIn(initialScale = 0.90f, animationSpec = tween(280)))
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "navModeMorph"
            ) { currentItems ->
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    currentItems.forEachIndexed { index, item ->
                        CuteNavItem(
                            item = item,
                            isSelected = selectedItem == index,
                            darkTheme = darkTheme,
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onItemSelected(index)
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun CuteNavItem(
    item: NavigationItem,
    isSelected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected && darkTheme -> Color(0xFF34225E).copy(alpha = 0.98f)
            isSelected -> Color(0xFFE6E0FF).copy(alpha = 0.95f)
            else -> Color.Transparent
        },
        label = "navItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFF8C5CFF)
            darkTheme -> Color.White.copy(alpha = 0.78f)
            else -> Color(0xFF5F35D9).copy(alpha = 0.55f)
        },
        label = "navItemColor"
    )

    Box(
        modifier = Modifier
            .height(44.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (isSelected) 14.dp else 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun DynamicIslandMiniPlayer(
    trackLabel: String,
    partnerName: String? = null,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E1738).copy(alpha = 0.92f),
        shadowElevation = 14.dp,
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFF472B6).copy(alpha = pulseAlpha),
                    Color(0xFFA855F7).copy(alpha = pulseAlpha),
                    Color(0xFF6366F1).copy(alpha = pulseAlpha)
                )
            )
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini Spinning Vinyl Record Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF110D20))
                    .graphicsLayer { rotationZ = if (isPlaying) rotationAngle else 0f }
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl center label
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA855F7))
                )
                Text("🎵", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (!partnerName.isNullOrBlank()) {
                    Text(
                        text = "Listening with $partnerName 💕",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF472B6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = trackLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play / Pause Button
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
