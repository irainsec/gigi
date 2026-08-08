import re

with open('app/src/main/java/com/aman/gigi/ui/Screensaver.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add BreakCardsScreen handler
break_cards_code = """
            ScreensaverViewModel.ScreensaverScreen.BREAK_CARDS -> {
                val connectionId = viewModel.selectedConnectionId.collectAsState().value
                if (connectionId != null) {
                    com.aman.gigi.ui.BreakCardsScreen(
                        connectionId = connectionId,
                        viewModel = viewModel,
                        onClose = {
                            viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.PARTNER_SESSIONS)
                        }
                    )
                } else {
                    viewModel.navigateTo(ScreensaverViewModel.ScreensaverScreen.LIST)
                }
            }
"""

if "ScreensaverViewModel.ScreensaverScreen.BREAK_CARDS -> {" not in content:
    content = content.replace(
        "ScreensaverViewModel.ScreensaverScreen.SPARKLE -> {",
        "ScreensaverViewModel.ScreensaverScreen.SPARKLE -> {"
    ).replace(
        """                    )
            }
        }
        
        // Partner Disconnected Dialog""",
        """                    )
            }
""" + break_cards_code + """
        }
        
        // Partner Disconnected Dialog"""
    )

with open('app/src/main/java/com/aman/gigi/ui/Screensaver.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated Screensaver.kt")
