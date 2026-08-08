import os

music_kt_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(music_kt_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix the placement of annotations.
target_bad_structure = """@Composable

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun VinylRecordShelf("""

replacement_good_structure = """@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun VinylRecordShelf("""

if target_bad_structure in content:
    content = content.replace(target_bad_structure, replacement_good_structure)

# Now we must restore @Composable to AlbumBrowserOverlay
# Let's find:
# }
# private fun AlbumBrowserOverlay(
# And replace with:
# }
# @Composable
# private fun AlbumBrowserOverlay(

target_overlay = """}
private fun AlbumBrowserOverlay("""

replacement_overlay = """}
@Composable
private fun AlbumBrowserOverlay("""

if target_overlay in content:
    content = content.replace(target_overlay, replacement_overlay)

# Fix palette.background to palette.backgroundBottom in VinylShelfItem
target_surface = """        // Album Sleeve
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(4.dp),
            color = palette.background
        ) {"""

replacement_surface = """        // Album Sleeve
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(4.dp),
            color = palette.backgroundBottom
        ) {"""

if target_surface in content:
    content = content.replace(target_surface, replacement_surface)

# Fix zIndex
target_zindex = """                    .graphicsLayer {
                        this.scaleX = scale
                        this.scaleY = scale
                        this.alpha = alpha
                        this.rotationY = rotationY
                        this.cameraDistance = 12f * density
                    }
                    .zIndex(zIndex),"""

replacement_zindex = """                    .graphicsLayer {
                        this.scaleX = scale
                        this.scaleY = scale
                        this.alpha = alpha
                        this.rotationY = rotationY
                        this.cameraDistance = 12f * density
                    }
                    .androidx.compose.ui.zIndex.zIndex(zIndex),"""

content = content.replace(target_zindex, replacement_zindex)

with open(music_kt_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed annotations, color, and zIndex!")
