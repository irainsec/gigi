import os

file_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

target = """        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle Button
            androidx.compose.material3.IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) palette.accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            key("prev") {
                PlayerPillButton(
                    icon = Icons.Default.SkipPrevious,
                    label = "PREV",
                    onClick = onPrevious,
                    palette = palette,
                    enabled = songCount > 0
                )
            }
            key("play") {
                PlayerPillButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = "PLAY",
                    onClick = onPlayPause,
                    palette = palette,
                    emphasized = true
                )
            }
            key("next") {
                PlayerPillButton(
                    icon = Icons.Default.SkipNext,
                    label = "NEXT",
                    onClick = onNext,
                    palette = palette,
                    enabled = songCount > 0
                )
            }

            // Repeat Button
            androidx.compose.material3.IconButton(
                onClick = onToggleRepeat,
                modifier = Modifier.size(48.dp)
            ) {
                val icon = when (repeatMode) {
                    com.aman.gigi.service.PlaybackRepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != com.aman.gigi.service.PlaybackRepeatMode.NONE) palette.accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }"""

replacement = """        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Shuffle Button
            androidx.compose.material3.IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleEnabled) palette.accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                key("prev") {
                    PlayerPillButton(
                        icon = Icons.Default.SkipPrevious,
                        label = "PREV",
                        onClick = onPrevious,
                        palette = palette,
                        enabled = songCount > 0
                    )
                }
                key("play") {
                    PlayerPillButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = "PLAY",
                        onClick = onPlayPause,
                        palette = palette,
                        emphasized = true
                    )
                }
                key("next") {
                    PlayerPillButton(
                        icon = Icons.Default.SkipNext,
                        label = "NEXT",
                        onClick = onNext,
                        palette = palette,
                        enabled = songCount > 0
                    )
                }
            }

            // Repeat Button
            androidx.compose.material3.IconButton(
                onClick = onToggleRepeat,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp)
            ) {
                val icon = when (repeatMode) {
                    com.aman.gigi.service.PlaybackRepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Repeat",
                    tint = if (repeatMode != com.aman.gigi.service.PlaybackRepeatMode.NONE) palette.accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("Success: Replaced Row with Box for StableMusicControls")
else:
    print("Error: Target not found in Music.kt")
