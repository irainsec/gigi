package com.aman.gigi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RomanceAmbientDecor(
    darkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FloatingHeart(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 22.dp, y = 110.dp),
            size = 12.dp,
            tint = if (darkTheme) Color(0xFFB883FF) else Color(0xFFE7A5FF),
            travel = 9f,
            durationMs = 2600
        )
        FloatingHeart(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-18).dp, y = (-120).dp),
            size = 10.dp,
            tint = if (darkTheme) Color(0xFFFF89A8) else Color(0xFFFFA4BD),
            travel = 11f,
            durationMs = 3100
        )
        FloatingHeart(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 34.dp, y = (-132).dp),
            size = 14.dp,
            tint = if (darkTheme) Color(0xFFC69DFF) else Color(0xFFD7B7FF),
            travel = 8f,
            durationMs = 2800
        )
        FloatingFlower(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-26).dp, y = 168.dp),
            size = 18.dp,
            petalColor = if (darkTheme) Color(0xFFFF99C5) else Color(0xFFF6B2D1),
            centerColor = Color(0xFFFFE39B),
            travel = 12f,
            durationMs = 3400
        )
        FloatingFlower(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 10.dp, y = 60.dp),
            size = 16.dp,
            petalColor = if (darkTheme) Color(0xFFD6B5FF) else Color(0xFFE3C8FF),
            centerColor = Color(0xFFFFF0A7),
            travel = 10f,
            durationMs = 3000
        )
        FloatingFlower(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-30).dp, y = (-188).dp),
            size = 20.dp,
            petalColor = if (darkTheme) Color(0xFFFFABD1) else Color(0xFFFFC5DF),
            centerColor = Color(0xFFFFE7AD),
            travel = 13f,
            durationMs = 3600
        )
        FloatingPetal(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 84.dp, y = 92.dp),
            size = 16.dp,
            tint = if (darkTheme) Color(0xFFFFADD1) else Color(0xFFFFC3DD),
            travel = 9f,
            durationMs = 2400
        )
        FloatingPetal(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-32).dp, y = 120.dp),
            size = 18.dp,
            tint = if (darkTheme) Color(0xFFB9A0FF) else Color(0xFFCBB9FF),
            travel = 10f,
            durationMs = 3200
        )
        FloatingSparkle(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-64).dp, y = 128.dp),
            size = 20.dp,
            tint = if (darkTheme) Color(0xFFFFC0EB) else Color(0xFFFFD7F2),
            travel = 7f,
            durationMs = 2600
        )
        FloatingSparkle(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 82.dp, y = (-82).dp),
            size = 16.dp,
            tint = if (darkTheme) Color(0xFFD8C1FF) else Color(0xFFE9D7FF),
            travel = 8f,
            durationMs = 2900
        )
    }
}

@Composable
private fun FloatingHeart(
    modifier: Modifier,
    size: Dp,
    tint: Color,
    travel: Float,
    durationMs: Int
) {
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )

    val transition = rememberInfiniteTransition(label = "ambient-heart")
    val y by transition.animateFloat(
        initialValue = -travel,
        targetValue = travel,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-heart-y"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-heart-alpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-heart-scale"
    )

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = tint.copy(alpha = alpha),
        modifier = modifier
            .offset(y = y.dp)
            .size(size)
            .scale(scale)
    )
}

@Composable
private fun FloatingFlower(
    modifier: Modifier,
    size: Dp,
    petalColor: Color,
    centerColor: Color,
    travel: Float,
    durationMs: Int
) {
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )

    val transition = rememberInfiniteTransition(label = "ambient-flower")
    val y by transition.animateFloat(
        initialValue = -travel,
        targetValue = travel,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-flower-y"
    )
    val rotation by transition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-flower-rotation"
    )

    Box(
        modifier = modifier
            .offset(y = y.dp)
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .scale(pressScale).pointerInput(Unit) { detectTapGestures(onPress = { isPressed = true; tryAwaitRelease(); isPressed = false }) }
    ) {
        FlowerPetal(Modifier.align(Alignment.TopCenter), petalColor, size * 0.42f)
        FlowerPetal(Modifier.align(Alignment.BottomCenter), petalColor, size * 0.42f)
        FlowerPetal(Modifier.align(Alignment.CenterStart), petalColor, size * 0.42f)
        FlowerPetal(Modifier.align(Alignment.CenterEnd), petalColor, size * 0.42f)
        FlowerPetal(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.dp, y = 2.dp),
            petalColor.copy(alpha = 0.86f),
            size * 0.34f
        )
        FlowerPetal(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-2).dp),
            petalColor.copy(alpha = 0.86f),
            size * 0.34f
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(size * 0.28f)
                .background(centerColor.copy(alpha = 0.92f), CircleShape)
                .alpha(0.95f)
        )
    }
}

@Composable
private fun FlowerPetal(
    modifier: Modifier,
    color: Color,
    size: Dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.52f), CircleShape)
    )
}

@Composable
private fun FloatingPetal(
    modifier: Modifier,
    size: Dp,
    tint: Color,
    travel: Float,
    durationMs: Int
) {
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )

    val transition = rememberInfiniteTransition(label = "ambient-petal")
    val y by transition.animateFloat(
        initialValue = -travel,
        targetValue = travel,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-petal-y"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.46f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-petal-alpha"
    )

    Box(
        modifier = modifier
            .offset(y = y.dp)
            .size(size)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = alpha),
                        Color.White.copy(alpha = alpha * 0.78f)
                    )
                ),
                shape = CircleShape
            )
            .graphicsLayer {
                scaleX = 1.18f
                scaleY = 0.72f
            }
            .scale(pressScale).pointerInput(Unit) { detectTapGestures(onPress = { isPressed = true; tryAwaitRelease(); isPressed = false }) }
    )
}

@Composable
private fun FloatingSparkle(
    modifier: Modifier,
    size: Dp,
    tint: Color,
    travel: Float,
    durationMs: Int
) {
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )

    val transition = rememberInfiniteTransition(label = "ambient-sparkle")
    val scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-sparkle-scale"
    )
    val y by transition.animateFloat(
        initialValue = -travel,
        targetValue = travel,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs + 240, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-sparkle-y"
    )

    Box(
        modifier = modifier
            .offset(y = y.dp)
            .size(size)
            .scale(scale)
    ) {
        SparkleArm(Modifier.align(Alignment.Center), tint, width = 2.dp, height = size)
        SparkleArm(Modifier.align(Alignment.Center), tint, width = size, height = 2.dp)
        SparkleArm(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = 0.72f
                    scaleY = 0.72f
                },
            tint.copy(alpha = 0.84f),
            width = 2.dp,
            height = size * 0.72f
        )
        SparkleArm(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = 0.72f
                    scaleY = 0.72f
                },
            tint.copy(alpha = 0.84f),
            width = size * 0.72f,
            height = 2.dp
        )
    }
}

@Composable
private fun BoxScope.SparkleArm(
    modifier: Modifier,
    tint: Color,
    width: Dp,
    height: Dp
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .background(tint.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
    )
}
