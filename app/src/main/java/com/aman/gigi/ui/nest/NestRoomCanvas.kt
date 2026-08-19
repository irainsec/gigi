package com.aman.gigi.ui.nest

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aman.gigi.data.nest.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NestRoomCanvas(
    modifier: Modifier = Modifier,
    roomData: NestRoomData?,
    timeOfDay: TimeOfDay,
    myTwigiPos: TwigiRoomPosition,
    partnerTwigiPos: TwigiRoomPosition,
    myAvatarUrl: String?,
    partnerAvatarUrl: String?,
    partnerName: String,
    isPlayingMusic: Boolean = false,
    onFloorTapped: (x: Float, y: Float) -> Unit,
    onFurnitureTapped: (FurnitureItem) -> Unit,
    onPetTapped: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nestAnimations")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "vinyl"
    )
    val fairyPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fairy"
    )
    val petWiggle by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pet"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normX = (offset.x / size.width).coerceIn(0.1f, 0.9f)
                    val normY = (offset.y / size.height).coerceIn(0.2f, 0.85f)
                    onFloorTapped(normX, normY)
                }
            }
    ) {
        // ── Room Background Canvas (Walls, Window, Floorboards, Lighting) ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Back Wall
            val wallColor = when (roomData?.wallpaper) {
                "cozy_wood" -> Color(0xFF452B1E)
                "mint_sakura" -> Color(0xFF1E3A34)
                "midnight_galaxy" -> Color(0xFF0F0B1E)
                else -> Color(0xFF24173D) // lavender_stars
            }
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(wallColor.copy(alpha = 0.95f), wallColor.copy(alpha = 0.75f)),
                    startY = 0f,
                    endY = h * 0.45f
                ),
                topLeft = Offset(0f, 0f),
                size = Size(w, h * 0.45f)
            )

            // Wallpaper Pattern Accents (Cute pixel stars / dots)
            val patternStep = 32.dp.toPx()
            var py = 16.dp.toPx()
            while (py < h * 0.42f) {
                var px = 16.dp.toPx()
                while (px < w) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = 2.dp.toPx(),
                        center = Offset(px, py)
                    )
                    px += patternStep
                }
                py += patternStep
            }

            // 2. Window with Dynamic Time-of-Day View
            val windowW = w * 0.32f
            val windowH = h * 0.22f
            val windowX = w * 0.52f
            val windowY = h * 0.08f

            // Window Sky Background
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(timeOfDay.skyColorTop, timeOfDay.skyColorBottom),
                    startY = windowY,
                    endY = windowY + windowH
                ),
                topLeft = Offset(windowX, windowY),
                size = Size(windowW, windowH),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )

            // Window Stars (at night) or Sun (day)
            if (timeOfDay == TimeOfDay.NIGHT) {
                drawCircle(Color(0xFFFEF08A), radius = 6.dp.toPx(), center = Offset(windowX + windowW * 0.75f, windowY + windowH * 0.32f))
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 1.5.dp.toPx(), center = Offset(windowX + windowW * 0.25f, windowY + windowH * 0.45f))
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = Offset(windowX + windowW * 0.45f, windowY + windowH * 0.2f))
            } else if (timeOfDay == TimeOfDay.DAY || timeOfDay == TimeOfDay.SUNRISE) {
                drawCircle(Color(0xFFFDE047), radius = 14.dp.toPx(), center = Offset(windowX + windowW * 0.78f, windowY + windowH * 0.35f))
            }

            // Window Wooden Frame & Panes
            drawRoundRect(
                color = Color(0xFF6B4226),
                topLeft = Offset(windowX, windowY),
                size = Size(windowW, windowH),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(width = 4.dp.toPx())
            )
            // Cross bars
            drawLine(Color(0xFF6B4226), Offset(windowX + windowW / 2f, windowY), Offset(windowX + windowW / 2f, windowY + windowH), strokeWidth = 3.dp.toPx())
            drawLine(Color(0xFF6B4226), Offset(windowX, windowY + windowH / 2f), Offset(windowX + windowW, windowY + windowH / 2f), strokeWidth = 3.dp.toPx())

            // 3. Wooden Baseboard Trim
            drawRect(
                color = Color(0xFF3E2312),
                topLeft = Offset(0f, h * 0.435f),
                size = Size(w, h * 0.025f)
            )

            // 4. Cozy Isometric Wooden Flooring
            val floorColor = when (roomData?.flooring) {
                "dark_walnut" -> Color(0xFF26170E)
                "pink_carpet" -> Color(0xFF4A192C)
                "tatami_mat" -> Color(0xFF384326)
                else -> Color(0xFF382315) // warm_oak
            }
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(floorColor, floorColor.copy(alpha = 0.88f)),
                    startY = h * 0.45f,
                    endY = h
                ),
                topLeft = Offset(0f, h * 0.45f),
                size = Size(w, h * 0.55f)
            )

            // Planks / Grid Lines
            val plankH = 26.dp.toPx()
            var fy = h * 0.46f
            while (fy < h) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.22f),
                    start = Offset(0f, fy),
                    end = Offset(w, fy),
                    strokeWidth = 1.5.dp.toPx()
                )
                fy += plankH
            }

            // 5. Fairy Lights String along Top Wall
            val lightCount = 8
            for (i in 0..lightCount) {
                val lx = (w / (lightCount + 1)) * (i + 0.5f)
                val ly = h * 0.04f + sin(i * 1.2f) * 6.dp.toPx()
                val lightColors = listOf(Color(0xFFFBBF24), Color(0xFFF472B6), Color(0xFF38BDF8), Color(0xFF34D399))
                val col = lightColors[i % lightColors.size]
                drawCircle(col.copy(alpha = fairyPulse), radius = 5.dp.toPx(), center = Offset(lx, ly))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(lx, ly))
            }

            // 6. Ambient Time-of-day Room Tint
            if (timeOfDay.ambientTint != Color.Transparent) {
                drawRect(color = timeOfDay.ambientTint, topLeft = Offset.Zero, size = size)
            }
        }

        // ── Interactive Placeable Furniture ──
        val furnitureList = roomData?.furniture ?: NestRoomData.defaultFurnitureList()
        furnitureList.forEach { f ->
            RenderFurnitureItem(
                furniture = f,
                vinylRotation = vinylRotation,
                isPlayingMusic = isPlayingMusic,
                notesCount = roomData?.fridgeNotes?.size ?: 0,
                onClick = { onFurnitureTapped(f) }
            )
        }

        // ── Shared Virtual Pet (Mochi the Cat / Puppy) ──
        val pet = roomData?.pet ?: PetState()
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = (pet.x * 320).dp,
                    y = (pet.y * 560).dp
                )
                .graphicsLayer { rotationZ = petWiggle }
                .pointerInput(Unit) {
                    detectTapGestures { onPetTapped() }
                }
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E1436).copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF472B6).copy(alpha = 0.4f)),
                shadowElevation = 6.dp
            ) {
                Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (pet.type == "dog") "🐶" else "🐱",
                        fontSize = 28.sp
                    )
                }
            }
        }

        // ── Partner's Twigi Avatar ──
        RenderTwigiCharacter(
            name = partnerName,
            isMe = false,
            pos = partnerTwigiPos,
            avatarUrl = partnerAvatarUrl,
            breathScale = breathScale,
            isPlayingMusic = isPlayingMusic
        )

        // ── My Twigi Avatar ──
        RenderTwigiCharacter(
            name = "You",
            isMe = true,
            pos = myTwigiPos,
            avatarUrl = myAvatarUrl,
            breathScale = breathScale,
            isPlayingMusic = isPlayingMusic
        )
    }
}

@Composable
private fun RenderFurnitureItem(
    furniture: FurnitureItem,
    vinylRotation: Float,
    isPlayingMusic: Boolean,
    notesCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset(
                x = (furniture.x * 300).dp,
                y = (furniture.y * 540).dp
            )
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            }
    ) {
        when (furniture.type) {
            "bed" -> {
                // Cozy Canopy Bed
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF4A2810),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF78350F)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(width = 110.dp, height = 75.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Pillow
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFDF4FF),
                            modifier = Modifier
                                .size(width = 30.dp, height = 18.dp)
                                .offset(x = 8.dp, y = 8.dp)
                        ) {}
                        // Quilt / Duvet
                        Surface(
                            shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
                            color = Color(0xFFC084FC),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .align(Alignment.BottomCenter)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🌸", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            "couch" -> {
                // Sweetheart Loveseat
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFBE185D),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF472B6).copy(alpha = 0.5f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(width = 95.dp, height = 55.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🛋️", fontSize = 32.sp)
                    }
                }
            }
            "music" -> {
                // Vinyl Turntable
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1035),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B5CF6)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(width = 54.dp, height = 54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "💿",
                            fontSize = 28.sp,
                            modifier = Modifier.graphicsLayer { rotationZ = if (isPlayingMusic) vinylRotation else 0f }
                        )
                    }
                }
            }
            "fridge" -> {
                // Pastel Mini Fridge with Magnet Notes
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0284C7),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(width = 52.dp, height = 72.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("🧊", fontSize = 24.sp)
                        if (notesCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF43F5E),
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$notesCount",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "plant" -> {
                Text("🪴", fontSize = 36.sp)
            }
            "rug" -> {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color(0xFFF472B6).copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF472B6).copy(alpha = 0.6f)),
                    modifier = Modifier.size(width = 110.dp, height = 65.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("💖", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderTwigiCharacter(
    name: String,
    isMe: Boolean,
    pos: TwigiRoomPosition,
    avatarUrl: String?,
    breathScale: Float,
    isPlayingMusic: Boolean
) {
    Box(
        modifier = Modifier
            .offset(
                x = (pos.x * 300).dp,
                y = (pos.y * 540).dp
            )
            .scale(breathScale)
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emote bubble above head (e.g. 💖 or 💤)
            if (pos.action == TwigiAction.SLEEP_BED) {
                Text("💤", fontSize = 16.sp, modifier = Modifier.offset(y = (-4).dp))
            } else if (pos.action == TwigiAction.JAM_MUSIC || isPlayingMusic) {
                Text("🎧 🎵", fontSize = 14.sp, modifier = Modifier.offset(y = (-4).dp))
            } else if (pos.emote != null) {
                Text(pos.emote, fontSize = 18.sp, modifier = Modifier.offset(y = (-4).dp))
            }

            // Twigi Avatar Sprite or Fallback Cute Emoji
            Surface(
                shape = CircleShape,
                color = if (isMe) Color(0xFF7C3AED).copy(alpha = 0.25f) else Color(0xFFDB2777).copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isMe) Color(0xFFC084FC) else Color(0xFFF472B6)
                ),
                shadowElevation = 6.dp,
                modifier = Modifier.size(48.dp)
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .graphicsLayer {
                                rotationY = if (pos.facingLeft) 180f else 0f
                            }
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isMe) "☀️" else "💖",
                            fontSize = 24.sp
                        )
                    }
                }
            }

            // Name Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 3.dp)
            ) {
                Text(
                    text = name,
                    color = if (isMe) Color(0xFFFDE68A) else Color(0xFFF9A8D4),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
