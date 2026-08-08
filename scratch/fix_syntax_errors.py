import os

music_kt_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(music_kt_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Add zIndex import if missing
if "import androidx.compose.ui.zIndex.zIndex" not in content:
    content = content.replace("import androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp\nimport androidx.compose.ui.zIndex.zIndex")

# Fix 2: B64Image & songs reference -> coil.compose.AsyncImage & tracks
target1 = """                val firstArt = album.songs.firstOrNull()?.artBase64
                if (firstArt != null) {
                    com.aman.gigi.ui.components.B64Image(
                        b64 = firstArt,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                    )
                }"""
replacement1 = """                val firstArt = album.tracks.firstOrNull()?.albumArtUri ?: album.albumArtUri
                if (firstArt != null) {
                    coil.compose.AsyncImage(
                        model = firstArt,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                    )
                }"""
content = content.replace(target1, replacement1)

target2 = """            val coverArt = album.songs.firstOrNull()?.artBase64
            if (coverArt != null) {
                com.aman.gigi.ui.components.B64Image(
                    b64 = coverArt,
                    modifier = Modifier.fillMaxSize()
                )
            }"""
replacement2 = """            val coverArt = album.tracks.firstOrNull()?.albumArtUri ?: album.albumArtUri
            if (coverArt != null) {
                coil.compose.AsyncImage(
                    model = coverArt,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }"""
content = content.replace(target2, replacement2)

target3 = """                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = palette.accent.copy(alpha = 0.5f)
                    )"""
replacement3 = """                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = palette.accent.copy(alpha = 0.5f)
                    )"""
content = content.replace(target3, replacement3)

with open(music_kt_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Fixed syntax errors!")
