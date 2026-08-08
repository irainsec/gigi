package com.aman.gigi.ui.screensaver.drawing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.Cloudy

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.aman.gigi.model.BrushType
import com.aman.gigi.model.EffectType

/**
 * Drawing tools UI - Collapsible Bottom Sheet with Persistent Footer
 */
@Composable
fun DrawingTools(
    selectedColor: Color,
    availableColors: List<Color>,
    isEraser: Boolean,
    strokeCount: Int,
    maxStrokes: Int,
    onColorSelected: (Color) -> Unit,
    onEraserToggle: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onGifClick: () -> Unit,
    // Advanced fields
    selectedBrush: BrushType,
    selectedEffects: Set<EffectType>,
    onBrushSelected: (BrushType) -> Unit,
    onEffectToggled: (EffectType) -> Unit,
    onLongPress: () -> Unit,
    // Sliders
    strokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    // State
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = false
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(32.dp))
            .border(1.2.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
    ) {
        Cloudy(radius = 35) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.2f))
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // COLLAPSIBLE CONTENT (Brushes, Effects, Colors)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, start = 20.dp, end = 20.dp)
                ) {

                    // Brush Selector Row
                    Text(
                        "Brushes",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(BrushType.entries) { brush ->
                            ToolChip(
                                label = brush.name.lowercase().replaceFirstChar { it.uppercase() },
                                isSelected = selectedBrush == brush && !isEraser,
                                onClick = { onBrushSelected(brush) },
                                onLongPress = onLongPress
                            )
                        }
                    }

                    // Brush Width Slider
                    Slider(
                        value = strokeWidth,
                        onValueChange = onStrokeWidthChange,
                        valueRange = 2f..50f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                        modifier = Modifier.height(32.dp).padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Effects Selector Row
                    Text(
                        "Effects",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(EffectType.entries) { effect ->
                            ToolChip(
                                label = effect.name.lowercase().replaceFirstChar { it.uppercase() },
                                isSelected = selectedEffects.contains(effect),
                                onClick = { onEffectToggled(effect) },
                                onLongPress = onLongPress,
                                activeColor = Color(0xFF03DAC6)
                            )
                        }
                    }

                    // Effect Intensity (Opacity) Slider
                    Slider(
                        value = opacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF03DAC6), activeTrackColor = Color(0xFF03DAC6).copy(alpha = 0.5f)),
                        modifier = Modifier.height(32.dp).padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Color picker
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            EraserButton(isSelected = isEraser, onClick = onEraserToggle)
                        }
                        items(availableColors) { color ->
                            ColorButton(
                                color = color,
                                isSelected = !isEraser && selectedColor == color,
                                onClick = { onColorSelected(color) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Secondary Actions (Undo, Clear, GIF)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                       TransparentButton(
                            label = "Undo",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onUndo,
                            isDark = isDark
                        )

                        // Magic Star for GIFs
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFBB86FC).copy(alpha = 0.3f))
                                .border(1.dp, Color(0xFFBB86FC).copy(alpha = 0.5f), CircleShape)
                                .clickable { onGifClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "GIFs",
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        TransparentButton(
                            label = "Clear",
                            icon = Icons.Default.Delete,
                            onClick = onClear,
                            isDestructive = true,
                            isDark = isDark
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.1f))
                }
            }

            // PERSISTENT FOOTER (Cancel - Toggle - Send)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CANCEL BUTTON
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }

                // TOGGLE BUTTON (Center)
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            CircleShape
                        )
                ) {
                    Icon(
                        if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Toggle Tools",
                        tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
                    )
                }

                // SEND BUTTON
                Button(
                    onClick = onSend,
                    enabled = strokeCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8B5CF6),
                        disabledContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Send", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Generic Tool Chip for brushes and effects
 */
@Composable
fun ToolChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    activeColor: Color = Color(0xFF8B5CF6)
) {
    val isDark = false
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1f, label = "chip_scale")

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) activeColor.copy(alpha = 0.15f)
                else if (isDark) Color.White.copy(alpha = 0.08f)
                else Color.Black.copy(alpha = 0.05f)
            )
            .border(
                width = 1.2.dp,
                color = if (isSelected) activeColor.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) activeColor else {
                if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
            }
        )
    }
}

/**
 * Color button component
 */
@Composable
fun ColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "color_scale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, Color.White, CircleShape)
                        .border(5.dp, Color(0xFF8B5CF6), CircleShape)
                } else {
                    Modifier.border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                }
            )
            .clickable(onClick = onClick)
    )
}

/**
 * Eraser button component
 */
@Composable
fun EraserButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = false
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "eraser_scale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isDark) Color(0xFF2A2A3E) else Color.White)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, Color.White, CircleShape)
                        .border(5.dp, Color(0xFF8B5CF6), CircleShape)
                } else {
                    Modifier.border(2.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Brush,
            contentDescription = "Eraser",
            tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Modern transparent button for secondary actions
 */
@Composable
fun TransparentButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    isDark: Boolean = false
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isDestructive) Color.Red.copy(alpha = 0.7f)
                           else if (isDark) Color.White.copy(alpha = 0.5f)
                           else Color.Black.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
