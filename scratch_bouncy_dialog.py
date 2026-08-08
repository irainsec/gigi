import re

with open('app/src/main/java/com/aman/gigi/ui/Screensaver.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace:
# if (showSyncOverlay) {
#             val overlayTitle = when {
# with AnimatedVisibility

old_str = """        if (showSyncOverlay) {
            val overlayTitle = when {"""

new_str = """        androidx.compose.animation.AnimatedVisibility(
            visible = showSyncOverlay,
            enter = androidx.compose.animation.scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
        ) {
            val overlayTitle = when {"""

content = content.replace(old_str, new_str)

with open('app/src/main/java/com/aman/gigi/ui/Screensaver.kt', 'w', encoding='utf-8') as f:
    f.write(content)
