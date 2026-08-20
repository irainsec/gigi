package com.aman.gigi.ui.nest

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aman.gigi.data.nest.*
import kotlin.math.abs
import kotlin.math.sin

private data class RenderableEntity(
    val id: String,
    val y: Float,
    val isFloorUnderlay: Boolean = false,
    val draw: @Composable BoxScope.() -> Unit
)

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
    onMoveTarget: (x: Float, y: Float, facing: FacingDirection, isWalking: Boolean) -> Unit,
    onFurnitureTapped: (FurnitureItem) -> Unit,
    onPetTapped: () -> Unit
) {
    val density = LocalDensity.current

    // Continuous smooth animation clock for pixel walk cycles & vinyl
    val infiniteTransition = rememberInfiniteTransition(label = "rpgAnimations")
    val walkBob by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(220, easing = LinearEasing), RepeatMode.Reverse),
        label = "walkBob"
    )
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "vinyl"
    )
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val petWiggle by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pet"
    )

    // Smooth lerp animated positions for local character
    val animatedMyX = remember { Animatable(myTwigiPos.x) }
    val animatedMyY = remember { Animatable(myTwigiPos.y) }

    LaunchedEffect(myTwigiPos.x, myTwigiPos.y) {
        val dist = kotlin.math.hypot(myTwigiPos.x - animatedMyX.value, myTwigiPos.y - animatedMyY.value)
        val duration = (dist * 1200).toInt().coerceIn(120, 900)
        animatedMyX.animateTo(myTwigiPos.x, tween(duration, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(myTwigiPos.y) {
        val dist = kotlin.math.hypot(myTwigiPos.x - animatedMyX.value, myTwigiPos.y - animatedMyY.value)
        val duration = (dist * 1200).toInt().coerceIn(120, 900)
        animatedMyY.animateTo(myTwigiPos.y, tween(duration, easing = LinearOutSlowInEasing))
    }

    // Smooth lerp for partner
    val animatedPartnerX = remember { Animatable(partnerTwigiPos.x) }
    val animatedPartnerY = remember { Animatable(partnerTwigiPos.y) }

    LaunchedEffect(partnerTwigiPos.x, partnerTwigiPos.y) {
        val dist = kotlin.math.hypot(partnerTwigiPos.x - animatedPartnerX.value, partnerTwigiPos.y - animatedPartnerY.value)
        val duration = (dist * 1200).toInt().coerceIn(120, 900)
        animatedPartnerX.animateTo(partnerTwigiPos.x, tween(duration, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(partnerTwigiPos.y) {
        val dist = kotlin.math.hypot(partnerTwigiPos.x - animatedPartnerX.value, partnerTwigiPos.y - animatedPartnerY.value)
        val duration = (dist * 1200).toInt().coerceIn(120, 900)
        animatedPartnerY.animateTo(partnerTwigiPos.y, tween(duration, easing = LinearOutSlowInEasing))
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val animLoader = remember(context) {
        coil.ImageLoader.Builder(context).components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }.build()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Handle Smooth Drag-to-Walk and Direct Dragging
                detectDragGestures(
                    onDragStart = { offset ->
                        val normX = (offset.x / size.width).coerceIn(0.10f, 0.90f)
                        val normY = (offset.y / size.height).coerceIn(0.18f, 0.90f)
                        val dx = normX - animatedMyX.value
                        val dy = normY - animatedMyY.value
                        val facing = calculateFacing(dx, dy)
                        onMoveTarget(normX, normY, facing, true)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val normX = (change.position.x / size.width).coerceIn(0.10f, 0.90f)
                        val normY = (change.position.y / size.height).coerceIn(0.18f, 0.90f)
                        val dx = normX - animatedMyX.value
                        val dy = normY - animatedMyY.value
                        val facing = calculateFacing(dx, dy)
                        onMoveTarget(normX, normY, facing, true)
                    },
                    onDragEnd = {
                        onMoveTarget(animatedMyX.value, animatedMyY.value, myTwigiPos.facing, false)
                    },
                    onDragCancel = {
                        onMoveTarget(animatedMyX.value, animatedMyY.value, myTwigiPos.facing, false)
                    }
                )
            }
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        // 1. Draw 2.5D Room Structure (Perimeter Walls, Doorway, Floor Tiles, Window, AC)
        Canvas(modifier = Modifier.fillMaxSize()) {
            PixelArtRoomRenderer.drawRoomStructure(
                drawScope = this,
                w = size.width,
                h = size.height,
                wallpaper = roomData?.wallpaper ?: "apartment_light",
                flooring = roomData?.flooring ?: "office_grid",
                timeOfDay = timeOfDay
            )
        }

        // 2. Build Depth-Sorted Entity List
        val furnitureList = roomData?.furniture ?: NestRoomData.defaultFurnitureList()
        val entities = mutableListOf<RenderableEntity>()

        // Add Furniture Entities
        furnitureList.forEach { f ->
            val isRug = f.type == "heart_rug" || f.id.contains("rug")
            entities.add(
                RenderableEntity(
                    id = f.id,
                    y = if (isRug) 0.01f else f.y, // rugs stay under characters
                    isFloorUnderlay = isRug,
                    draw = {
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = screenW * f.x - (f.widthDp.dp / 2f),
                                    y = screenH * f.y - (f.heightDp.dp / 2f)
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures { onFurnitureTapped(f) }
                                }
                        ) {
                            Canvas(modifier = Modifier.size(f.widthDp.dp, f.heightDp.dp)) {
                                PixelArtRoomRenderer.drawFurniture(
                                    drawScope = this,
                                    item = f,
                                    baseX = size.width / 2f,
                                    baseY = size.height / 2f,
                                    isPlayingMusic = isPlayingMusic,
                                    vinylRotation = vinylRotation,
                                    notesCount = roomData?.fridgeNotes?.size ?: 0
                                )
                            }
                        }
                    }
                )
            )
        }

        // Add Pet Mochi Entity
        val pet = roomData?.pet ?: PetState()
        entities.add(
            RenderableEntity(
                id = "pet_mochi",
                y = pet.y,
                draw = {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = screenW * pet.x - 18.dp,
                                y = screenH * pet.y - 18.dp
                            )
                            .pointerInput(Unit) {
                                detectTapGestures { onPetTapped() }
                            }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (pet.isSleeping) "💤" else "🐾",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1E1035).copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.6f)),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (pet.type == "dog") "🐶" else "🐱",
                                        fontSize = 18.sp,
                                        modifier = Modifier.graphicsLayer { rotationZ = petWiggle }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        )

        // Add Partner Twigi Character
        entities.add(
            RenderableEntity(
                id = "char_partner",
                y = animatedPartnerY.value,
                draw = {
                    RenderRpgCharacter(
                        name = partnerName,
                        isMe = false,
                        x = screenW * animatedPartnerX.value,
                        y = screenH * animatedPartnerY.value,
                        facing = partnerTwigiPos.facing,
                        isWalking = partnerTwigiPos.isWalking,
                        walkBob = walkBob,
                        avatarUrl = partnerAvatarUrl,
                        emote = partnerTwigiPos.emote,
                        action = partnerTwigiPos.action,
                        isPlayingMusic = isPlayingMusic,
                        breathScale = breathScale,
                        animLoader = animLoader
                    )
                }
            )
        )

        // Add My Twigi Character
        entities.add(
            RenderableEntity(
                id = "char_me",
                y = animatedMyY.value,
                draw = {
                    RenderRpgCharacter(
                        name = "You",
                        isMe = true,
                        x = screenW * animatedMyX.value,
                        y = screenH * animatedMyY.value,
                        facing = myTwigiPos.facing,
                        isWalking = myTwigiPos.isWalking,
                        walkBob = walkBob,
                        avatarUrl = myAvatarUrl,
                        emote = myTwigiPos.emote,
                        action = myTwigiPos.action,
                        isPlayingMusic = isPlayingMusic,
                        breathScale = breathScale,
                        animLoader = animLoader
                    )
                }
            )
        )

        // 3. Render all entities strictly sorted by Y (Natural 2.5D Depth Layering!)
        val sortedEntities = entities.sortedBy { it.y }
        sortedEntities.forEach { entity ->
            entity.draw(this)
        }
    }
}

@Composable
private fun RenderRpgCharacter(
    name: String,
    isMe: Boolean,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    facing: FacingDirection,
    isWalking: Boolean,
    walkBob: Float,
    avatarUrl: String?,
    emote: String?,
    action: TwigiAction,
    isPlayingMusic: Boolean,
    breathScale: Float,
    animLoader: coil.ImageLoader
) {
    val charWidth = 64.dp
    val charHeight = 72.dp
    val bobOffset = if (isWalking) walkBob.dp else 0.dp
    val isSleeping = action == TwigiAction.SLEEP_BED

    val sleepRotZ by animateFloatAsState(targetValue = if (isSleeping) -90f else 0f, label = "sleepRot")
    val sleepOffsetX by animateDpAsState(targetValue = if (isSleeping) (-8).dp else 0.dp, label = "sleepX")
    val sleepOffsetY by animateDpAsState(targetValue = if (isSleeping) 14.dp else 0.dp, label = "sleepY")

    val sleepingBreathScale = if (isSleeping) {
        val infiniteTransition = rememberInfiniteTransition(label = "sleepBreath")
        val breath by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath"
        )
        breath
    } else if (!isWalking) breathScale else 1f

    Box(
        modifier = Modifier
            .offset(
                x = x - (charWidth / 2) + sleepOffsetX,
                y = y - (charHeight * 0.82f) + bobOffset + sleepOffsetY
            )
            .scale(sleepingBreathScale)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emote Overhead Bubble (Agent Town style)
            if (isSleeping) {
                // Floating Animated Zzz's drifting upward
                val infiniteTransition = rememberInfiniteTransition(label = "zzz")
                val zProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "zProgress"
                )

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(60.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    listOf(0.0f, 0.33f, 0.66f).forEachIndexed { i, offsetFrac ->
                        val p = (zProgress + offsetFrac) % 1f
                        val pAlpha = (sin(p * 3.14159f)).coerceIn(0f, 1f)
                        val pY = (-p * 22f).dp
                        val pX = (sin(p * 6.283f + i * 1.6f) * 10f).dp
                        val pSize = (10 + (1f - p) * 6).sp
                        Text(
                            text = if (i == 0) "Z" else "z",
                            color = Color(0xFFC084FC).copy(alpha = pAlpha),
                            fontSize = pSize,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.offset(x = pX, y = pY)
                        )
                    }
                }
            } else if (action == TwigiAction.JAM_MUSIC || isPlayingMusic) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF1E1436).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.4f)),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text("🎧 🎵", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            } else if (emote != null) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF1E1436).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF472B6).copy(alpha = 0.5f)),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(emote, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            // Full LPC Pixel Character Sprite (Transparent Full Body, with Floor Shadow)
            Box(
                modifier = Modifier.size(54.dp, 60.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Character Foot Shadow
                if (!isSleeping) {
                    Canvas(
                        modifier = Modifier
                            .size(32.dp, 10.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        drawOval(
                            color = Color.Black.copy(alpha = 0.28f),
                            topLeft = Offset.Zero,
                            size = size
                        )
                    }
                }

                val fallbackAvatarUrl = "https://gigi.iamanraj.com/twigi/anim?c=eyJib2R5IjoibWFsZSIsImFuaW0iOiJ3YWxrIiwic3R5bGUiOiJwaXhlbCIsImhlYWQiOiJodW1hbl9tYWxlX2xpZ2h0In0=&size=128"
                val resolvedAvatar = avatarUrl?.takeIf { it.isNotBlank() } ?: fallbackAvatarUrl
                val parsedUrl = com.aman.gigi.utils.ImageUtils.parseEmojiModel(resolvedAvatar)

                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(parsedUrl)
                        .crossfade(true)
                        .build(),
                    imageLoader = animLoader,
                    contentDescription = name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = sleepRotZ
                            rotationY = if (facing == FacingDirection.LEFT) 180f else 0f
                        }
                )
            }

            // Name Pill (Pixel RPG Tag)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isMe) Color(0xFFA5B4FC).copy(alpha = 0.5f) else Color(0xFFF9A8D4).copy(alpha = 0.5f)),
                modifier = Modifier.padding(top = 1.dp)
            ) {
                Text(
                    text = name,
                    color = if (isMe) Color(0xFFFDE68A) else Color(0xFFF9A8D4),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private fun calculateFacing(dx: Float, dy: Float): FacingDirection {
    return if (abs(dx) > abs(dy)) {
        if (dx > 0) FacingDirection.RIGHT else FacingDirection.LEFT
    } else {
        if (dy > 0) FacingDirection.DOWN else FacingDirection.UP
    }
}
