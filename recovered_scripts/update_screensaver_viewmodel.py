import re

with open('app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add BreakCardDao to constructor
old_constructor = """    private val themeSongPlayer: ThemeSongPlayer,
    private val httpUploader: com.aman.gigi.network.HttpUploader,
    private val locationProvider: com.aman.gigi.data.location.LocationProvider
) : ViewModel() {"""

new_constructor = """    private val themeSongPlayer: ThemeSongPlayer,
    private val httpUploader: com.aman.gigi.network.HttpUploader,
    private val locationProvider: com.aman.gigi.data.location.LocationProvider,
    val breakCardDao: com.aman.gigi.data.dao.BreakCardDao
) : ViewModel() {"""

content = content.replace(old_constructor, new_constructor)

# Add BreakCards StateFlow exposing bootstrapManager.breakCards
flow_code = """
    // Break Cards
    val breakCards = bootstrapManager.breakCards

    fun fetchBreakCards() {
        viewModelScope.launch {
            bootstrapManager.fetchBreakCardConfigs()
        }
    }
"""

if "val breakCards = bootstrapManager.breakCards" not in content:
    content = content.replace(
        "    // Galaxy camera (zoom/rotation/tilt)",
        flow_code + "\n    // Galaxy camera (zoom/rotation/tilt)"
    )

with open('app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated ScreensaverViewModel.kt")
