import os

# Fix MusicViewModel.kt
viewmodel_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/viewmodel/MusicViewModel.kt"
with open(viewmodel_path, 'r', encoding='utf-8') as f:
    vm_content = f.read()

target_vm = """            _uiState.update { it.copy(lyrics = lyrics) }"""
replacement_vm = """            _uiState.update { it.copy(lyrics = lyrics ?: emptyList()) }"""

if target_vm in vm_content:
    vm_content = vm_content.replace(target_vm, replacement_vm)
    with open(viewmodel_path, 'w', encoding='utf-8') as f:
        f.write(vm_content)
    print("Success: Fixed MusicViewModel.kt lyrics state update")
else:
    print("Error: Target not found in MusicViewModel.kt")

# Fix Music.kt
music_path = "c:/Users/ATPL-ADMIN/Downloads/gigi/app/src/main/java/com/aman/gigi/ui/Music.kt"
with open(music_path, 'r', encoding='utf-8') as f:
    music_content = f.read()

target_music = """                            onShare = {
                                val connectionId = activeConnections.firstOrNull()?.connectionId
                                if (connectionId != null) {
                                    viewModel.shareNowPlaying(connectionId)
                                } else {
                                    android.widget.Toast.makeText(context, "No active partner connection", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },"""

replacement_music = """                            onShare = {
                                val currentSong = uiState.songs.find { it.id == uiState.currentSongId }
                                if (currentSong != null) {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Listening to ${currentSong.title}")
                                        putExtra(android.content.Intent.EXTRA_TEXT, "I'm listening to ${currentSong.title} by ${currentSong.artist} on Gigi!")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Song"))
                                } else {
                                    android.widget.Toast.makeText(context, "No song playing to share", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },"""

if target_music in music_content:
    music_content = music_content.replace(target_music, replacement_music)
    with open(music_path, 'w', encoding='utf-8') as f:
        f.write(music_content)
    print("Success: Fixed Music.kt share button")
else:
    print("Error: Target not found in Music.kt")
