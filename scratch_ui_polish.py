import re

with open('app/src/main/java/com/aman/gigi/ui/components/RomanceAmbientDecor.kt', 'r', encoding='utf-8') as f:
    content = f.read()

imports = """
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
"""
if 'import androidx.compose.animation.core.Spring' not in content:
    content = content.replace('import androidx.compose.runtime.getValue', 'import androidx.compose.runtime.getValue\n' + imports)

def add_state(func_body):
    return """
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale"
    )
""" + func_body

content = re.sub(r'(@Composable\s+private fun FloatingHeart.*?\{)(.*?val transition = rememberInfiniteTransition)', lambda m: m.group(1) + add_state(m.group(2)), content, flags=re.DOTALL)
content = re.sub(r'(@Composable\s+private fun FloatingFlower.*?\{)(.*?val transition = rememberInfiniteTransition)', lambda m: m.group(1) + add_state(m.group(2)), content, flags=re.DOTALL)
content = re.sub(r'(@Composable\s+private fun FloatingPetal.*?\{)(.*?val transition = rememberInfiniteTransition)', lambda m: m.group(1) + add_state(m.group(2)), content, flags=re.DOTALL)
content = re.sub(r'(@Composable\s+private fun FloatingSparkle.*?\{)(.*?val transition = rememberInfiniteTransition)', lambda m: m.group(1) + add_state(m.group(2)), content, flags=re.DOTALL)

pointer_str = """.scale(pressScale).pointerInput(Unit) { detectTapGestures(onPress = { isPressed = true; tryAwaitRelease(); isPressed = false }) }"""

# Heart
content = re.sub(r'(\.scale\(scale\))(.*?)\)', r'\1' + pointer_str + r'\2)', content, count=1)
# Flower
content = re.sub(r'(\.graphicsLayer \{ rotationZ = rotation \})', r'\1\n            ' + pointer_str, content, count=1)
# Petal
content = re.sub(r'(\.graphicsLayer \{\s*scaleX = 1\.18f\s*scaleY = 0\.72f\s*\})', r'\1\n            ' + pointer_str, content, count=1)
# Sparkle
content = re.sub(r'(\.scale\(scale\))(.*?)\)', r'\1' + pointer_str + r'\2)', content, count=1) # Note: Sparkle has .scale(scale) as well, wait, Heart had the first one. So count=1 again will hit Sparkle if we do it sequentially.

with open('app/src/main/java/com/aman/gigi/ui/components/RomanceAmbientDecor.kt', 'w', encoding='utf-8') as f:
    f.write(content)
